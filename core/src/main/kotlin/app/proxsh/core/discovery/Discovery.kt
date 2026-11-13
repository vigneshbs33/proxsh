package app.proxsh.core.discovery

import app.proxsh.core.link.LinkCandidate
import app.proxsh.core.link.LinkKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

const val DEFAULT_DISCOVERY_PORT = 53318
private const val GROUP = "239.255.42.99"
private const val ANNOUNCE_EVERY_MILLIS = 2000L
private const val PEER_TTL_MILLIS = 7000L

/** What a device says about itself on the network. The id is its certificate fingerprint. */
@Serializable
data class Announcement(
    val v: Int = 1,
    val id: String,
    val name: String,
    val device: String,
    val port: Int,
    /** True for the first announcement after starting, so others answer straight away. */
    val first: Boolean = false,
)

/** Another ProxSh device that is nearby right now. */
data class Peer(
    val id: String,
    val name: String,
    val device: String,
    val candidates: List<LinkCandidate>,
)

/**
 * Finds other ProxSh devices on the same network with no setup: each device announces itself every
 * couple of seconds over UDP multicast (and broadcast, for networks that block multicast), and
 * listens for the others. A new device is answered within a second.
 */
class Discovery(
    private val self: Announcement,
    private val scope: CoroutineScope,
    private val port: Int = DEFAULT_DISCOVERY_PORT,
) : Closeable {
    private class Seen(var name: String, var device: String, var port: Int) {
        val addresses = ConcurrentHashMap<String, Long>()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val seen = ConcurrentHashMap<String, Seen>()
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    private var receiver: MulticastSocket? = null
    private var sender: MulticastSocket? = null
    @Volatile private var closed = false

    val peers: StateFlow<List<Peer>> get() = _peers

    fun start() {
        val listenPort = this.port // inside apply { } a bare "port" would mean the socket's own port
        val rx = MulticastSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(listenPort))
            soTimeout = 1000
        }
        receiver = rx
        sender = MulticastSocket().apply { timeToLive = 1 }
        joinAll(rx)

        scope.launch(Dispatchers.IO) { listen(rx) }
        scope.launch(Dispatchers.IO) {
            var first = true
            while (isActive && !closed) {
                announce(first)
                first = false
                prune()
                delay(ANNOUNCE_EVERY_MILLIS)
            }
        }
    }

    /** Announce again right now, for example when the app window opens. */
    fun refresh() {
        scope.launch(Dispatchers.IO) { announce(first = true) }
    }

    private fun joinAll(rx: MulticastSocket) {
        val group = InetAddress.getByName(GROUP)
        for (ni in usableInterfaces()) {
            runCatching { rx.joinGroup(InetSocketAddress(group, 0), ni) }
        }
    }

    private fun usableInterfaces(): List<NetworkInterface> =
        runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList()).filter {
            runCatching { it.isUp && !it.isLoopback && it.supportsMulticast() && it.interfaceAddresses.any { a -> a.address is Inet4Address } }
                .getOrDefault(false)
        }

    private fun announce(first: Boolean) {
        val tx = sender ?: return
        val bytes = json.encodeToString(Announcement.serializer(), self.copy(first = first)).toByteArray()
        val group = InetAddress.getByName(GROUP)
        for (ni in usableInterfaces()) {
            runCatching {
                tx.networkInterface = ni
                tx.send(DatagramPacket(bytes, bytes.size, group, port))
            }
            // Some networks drop multicast but pass broadcast, so send that too.
            for (address in ni.interfaceAddresses) {
                val broadcast = address.broadcast ?: continue
                runCatching { tx.send(DatagramPacket(bytes, bytes.size, broadcast, port)) }
            }
        }
    }

    private fun listen(rx: MulticastSocket) {
        val buffer = ByteArray(2048)
        while (!closed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                rx.receive(packet)
                handle(packet)
            } catch (_: SocketTimeoutException) {
                // loop again so closing is noticed
            } catch (e: IOException) {
                if (closed) return
            }
        }
    }

    private fun handle(packet: DatagramPacket) {
        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
        val ann = runCatching { json.decodeFromString(Announcement.serializer(), text) }.getOrNull() ?: return
        if (ann.id == self.id || ann.id.length != 64 || ann.port !in 1..65535) return
        val host = packet.address.hostAddress ?: return
        val entry = seen.getOrPut(ann.id) { Seen(ann.name, ann.device, ann.port) }
        entry.name = ann.name.take(60)
        entry.device = ann.device
        entry.port = ann.port
        entry.addresses[host] = System.currentTimeMillis()
        publish()
        if (ann.first) {
            // Tell the newcomer about us straight away instead of making it wait for our next turn.
            val reply = json.encodeToString(Announcement.serializer(), self).toByteArray()
            runCatching { sender?.send(DatagramPacket(reply, reply.size, packet.address, port)) }
        }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - PEER_TTL_MILLIS
        for ((id, entry) in seen) {
            entry.addresses.entries.removeIf { it.value < cutoff }
            if (entry.addresses.isEmpty()) seen.remove(id)
        }
        publish()
    }

    private fun publish() {
        val list = seen.entries.map { (id, entry) ->
            val candidates = entry.addresses.entries.sortedByDescending { it.value }
                .map { LinkCandidate(LinkKind.LAN, it.key, entry.port) }
            Peer(id, entry.name, entry.device, candidates)
        }.sortedBy { it.name.lowercase() }
        if (list != _peers.value) _peers.value = list
    }

    override fun close() {
        closed = true
        runCatching { receiver?.close() }
        runCatching { sender?.close() }
    }
}
