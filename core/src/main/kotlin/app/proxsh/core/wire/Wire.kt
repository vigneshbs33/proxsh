package app.proxsh.core.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

const val PROTOCOL_VERSION = 1
const val DEFAULT_CHUNK_SIZE = 512 * 1024
const val MAX_FRAME_BYTES = 4 * 1024 * 1024

class ProtocolException(message: String) : IOException(message)

object FrameType {
    const val CONTROL = 1
    const val DATA = 2
    const val BURST = 3
}

val WireJson = Json {
    classDiscriminator = "t"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Everything the two devices say to each other apart from raw file data. */
@Serializable
sealed interface Msg

@Serializable @SerialName("hello")
data class Hello(val proto: Int, val name: String, val device: String) : Msg

@Serializable
data class OfferItem(
    val index: Int,
    val kind: String, // "file" or "text"
    val name: String,
    val size: Long,
    /** Same for the same file every time, so a half-sent file can carry on later. */
    val key: String = "",
    val chunkSize: Int = DEFAULT_CHUNK_SIZE,
)

@Serializable @SerialName("offer")
data class Offer(val offerId: String, val items: List<OfferItem>) : Msg

/** Chunks the receiver already has for one file, as flat [start, end) pairs of chunk numbers. */
@Serializable
data class Have(val index: Int, val ranges: List<Long>)

@Serializable @SerialName("decision")
data class Decision(
    val offerId: String,
    val accept: Boolean,
    val reason: String? = null,
    val have: List<Have> = emptyList(),
) : Msg

@Serializable @SerialName("text")
data class TextBody(val index: Int, val text: String) : Msg

@Serializable @SerialName("file-done")
data class FileDone(val index: Int, val sha256: String) : Msg

@Serializable @SerialName("file-result")
data class FileResult(val index: Int, val ok: Boolean, val message: String? = null) : Msg

@Serializable @SerialName("ping")
data class Ping(val nonce: Long) : Msg

@Serializable @SerialName("pong")
data class Pong(val nonce: Long) : Msg

@Serializable @SerialName("probe-start")
data object ProbeStart : Msg

@Serializable @SerialName("probe-done")
data object ProbeDone : Msg

@Serializable @SerialName("probe-result")
data class ProbeResult(val bytes: Long, val nanos: Long) : Msg

@Serializable @SerialName("cancel")
data class Cancel(val reason: String) : Msg

@Serializable @SerialName("bye")
data object Bye : Msg

class Frame(val type: Int, val payload: ByteArray) {
    /** For DATA frames: which file in the offer, which chunk of it, and where the bytes start. */
    val dataIndex: Int get() = ByteBuffer.wrap(payload).getInt(0)
    val dataChunk: Int get() = ByteBuffer.wrap(payload).getInt(4)
    val dataOffset: Int get() = 8
    val dataLength: Int get() = payload.size - 8

    fun control(): Msg {
        if (type != FrameType.CONTROL) throw ProtocolException("Expected a message, got frame type $type")
        return try {
            WireJson.decodeFromString(Msg.serializer(), String(payload, Charsets.UTF_8))
        } catch (e: Exception) {
            throw ProtocolException("Unreadable message")
        }
    }
}

/** Reads and writes frames: 1 byte type, 4 bytes length, then the payload. */
class FrameIO(input: InputStream, output: OutputStream) : Closeable {
    private val din = DataInputStream(BufferedInputStream(input, 1 shl 16))
    private val dout = DataOutputStream(BufferedOutputStream(output, 1 shl 16))
    private val writeLock = Any()

    fun writeControl(message: Msg) {
        val bytes = WireJson.encodeToString(Msg.serializer(), message).toByteArray(Charsets.UTF_8)
        synchronized(writeLock) {
            dout.writeByte(FrameType.CONTROL)
            dout.writeInt(bytes.size)
            dout.write(bytes)
            dout.flush()
        }
    }

    fun writeData(fileIndex: Int, chunk: Int, buffer: ByteArray, offset: Int, length: Int) {
        synchronized(writeLock) {
            dout.writeByte(FrameType.DATA)
            dout.writeInt(8 + length)
            dout.writeInt(fileIndex)
            dout.writeInt(chunk)
            dout.write(buffer, offset, length)
        }
    }

    fun writeBurst(buffer: ByteArray, length: Int) {
        synchronized(writeLock) {
            dout.writeByte(FrameType.BURST)
            dout.writeInt(length)
            dout.write(buffer, 0, length)
        }
    }

    fun flush() = synchronized(writeLock) { dout.flush() }

    fun readFrame(): Frame {
        val type = din.readUnsignedByte()
        val length = din.readInt()
        if (length < 0 || length > MAX_FRAME_BYTES) throw ProtocolException("Bad frame size")
        if (type == FrameType.DATA && length < 8) throw ProtocolException("Bad data frame")
        val payload = ByteArray(length)
        din.readFully(payload)
        return Frame(type, payload)
    }

    fun readControl(): Msg = readFrame().control()

    override fun close() {
        runCatching { dout.close() }
        runCatching { din.close() }
    }
}

fun chunkCount(size: Long, chunkSize: Int): Int = ((size + chunkSize - 1) / chunkSize).toInt()

fun chunkLength(size: Long, chunkSize: Int, chunk: Int): Int =
    minOf(chunkSize.toLong(), size - chunk.toLong() * chunkSize).toInt()
