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

/**
 * What a device says about itself on the network.
 * The ID is its certificate fingerprint.
 */
@Serializable
data class Announcement(
    val v: Int = 1,
    val id: String,
    val name: String,
    val device: String,
    val port: Int,

    /**
     * True for the first announcement after starting,
     * so others answer straight away.
     */
    val first: Boolean = false,
)

/**
 * Another ProxSh device that is nearby right now.
 */
data class Peer(
    val id: String,
    val name: String,
    val device: String,
    val candidates: List<LinkCandidate>,
)

/**
 * Finds other ProxSh devices on the same network.
 *
 * Devices announce themselves through UDP multicast and broadcast.
 *
 * Discovered identities are retained independently of usable
 * connection addresses. This allows emulator discovery through
 * ADB even when its advertised network address is not directly
 * reachable from the host.
 */
class Discovery(
    private val self: Announcement,
    private val scope: CoroutineScope,
    private val port: Int = DEFAULT_DISCOVERY_PORT,
) : Closeable {

    private class Seen(
        @Volatile var name: String,
        @Volatile var device: String,
        @Volatile var port: Int,
        @Volatile var lastSeen: Long,
    ) {
        // Only usable remote addresses are stored here.
        val addresses = ConcurrentHashMap<String, Long>()
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val seen = ConcurrentHashMap<String, Seen>()

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())

    private var receiver: MulticastSocket? = null
    private var sender: MulticastSocket? = null

    @Volatile
    private var closed = false

    val peers: StateFlow<List<Peer>> get() = _peers

    // ----------------------------------------------------------------
    // Start discovery
    // ----------------------------------------------------------------

    fun start() {
        val listenPort = this.port

        val rx = MulticastSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(listenPort))
            soTimeout = 1000
        }

        receiver = rx

        sender = MulticastSocket().apply {
            timeToLive = 1
        }

        joinAll(rx)

        scope.launch(Dispatchers.IO) {
            listen(rx)
        }

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

    /**
     * Announce again right now, for example when the app window opens.
     */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            announce(first = true)
        }
    }

    // ----------------------------------------------------------------
    // Network interfaces
    // ----------------------------------------------------------------

    private fun joinAll(rx: MulticastSocket) {
        val group = InetAddress.getByName(GROUP)

        for (ni in usableInterfaces()) {
            runCatching {
                rx.joinGroup(
                    InetSocketAddress(group, 0),
                    ni
                )
            }
        }
    }

    /**
     * Returns active network interfaces that support IPv4 multicast.
     */
    private fun usableInterfaces(): List<NetworkInterface> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
        }.getOrDefault(emptyList()).filter { ni ->

            runCatching {
                ni.isUp &&
                        !ni.isLoopback &&
                        ni.supportsMulticast() &&
                        ni.interfaceAddresses.any { address ->
                            address.address is Inet4Address
                        }
            }.getOrDefault(false)
        }

    /**
     * Collects IPv4 addresses owned by this device.
     *
     * These addresses must not be treated as remote peer addresses.
     *
     * This specifically prevents addresses such as a Windows WSL
     * virtual adapter address from becoming a connection candidate
     * when a discovery packet is observed with that local source IP.
     */
    private fun localIpv4Addresses(): Set<String> =
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .flatMap { ni ->
                    ni.interfaceAddresses.mapNotNull { address ->
                        address.address as? Inet4Address
                    }
                }
                .map { address ->
                    address.hostAddress
                }
                .toSet()
        }.getOrDefault(emptySet())

    /**
     * Determines whether a packet source can be stored as a
     * remote connection address.
     */
    private fun isUsableRemoteAddress(
        address: InetAddress,
        localAddresses: Set<String>,
    ): Boolean {

        if (address !is Inet4Address) return false

        if (address.isAnyLocalAddress) return false
        if (address.isLoopbackAddress) return false
        if (address.isMulticastAddress) return false

        val host = address.hostAddress ?: return false

        // Never connect to an address owned by this machine.
        if (host in localAddresses) return false

        return true
    }

    // ----------------------------------------------------------------
    // Announcements
    // ----------------------------------------------------------------

    private fun announce(first: Boolean) {
        val tx = sender ?: return
        if (closed) return

        val bytes = json.encodeToString(
            Announcement.serializer(),
            self.copy(first = first)
        ).toByteArray()

        val group = InetAddress.getByName(GROUP)

        for (ni in usableInterfaces()) {

            runCatching {
                tx.networkInterface = ni

                tx.send(
                    DatagramPacket(
                        bytes,
                        bytes.size,
                        group,
                        port
                    )
                )
            }

            // Some networks drop multicast but pass broadcast,
            // so send the announcement to the interface broadcast
            // addresses as well.
            for (address in ni.interfaceAddresses) {

                val broadcast = address.broadcast ?: continue

                runCatching {
                    tx.networkInterface = ni

                    tx.send(
                        DatagramPacket(
                            bytes,
                            bytes.size,
                            broadcast,
                            port
                        )
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // Listen for announcements
    // ----------------------------------------------------------------

    private fun listen(rx: MulticastSocket) {
        val buffer = ByteArray(2048)

        while (!closed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)

                rx.receive(packet)

                handle(packet)

            } catch (_: SocketTimeoutException) {
                // Loop again so closing is noticed.

            } catch (e: IOException) {
                if (closed) return
            }
        }
    }

    private fun handle(packet: DatagramPacket) {

        val text = String(
            packet.data,
            0,
            packet.length,
            Charsets.UTF_8
        )

        val ann = runCatching {
            json.decodeFromString(
                Announcement.serializer(),
                text
            )
        }.getOrNull() ?: return

        // Basic announcement validation.
        if (ann.id == self.id) return
        if (ann.id.length != 64) return
        if (ann.port !in 1..65535) return

        val sourceAddress = packet.address as? Inet4Address
            ?: return

        val host = sourceAddress.hostAddress ?: return

        val now = System.currentTimeMillis()

        // Identify addresses belonging to this local machine.
        val localAddresses = localIpv4Addresses()

        val usableRemote = isUsableRemoteAddress(
            sourceAddress,
            localAddresses
        )

        // Keep the discovered identity even if its source address
        // cannot be used as a direct connection candidate.
        val entry = seen.getOrPut(ann.id) {
            Seen(
                ann.name.take(60),
                ann.device,
                ann.port,
                now
            )
        }

        entry.name = ann.name.take(60)
        entry.device = ann.device
        entry.port = ann.port
        entry.lastSeen = now

        if (usableRemote) {

            // Store only addresses that are not owned locally.
            entry.addresses[host] = now

        } else {

            // Remove an address if it is identified as local.
            entry.addresses.remove(host)
        }

        publish()

        // Don't send discovery replies back to our own interface.
        // A remote device still receives an immediate reply.
        if (ann.first && usableRemote) {

            val reply = json.encodeToString(
                Announcement.serializer(),
                self
            ).toByteArray()

            runCatching {
                sender?.send(
                    DatagramPacket(
                        reply,
                        reply.size,
                        sourceAddress,
                        port
                    )
                )
            }
        }
    }

    // ----------------------------------------------------------------
    // Remove stale peers and addresses
    // ----------------------------------------------------------------

    private fun prune() {
        val cutoff = System.currentTimeMillis() - PEER_TTL_MILLIS

        for ((id, entry) in seen) {

            // Remove expired network addresses independently.
            entry.addresses.entries.removeIf {
                it.value < cutoff
            }

            // Retain the device only while its announcements
            // are still being received.
            if (entry.lastSeen < cutoff) {
                seen.remove(id, entry)
            }
        }

        publish()
    }

    // ----------------------------------------------------------------
    // Publish discovered peers
    // ----------------------------------------------------------------

    private fun publish() {

        val list = seen.entries.map { (id, entry) ->

            val candidates = entry.addresses.entries
                .sortedByDescending { it.value }
                .map { address ->
                    LinkCandidate(
                        LinkKind.LAN,
                        address.key,
                        entry.port
                    )
                }

            // A peer may have zero LAN candidates.
            // It remains visible for development transports
            // such as the ADB route.
            Peer(
                id = id,
                name = entry.name,
                device = entry.device,
                candidates = candidates
            )

        }.sortedBy {
            it.name.lowercase()
        }

        if (list != _peers.value) {
            _peers.value = list
        }
    }

    // ----------------------------------------------------------------
    // Close discovery
    // ----------------------------------------------------------------

    override fun close() {
        closed = true

        runCatching {
            receiver?.close()
        }

        runCatching {
            sender?.close()
        }

        seen.clear()
        _peers.value = emptyList()
    }
}
