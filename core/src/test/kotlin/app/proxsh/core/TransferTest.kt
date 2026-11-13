package app.proxsh.core

import app.proxsh.core.transfer.OutgoingItem
import app.proxsh.core.transfer.ReceivedOffer
import app.proxsh.core.transfer.Receiver
import app.proxsh.core.transfer.SendOutcome
import app.proxsh.core.transfer.Sender
import app.proxsh.core.util.sha256Hex
import app.proxsh.core.wire.FrameIO
import app.proxsh.core.wire.Offer
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Random
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Sender and receiver over plain local sockets, so the transfer logic is tested on its own. */
class TransferTest {
    private val root: File = Files.createTempDirectory("proxsh-transfer").toFile()
    private val sendDir = File(root, "send").apply { mkdirs() }
    private val receiveDir = File(root, "receive")
    private val partialDir = File(receiveDir, ".partial")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private class CountingOut(private val inner: OutputStream, private val limit: Long = Long.MAX_VALUE) : OutputStream() {
        var count = 0L
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            val room = limit - count
            if (len > room) {
                if (room > 0) inner.write(b, off, room.toInt())
                inner.flush()
                count += maxOf(room, 0)
                throw IOException("connection cut")
            }
            inner.write(b, off, len)
            count += len
        }
        override fun flush() = inner.flush()
        override fun close() = inner.close()
    }

    private class Run(val sent: Result<SendOutcome>, val received: Result<ReceivedOffer?>, val bytesWritten: Long)

    private fun run(items: List<OutgoingItem>, accept: Boolean = true, cutAfter: Long = Long.MAX_VALUE): Run {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val client = Socket(InetAddress.getLoopbackAddress(), server.localPort)
        val peer = server.accept()
        server.close()
        val receivedRef = AtomicReference<Result<ReceivedOffer?>>()
        val worker = thread {
            receivedRef.set(
                runCatching {
                    peer.use {
                        val io = FrameIO(peer.getInputStream(), peer.getOutputStream())
                        val receiver = Receiver(io, receiveDir, partialDir)
                        val offer = io.readControl() as Offer
                        val problem = receiver.check(offer)
                        if (problem != null) {
                            receiver.reject(offer.offerId, problem)
                            return@runCatching null
                        }
                        if (!accept) {
                            receiver.reject(offer.offerId, "Declined")
                            return@runCatching null
                        }
                        receiver.accept(offer, "sender-fingerprint") {}
                    }
                },
            )
        }
        val out = CountingOut(client.getOutputStream(), cutAfter)
        val sent = runCatching { Sender(FrameIO(client.getInputStream(), out)).send(items) }
        runCatching { client.close() }
        worker.join(15_000)
        return Run(sent, receivedRef.get(), out.count)
    }

    private fun randomFile(name: String, size: Int): File {
        val f = File(sendDir, name)
        val bytes = ByteArray(size)
        Random(size.toLong()).nextBytes(bytes)
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun `files and text arrive byte for byte`() {
        val empty = File(sendDir, "empty.txt").apply { writeText("") }
        val big = randomFile("big.bin", 3 * 1024 * 1024 + 17)
        val unicode = randomFile("హాయ్ photo.jpg", 70_000)
        val items = listOf(OutgoingItem.file(empty), OutgoingItem.file(big), OutgoingItem.Text("OTP 482913 — 😀"), OutgoingItem.file(unicode))

        val r = run(items)

        assertIs<SendOutcome.Done>(r.sent.getOrThrow())
        val got = r.received.getOrThrow()!!
        assertEquals(listOf("OTP 482913 — 😀"), got.texts)
        assertEquals(3, got.files.size)
        assertEquals(sha256Hex(big), sha256Hex(File(receiveDir, "big.bin")))
        assertEquals(sha256Hex(unicode), sha256Hex(File(receiveDir, "హాయ్ photo.jpg")))
        assertEquals(0L, File(receiveDir, "empty.txt").length())
        assertTrue(partialDir.listFiles().orEmpty().isEmpty(), "no partial files are left behind")
    }

    @Test
    fun `a declined offer sends nothing`() {
        val file = randomFile("secret.bin", 1_000_000)
        val r = run(listOf(OutgoingItem.file(file)), accept = false)
        val outcome = r.sent.getOrThrow()
        assertIs<SendOutcome.Rejected>(outcome)
        assertEquals("Declined", outcome.reason)
        assertTrue(receiveDir.listFiles().orEmpty().none { it.name == "secret.bin" })
    }

    @Test
    fun `an interrupted transfer carries on instead of starting again`() {
        val file = randomFile("movie.bin", 4 * 1024 * 1024)
        val items = listOf(OutgoingItem.file(file))

        val first = run(items, cutAfter = 2_000_000)
        assertFailsWith<IOException> { first.sent.getOrThrow() }
        assertTrue(partialDir.listFiles().orEmpty().isNotEmpty(), "the half-received file is kept")

        val second = run(items)
        assertIs<SendOutcome.Done>(second.sent.getOrThrow())
        assertEquals(sha256Hex(file), sha256Hex(File(receiveDir, "movie.bin")))
        assertTrue(
            second.bytesWritten < 3_000_000,
            "second attempt sent ${second.bytesWritten} bytes, expected clearly less than the 4 MB file",
        )
    }

    @Test
    fun `the same name twice never overwrites`() {
        val a = randomFile("photo.jpg", 5000)
        run(listOf(OutgoingItem.file(a))).received.getOrThrow()
        run(listOf(OutgoingItem.file(a))).received.getOrThrow()
        assertTrue(File(receiveDir, "photo.jpg").exists())
        assertTrue(File(receiveDir, "photo (1).jpg").exists())
    }

    @Test
    fun `oversized text is refused`() {
        val r = run(listOf(OutgoingItem.Text("x".repeat(2 * 1024 * 1024))))
        val outcome = r.sent.getOrThrow()
        assertIs<SendOutcome.Rejected>(outcome)
        assertTrue(outcome.reason.contains("too long"))
    }
}
