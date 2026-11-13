package app.proxsh.core

import app.proxsh.core.transfer.OutgoingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Uses the real network stack on this machine: multicast announcements, no manual addresses. */
class DiscoveryLiveTest {
    private val root: File = Files.createTempDirectory("proxsh-live").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodes = mutableListOf<ProxshNode>()

    @AfterTest
    fun cleanUp() {
        nodes.forEach { it.close() }
        scope.cancel()
        root.deleteRecursively()
    }

    private fun node(name: String, kind: String): ProxshNode {
        val dir = File(root, name)
        return ProxshNode(
            NodeConfig(File(dir, "data"), File(dir, "received"), name, kind, tcpPort = 0, discoveryPort = 54318),
            scope,
        ).also {
            it.start()
            nodes.add(it)
        }
    }

    @Test
    fun `two devices find each other by themselves and send`() = runBlocking {
        val phone = node("Test phone", "phone")
        val laptop = node("Test laptop", "laptop")
        laptop.trustStore.add(phone.identity.fingerprint, "Test phone")

        val seen = withTimeout(15_000) {
            while (phone.peers.value.none { it.id == laptop.identity.fingerprint }) delay(100)
            phone.peers.value.first { it.id == laptop.identity.fingerprint }
        }
        assertEquals("Test laptop", seen.name)
        assertEquals("laptop", seen.device)
        assertTrue(seen.candidates.isNotEmpty())

        withTimeout(15_000) {
            while (laptop.peers.value.none { it.id == phone.identity.fingerprint }) delay(100)
        }

        val result = phone.send(seen, listOf(OutgoingItem.Text("found you by myself")))
        assertTrue(result.ok, result.message)
    }
}
