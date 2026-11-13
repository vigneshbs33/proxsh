package app.proxsh.core.transfer

import app.proxsh.core.util.Hex
import app.proxsh.core.wire.Bye
import app.proxsh.core.wire.Decision
import app.proxsh.core.wire.DEFAULT_CHUNK_SIZE
import app.proxsh.core.wire.FileDone
import app.proxsh.core.wire.FileResult
import app.proxsh.core.wire.FrameIO
import app.proxsh.core.wire.Offer
import app.proxsh.core.wire.OfferItem
import app.proxsh.core.wire.ProtocolException
import app.proxsh.core.wire.TextBody
import app.proxsh.core.wire.chunkCount
import app.proxsh.core.wire.chunkLength
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.BitSet
import java.util.UUID

/**
 * Sends one offer over an open connection: asks first, then streams only the chunks the other side
 * still needs. Blocking; run it on an IO thread. Closing the connection cancels it.
 */
class Sender(private val io: FrameIO, private val chunkSize: Int = DEFAULT_CHUNK_SIZE) {

    fun send(items: List<OutgoingItem>, onProgress: (Progress) -> Unit = {}): SendOutcome {
        val offerItems = items.mapIndexed { index, item ->
            when (item) {
                is OutgoingItem.Content -> OfferItem(index, "file", item.source.name, item.source.size, item.source.key, chunkSize)
                is OutgoingItem.Text -> OfferItem(index, "text", "text", item.text.toByteArray(Charsets.UTF_8).size.toLong())
            }
        }
        val total = offerItems.sumOf { it.size }
        val offerId = UUID.randomUUID().toString()
        io.writeControl(Offer(offerId, offerItems))

        val decision = io.readControl() as? Decision ?: throw ProtocolException("Expected a decision")
        if (!decision.accept) return SendOutcome.Rejected(decision.reason ?: "Declined")

        var done = 0L
        for ((index, item) in items.withIndex()) {
            val offered = offerItems[index]
            when (item) {
                is OutgoingItem.Text -> {
                    io.writeControl(TextBody(index, item.text))
                    expectOk(index)
                    done += offered.size
                    onProgress(Progress(offered.name, done, total))
                }
                is OutgoingItem.Content -> {
                    val have = decision.have.firstOrNull { it.index == index }?.ranges.orEmpty().toBitSet()
                    done = streamFile(index, item.source, have, done, total, onProgress)
                    io.writeControl(FileDone(index, hashOf(item.source)))
                    expectOk(index)
                }
            }
        }
        io.writeControl(Bye)
        return SendOutcome.Done
    }

    private fun streamFile(
        index: Int,
        source: ByteSource,
        have: BitSet,
        doneBefore: Long,
        total: Long,
        onProgress: (Progress) -> Unit,
    ): Long {
        var done = doneBefore
        val chunks = chunkCount(source.size, chunkSize)
        val buffer = ByteArray(chunkSize)
        var stream: InputStream? = null
        var position = -1L
        try {
            for (chunk in 0 until chunks) {
                val length = chunkLength(source.size, chunkSize, chunk)
                if (have.get(chunk)) {
                    done += length
                    continue
                }
                val offset = chunk.toLong() * chunkSize
                if (stream == null || position != offset) {
                    stream?.close()
                    stream = source.open(offset)
                    position = offset
                }
                readFully(stream, buffer, length)
                position += length
                io.writeData(index, chunk, buffer, 0, length)
                done += length
                onProgress(Progress(source.name, done, total))
            }
            io.flush()
        } finally {
            stream?.close()
        }
        if (chunks == 0) onProgress(Progress(source.name, done, total))
        return done
    }

    private fun expectOk(index: Int) {
        val result = io.readControl() as? FileResult ?: throw ProtocolException("Expected a result")
        if (result.index != index) throw ProtocolException("Result for the wrong file")
        if (!result.ok) throw IOException(result.message ?: "The other device could not save the file")
    }

    private fun hashOf(source: ByteSource): String {
        val md = MessageDigest.getInstance("SHA-256")
        source.open(0).use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return Hex.encode(md.digest())
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int) {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) throw IOException("The file changed while sending")
            read += n
        }
    }
}

internal fun List<Long>.toBitSet(): BitSet {
    val bits = BitSet()
    var i = 0
    while (i + 1 < size) {
        val start = this[i].toInt()
        val end = this[i + 1].toInt()
        if (start in 0 until end) bits.set(start, end)
        i += 2
    }
    return bits
}

internal fun BitSet.toRanges(): List<Long> {
    val out = ArrayList<Long>()
    var start = nextSetBit(0)
    while (start >= 0) {
        val end = nextClearBit(start)
        out.add(start.toLong())
        out.add(end.toLong())
        start = nextSetBit(end)
    }
    return out
}
