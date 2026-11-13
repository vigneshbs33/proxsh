package app.proxsh.core.transfer

import app.proxsh.core.util.FileNames
import app.proxsh.core.util.sha256Hex
import app.proxsh.core.wire.Bye
import app.proxsh.core.wire.Cancel
import app.proxsh.core.wire.Decision
import app.proxsh.core.wire.FileDone
import app.proxsh.core.wire.FileResult
import app.proxsh.core.wire.FrameIO
import app.proxsh.core.wire.FrameType
import app.proxsh.core.wire.Have
import app.proxsh.core.wire.Offer
import app.proxsh.core.wire.OfferItem
import app.proxsh.core.wire.ProtocolException
import app.proxsh.core.wire.TextBody
import app.proxsh.core.wire.WireJson
import app.proxsh.core.wire.chunkCount
import app.proxsh.core.wire.chunkLength
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.BitSet
import java.util.UUID

class CancelledByPeer(reason: String) : IOException(reason)

class ReceivedOffer(val files: List<File>, val texts: List<String>)

/**
 * Receives one offer. Half-received files are kept in [partialDir] with a list of the chunks they
 * hold, so if the connection drops the sender can carry on instead of starting again.
 */
class Receiver(
    private val io: FrameIO,
    private val destDir: File,
    private val partialDir: File,
) {
    fun reject(offerId: String, reason: String) = io.writeControl(Decision(offerId, false, reason))

    /** Returns null if the offer is fine, otherwise the reason to give the sender. */
    fun check(offer: Offer): String? {
        if (offer.items.isEmpty() || offer.items.size > MAX_ITEMS) return "Too many items"
        var total = 0L
        for ((i, item) in offer.items.withIndex()) {
            if (item.index != i || item.size < 0) return "Bad offer"
            if (item.kind != "file" && item.kind != "text") return "Bad offer"
            if (item.kind == "text" && item.size > MAX_TEXT_BYTES) return "Text is too long. Send it as a file instead."
            if (item.kind == "file" && item.chunkSize !in MIN_CHUNK..MAX_CHUNK) return "Bad offer"
            if (item.kind == "file" && item.key.isBlank()) return "Bad offer"
            total += item.size
        }
        destDir.mkdirs()
        if (total > destDir.usableSpace) return "Not enough space on this device"
        return null
    }

    fun accept(offer: Offer, senderFingerprint: String, onProgress: (Progress) -> Unit): ReceivedOffer {
        destDir.mkdirs()
        partialDir.mkdirs()
        val states = offer.items.map { if (it.kind == "file") FileState.open(it, senderFingerprint, partialDir) else null }
        val total = offer.items.sumOf { it.size }
        val finished = BooleanArray(offer.items.size)
        val files = ArrayList<File>()
        val texts = ArrayList<String>()
        var textBytes = 0L
        try {
            val have = states.filterNotNull().map { Have(it.item.index, it.received.toRanges()) }
            io.writeControl(Decision(offer.offerId, true, have = have))
            io.flush()

            fun doneBytes(): Long =
                states.sumOf { it?.bytesHeld() ?: 0L } + offer.items.indices.sumOf { i ->
                    if (offer.items[i].kind == "text" && finished[i]) offer.items[i].size else 0L
                }

            // Chunks already held count as progress straight away.
            states.firstOrNull { it != null }?.let { onProgress(Progress(it.item.name, doneBytes(), total)) }

            while (true) {
                val frame = io.readFrame()
                when (frame.type) {
                    FrameType.DATA -> {
                        val state = states.getOrNull(frame.dataIndex) ?: throw ProtocolException("Data for an unknown file")
                        state.write(frame.dataChunk, frame.payload, frame.dataOffset, frame.dataLength)
                        onProgress(Progress(state.item.name, doneBytes(), total))
                    }
                    FrameType.CONTROL -> when (val msg = frame.control()) {
                        is FileDone -> {
                            val state = states.getOrNull(msg.index) ?: throw ProtocolException("Unknown file")
                            val result = state.finish(msg.sha256, destDir)
                            if (result != null) {
                                finished[msg.index] = true
                                files.add(result)
                                io.writeControl(FileResult(msg.index, true))
                            } else {
                                io.writeControl(FileResult(msg.index, false, "The file arrived damaged. Try again."))
                                throw IOException("Checksum mismatch")
                            }
                        }
                        is TextBody -> {
                            val item = offer.items.getOrNull(msg.index)
                            if (item == null || item.kind != "text") throw ProtocolException("Unexpected text")
                            textBytes += msg.text.toByteArray(Charsets.UTF_8).size
                            if (textBytes > MAX_TEXT_BYTES) throw ProtocolException("Text is too long")
                            finished[msg.index] = true
                            texts.add(msg.text)
                            io.writeControl(FileResult(msg.index, true))
                        }
                        is Bye -> break
                        is Cancel -> throw CancelledByPeer(msg.reason)
                        else -> throw ProtocolException("Unexpected message")
                    }
                    else -> throw ProtocolException("Unexpected frame")
                }
            }
            if (finished.any { !it }) throw IOException("The sender stopped before everything arrived")
            return ReceivedOffer(files, texts)
        } finally {
            states.forEach { it?.close() }
        }
    }

    /** One file being received: a partial file plus a note of which chunks are in it. */
    private class FileState(
        val item: OfferItem,
        private val dir: File,
        private val part: File,
        private val meta: File,
        val received: BitSet,
        private val raf: RandomAccessFile,
    ) {
        private val chunks = chunkCount(item.size, item.chunkSize)
        private var lastSaved = System.nanoTime()
        private var closed = false

        fun bytesHeld(): Long {
            var sum = 0L
            var c = received.nextSetBit(0)
            while (c >= 0 && c < chunks) {
                sum += chunkLength(item.size, item.chunkSize, c)
                c = received.nextSetBit(c + 1)
            }
            return sum
        }

        fun write(chunk: Int, buffer: ByteArray, offset: Int, length: Int) {
            if (chunk < 0 || chunk >= chunks) throw ProtocolException("Chunk out of range")
            if (length != chunkLength(item.size, item.chunkSize, chunk)) throw ProtocolException("Chunk has the wrong size")
            raf.seek(chunk.toLong() * item.chunkSize)
            raf.write(buffer, offset, length)
            received.set(chunk)
            if (System.nanoTime() - lastSaved > 2_000_000_000L) saveMeta()
        }

        /** Checks the whole file and moves it into place. Returns null if it does not match. */
        fun finish(expectedSha256: String, destDir: File): File? {
            if (received.cardinality() != chunks) return null
            raf.fd.sync()
            raf.close()
            closed = true
            if (sha256Hex(part) != expectedSha256.lowercase()) {
                dir.deleteRecursively()
                return null
            }
            val target = FileNames.uniqueIn(destDir, FileNames.sanitize(item.name))
            if (!part.renameTo(target)) {
                part.copyTo(target)
            }
            dir.deleteRecursively()
            return target
        }

        fun close() {
            if (closed) return
            closed = true
            runCatching { saveMeta() }
            runCatching { raf.close() }
        }

        private fun saveMeta() {
            lastSaved = System.nanoTime()
            val note = PartialNote(item.name, item.size, item.chunkSize, received.toRanges())
            meta.writeText(WireJson.encodeToString(PartialNote.serializer(), note))
        }

        companion object {
            fun open(item: OfferItem, senderFingerprint: String, partialDir: File): FileState {
                val id = sha256Hex("$senderFingerprint|${item.key}".toByteArray()).take(32)
                val dir = File(partialDir, id)
                dir.mkdirs()
                val part = File(dir, "data.part")
                val meta = File(dir, "note.json")
                var received = BitSet()
                val note = runCatching { WireJson.decodeFromString(PartialNote.serializer(), meta.readText()) }.getOrNull()
                val usable = note != null && note.size == item.size && note.chunkSize == item.chunkSize &&
                    part.exists() && part.length() == item.size
                if (usable) {
                    received = note!!.ranges.toBitSet()
                } else {
                    part.delete()
                }
                val raf = RandomAccessFile(part, "rw")
                if (!usable) raf.setLength(item.size)
                return FileState(item, dir, part, meta, received, raf)
            }
        }
    }

    @Serializable
    private class PartialNote(val name: String, val size: Long, val chunkSize: Int, val ranges: List<Long>)

    companion object {
        const val MAX_ITEMS = 1000
        const val MAX_TEXT_BYTES = 1024 * 1024L
        const val MIN_CHUNK = 16 * 1024
        const val MAX_CHUNK = 2 * 1024 * 1024

        fun newId(): String = UUID.randomUUID().toString()
    }
}
