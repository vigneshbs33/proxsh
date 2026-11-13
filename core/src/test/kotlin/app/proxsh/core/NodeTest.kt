package app.proxsh.core

import app.proxsh.core.discovery.Peer
import app.proxsh.core.link.LinkCandidate
import app.proxsh.core.link.LinkKind
import app.proxsh.core.transfer.OutgoingItem
import app.proxsh.core.trust.CheckCode
import app.proxsh.core.util.sha256Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Two real nodes over real encrypted connections on this machine. */
class NodeTest {
    private val root: File = Files.createTempDirectory("proxsh-node").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodes = mutableListOf<ProxshNode>()

    @AfterTest
    fun cleanUp() {
        nodes.forEach { it.close() }
        scope.cancel()
        root.deleteRecursively()
    }

    private class Log(node: ProxshNode, scope: CoroutineScope) {
        val events = CopyOnWriteArrayList<NodeEvent>()

        init {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { node.events.collect { events.add(it) } }
        }

        suspend inline fun <reified T : NodeEvent> await(timeoutMillis: Long = 15_000, crossinline match: (T) -> Boolean = { true }): T {
            withTimeout(timeoutMillis) {
                while (events.filterIsInstance<T>().none { match(it) }) delay(20)
            }
            return events.filterIsInstance<T>().first { match(it) }
        }
    }

    private fun node(name: String, kind: String = "laptop"): ProxshNode {
        val dir = File(root, name)
        val node = ProxshNode(
            NodeConfig(File(dir, "data"), File(dir, "received"), name, kind, tcpPort = 0, enableDiscovery = false),
            scope,
        )
        node.start()
        nodes.add(node)
        return node
    }

    private fun peerOf(node: ProxshNode, name: String, vararg kinds: LinkKind) = Peer(
        node.identity.fingerprint, name, "laptop",
        (kinds.toList().ifEmpty { listOf(LinkKind.LAN) }).map { LinkCandidate(it, "127.0.0.1", node.port) },
    )

    private fun sample(name: String, size: Int): File {
        val f = File(root, name)
        val bytes = ByteArray(size)
        Random(7).nextBytes(bytes)
        f.writeBytes(bytes)
        return f
    }

    private fun receivedDir(n: ProxshNode, name: String) = File(root, "$name/received")

    @Test
    fun `a stranger is asked about, trusted once, then goes straight through`() = runBlocking {
        val phone = node("phone", "phone")
        val laptop = node("laptop")
        val log = Log(laptop, scope)
        val file = sample("photo.jpg", 2_000_000)

        val first = async { phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.file(file))) }
        val offer = log.await<IncomingOffer>()
        assertEquals(CheckCode.of(phone.identity.fingerprint, laptop.identity.fingerprint), offer.checkCode)
        assertEquals("phone", offer.peerName)
        assertEquals("photo.jpg", offer.items.single().name)
        laptop.answer(offer.offerId, accept = true, always = true)

        assertTrue(first.await().ok)
        assertEquals(sha256Hex(file), sha256Hex(File(receivedDir(laptop, "laptop"), "photo.jpg")))
        assertEquals(listOf(phone.identity.fingerprint), laptop.trustStore.devices.value.map { it.fingerprint })

        // Second time: trusted, so no prompt.
        val second = phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.Text("hello again")))
        assertTrue(second.ok)
        assertEquals(1, log.events.count { it is IncomingOffer })
        val finished = log.await<TransferFinished> { it.texts.contains("hello again") }
        assertTrue(finished.ok)
    }

    @Test
    fun `declining sends nothing and trusts nobody`() = runBlocking {
        val phone = node("phone", "phone")
        val laptop = node("laptop")
        val log = Log(laptop, scope)

        val sending = async { phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.file(sample("a.bin", 1000)))) }
        val offer = log.await<IncomingOffer>()
        laptop.answer(offer.offerId, accept = false)

        val result = sending.await()
        assertFalse(result.ok)
        assertEquals("Declined", result.message)
        assertTrue(laptop.trustStore.devices.value.isEmpty())
    }

    @Test
    fun `accepting without ticking always still asks next time`() = runBlocking {
        val phone = node("phone", "phone")
        val laptop = node("laptop")
        val log = Log(laptop, scope)

        repeat(2) { round ->
            val sending = async { phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.Text("round $round"))) }
            val offer = log.await<IncomingOffer> { o -> log.events.filterIsInstance<IncomingOffer>().indexOf(o) == round }
            laptop.answer(offer.offerId, accept = true, always = false)
            assertTrue(sending.await().ok)
        }
        assertEquals(2, log.events.count { it is IncomingOffer })
    }

    @Test
    fun `trusted-only mode refuses strangers and off refuses everyone`() = runBlocking {
        val phone = node("phone", "phone")
        val laptop = node("laptop")
        laptop.setReceiveMode(ReceiveMode.TRUSTED_ONLY)

        val stranger = phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.Text("hi")))
        assertFalse(stranger.ok)
        assertTrue(stranger.message.contains("trusted"))

        laptop.trustStore.add(phone.identity.fingerprint, "phone")
        assertTrue(phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.Text("hi"))).ok)

        laptop.setReceiveMode(ReceiveMode.OFF)
        val off = phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.Text("hi")))
        assertFalse(off.ok)
        assertTrue(off.message.contains("not receiving"))
    }

    @Test
    fun `revoking a device makes it ask again`() = runBlocking {
        val phone = node("phone", "phone")
        val laptop = node("laptop")
        val log = Log(laptop, scope)
        laptop.trustStore.add(phone.identity.fingerprint, "phone")

        assertTrue(phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.Text("one"))).ok)
        assertEquals(0, log.events.count { it is IncomingOffer })

        laptop.revoke(phone.identity.fingerprint)
        val sending = async { phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.Text("two"))) }
        val offer = log.await<IncomingOffer>()
        laptop.answer(offer.offerId, accept = true)
        assertTrue(sending.await().ok)
    }

    @Test
    fun `a device pretending to be another is refused`() = runBlocking {
        val phone = node("phone", "phone")
        val laptop = node("laptop")
        val impostor = node("impostor")
        // The announcement says "laptop", but the connection reaches the impostor.
        val fake = Peer(laptop.identity.fingerprint, "laptop", "laptop", listOf(LinkCandidate(LinkKind.LAN, "127.0.0.1", impostor.port)))
        val result = phone.send(fake, listOf(OutgoingItem.Text("secret")))
        assertFalse(result.ok)
        assertTrue(result.message.contains("not the device"))
    }

    @Test
    fun `with several links it measures them and still delivers`() = runBlocking {
        val phone = node("phone", "phone")
        val laptop = node("laptop")
        laptop.trustStore.add(phone.identity.fingerprint, "phone")
        val file = sample("clip.mp4", 6_000_000)

        val result = phone.send(peerOf(laptop, "laptop", LinkKind.LAN, LinkKind.HOTSPOT), listOf(OutgoingItem.file(file)))

        assertTrue(result.ok, result.message)
        assertNotNull(result.link)
        assertEquals(sha256Hex(file), sha256Hex(File(receivedDir(laptop, "laptop"), "clip.mp4")))
    }

    @Test
    fun `progress and finish events are reported on both sides`() = runBlocking {
        val phone = node("phone", "phone")
        val laptop = node("laptop")
        laptop.trustStore.add(phone.identity.fingerprint, "phone")
        val sendLog = Log(phone, scope)
        val receiveLog = Log(laptop, scope)

        val ok = phone.send(peerOf(laptop, "laptop"), listOf(OutgoingItem.file(sample("big.bin", 8_000_000)))).ok
        assertTrue(ok)

        val sent = sendLog.await<TransferFinished>()
        assertTrue(sent.ok)
        val got = receiveLog.await<TransferFinished>()
        assertTrue(got.ok)
        assertEquals(1, got.files.size)
        val last = sendLog.events.filterIsInstance<TransferProgress>().lastOrNull() ?: fail("no progress events")
        assertEquals(8_000_000L, last.totalBytes)
    }
}
