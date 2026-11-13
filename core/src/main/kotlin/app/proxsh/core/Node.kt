package app.proxsh.core

import app.proxsh.core.discovery.Announcement
import app.proxsh.core.discovery.DEFAULT_DISCOVERY_PORT
import app.proxsh.core.discovery.Discovery
import app.proxsh.core.discovery.Peer
import app.proxsh.core.identity.Identity
import app.proxsh.core.identity.TlsFactory
import app.proxsh.core.link.LinkCandidate
import app.proxsh.core.link.LinkChooser
import app.proxsh.core.link.LinkKind
import app.proxsh.core.link.LinkProber
import app.proxsh.core.link.LinkScore
import app.proxsh.core.transfer.CancelledByPeer
import app.proxsh.core.transfer.OutgoingItem
import app.proxsh.core.transfer.Receiver
import app.proxsh.core.transfer.SendOutcome
import app.proxsh.core.transfer.Sender
import app.proxsh.core.trust.CheckCode
import app.proxsh.core.trust.TrustStore
import app.proxsh.core.wire.Bye
import app.proxsh.core.wire.FrameIO
import app.proxsh.core.wire.FrameType
import app.proxsh.core.wire.Hello
import app.proxsh.core.wire.Msg
import app.proxsh.core.wire.Offer
import app.proxsh.core.wire.PROTOCOL_VERSION
import app.proxsh.core.wire.Ping
import app.proxsh.core.wire.Pong
import app.proxsh.core.wire.ProbeDone
import app.proxsh.core.wire.ProbeResult
import app.proxsh.core.wire.ProbeStart
import app.proxsh.core.wire.ProtocolException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

enum class ReceiveMode { ASK, TRUSTED_ONLY, OFF }
enum class Direction { SEND, RECEIVE }

class NodeConfig(
    /** Where the identity and the trusted-devices list are kept. */
    val dataDir: File,
    /** Where received files are saved. */
    val receiveDir: File,
    val deviceName: String,
    /** "laptop" or "phone". Only used to pick an icon. */
    val deviceKind: String = "laptop",
    /** 0 picks any free port. */
    val tcpPort: Int = 53317,
    val discoveryPort: Int = DEFAULT_DISCOVERY_PORT,
    val enableDiscovery: Boolean = true,
)

data class ItemSummary(val name: String, val size: Long, val isText: Boolean)

sealed interface NodeEvent

/** Another device wants to send something. Answer with [ProxshNode.answer]. */
data class IncomingOffer(
    val offerId: String,
    val peerName: String,
    val peerFingerprint: String,
    /** The same 4 digits appear on the sender's screen. */
    val checkCode: String,
    val items: List<ItemSummary>,
    val totalBytes: Long,
) : NodeEvent

/** We offered something and are waiting for the other device to accept. */
data class WaitingForAnswer(val transferId: String, val peerName: String, val checkCode: String) : NodeEvent

data class TransferProgress(
    val transferId: String,
    val direction: Direction,
    val peerName: String,
    val currentName: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Double,
    val link: LinkKind?,
) : NodeEvent

data class TransferFinished(
    val transferId: String,
    val direction: Direction,
    val peerName: String,
    val ok: Boolean,
    val message: String,
    val files: List<File> = emptyList(),
    val texts: List<String> = emptyList(),
) : NodeEvent

data class SendResult(val ok: Boolean, val message: String, val link: LinkKind?)

/**
 * One ProxSh device: finds others nearby, receives (asking first unless the sender is trusted),
 * and sends over whichever link measures fastest, carrying on after drops.
 * It has no screen. The laptop and phone apps just show what it reports.
 */
class ProxshNode(private val config: NodeConfig, private val scope: CoroutineScope) : Closeable {
    private class Answer(val accept: Boolean, val always: Boolean)

    val identity: Identity = Identity.loadOrCreate(File(config.dataDir, "identity.p12"))
    val trustStore = TrustStore(File(config.dataDir, "trusted.json"))

    private val tls = TlsFactory(identity)
    private val chooser = LinkChooser(TlsProber())
    private val _mode = MutableStateFlow(ReceiveMode.ASK)
    private val _events = MutableSharedFlow<NodeEvent>(extraBufferCapacity = 256)
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Answer>>()
    private val openSockets = ConcurrentHashMap.newKeySet<SSLSocket>()
    private val connectionSlots = Semaphore(MAX_CONNECTIONS)
    private var server: SSLServerSocket? = null
    private var discovery: Discovery? = null

    val receiveMode: StateFlow<ReceiveMode> get() = _mode
    val events: SharedFlow<NodeEvent> get() = _events
    val peers: StateFlow<List<Peer>> get() = _peers
    val port: Int get() = server?.localPort ?: 0

    fun setReceiveMode(mode: ReceiveMode) {
        _mode.value = mode
    }

    fun start() {
        val s = tls.server(config.tcpPort)
        server = s
        scope.launch(Dispatchers.IO) { acceptLoop(s) }
        if (config.enableDiscovery) {
            val d = Discovery(
                Announcement(id = identity.fingerprint, name = config.deviceName, device = config.deviceKind, port = s.localPort),
                scope,
                config.discoveryPort,
            )
            discovery = d
            d.start()
            scope.launch { d.peers.collect { _peers.value = it } }
        }
    }

    /** Look for devices again right now. */
    fun refresh() {
        discovery?.refresh()
    }

    /** Answer an [IncomingOffer]. [always] also trusts that device so it is never asked about again. */
    fun answer(offerId: String, accept: Boolean, always: Boolean = false) {
        pending[offerId]?.complete(Answer(accept, always))
    }

    fun revoke(fingerprint: String) = trustStore.revoke(fingerprint)

    // ---------------------------------------------------------------- sending

    suspend fun send(peer: Peer, items: List<OutgoingItem>, allowMetered: Boolean = true): SendResult {
        val transferId = UUID.randomUUID().toString()
        var lastError = "Could not reach ${peer.name}"
        var link: LinkKind? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            // Measured fresh each time, so after a drop the transfer carries on over the faster link.
            val score = chooser.best(peer.candidates, allowMetered)
                ?: return SendResult(false, "Could not reach ${peer.name}", link)
            link = score.candidate.kind
            try {
                return withContext(Dispatchers.IO) { sendOnce(peer, score.candidate, items, transferId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                return SendResult(false, e.message ?: "Could not verify the other device", link)
            } catch (e: IOException) {
                lastError = e.message ?: "Connection lost"
                delay(700L * (attempt + 1))
            }
        }
        return SendResult(false, lastError, link)
    }

    private suspend fun sendOnce(peer: Peer, candidate: LinkCandidate, items: List<OutgoingItem>, transferId: String): SendResult {
        val sock = tls.connect(candidate.host, candidate.port)
        openSockets.add(sock)
        val onCancel = currentCoroutineContext().job.invokeOnCompletion { runCatching { sock.close() } }
        try {
            val fingerprint = TlsFactory.peerFingerprint(sock)
            if (fingerprint != peer.id) throw SecurityException("That is not the device it claimed to be")
            val io = FrameIO(sock.getInputStream(), sock.getOutputStream())
            io.writeControl(Hello(PROTOCOL_VERSION, config.deviceName, config.deviceKind))
            val hello = io.readControl() as? Hello ?: throw ProtocolException("Expected hello")
            val code = CheckCode.of(identity.fingerprint, fingerprint)
            _events.emit(WaitingForAnswer(transferId, hello.name, code))

            sock.soTimeout = ANSWER_WAIT_MILLIS
            var lastEmit = 0L
            var firstNanos = 0L
            var firstBytes = 0L
            val outcome = Sender(io).send(items) { p ->
                val now = System.nanoTime()
                if (firstNanos == 0L) {
                    // Chunks the other side already had are not "sent", so speed counts from here.
                    firstNanos = now
                    firstBytes = p.doneBytes
                }
                if (now - lastEmit > PROGRESS_EVERY_NANOS || p.doneBytes == p.totalBytes) {
                    lastEmit = now
                    val seconds = (now - firstNanos) / 1e9
                    val speed = if (seconds > 0.05) (p.doneBytes - firstBytes) / seconds else 0.0
                    _events.tryEmit(
                        TransferProgress(transferId, Direction.SEND, hello.name, p.name, p.doneBytes, p.totalBytes, speed, candidate.kind),
                    )
                }
            }
            val result = when (outcome) {
                is SendOutcome.Done -> SendResult(true, "Sent to ${hello.name}", candidate.kind)
                is SendOutcome.Rejected -> SendResult(false, outcome.reason, candidate.kind)
            }
            _events.emit(TransferFinished(transferId, Direction.SEND, hello.name, result.ok, result.message))
            return result
        } finally {
            onCancel.dispose()
            openSockets.remove(sock)
            runCatching { sock.close() }
        }
    }

    // ---------------------------------------------------------------- receiving

    private suspend fun acceptLoop(s: SSLServerSocket) {
        while (scope.isActive) {
            val sock = try {
                s.accept() as SSLSocket
            } catch (e: IOException) {
                return
            }
            if (!connectionSlots.tryAcquire()) {
                runCatching { sock.close() }
                continue
            }
            openSockets.add(sock)
            scope.launch(Dispatchers.IO) {
                try {
                    handle(sock)
                } finally {
                    openSockets.remove(sock)
                    runCatching { sock.close() }
                    connectionSlots.release()
                }
            }
        }
    }

    private suspend fun handle(sock: SSLSocket) {
        try {
            sock.soTimeout = FIRST_MESSAGE_MILLIS
            sock.startHandshake()
            val fingerprint = TlsFactory.peerFingerprint(sock)
            val io = FrameIO(sock.getInputStream(), sock.getOutputStream())
            val hello = io.readControl() as? Hello ?: return
            io.writeControl(Hello(PROTOCOL_VERSION, config.deviceName, config.deviceKind))
            when (val first = io.readControl()) {
                is Offer -> handleOffer(sock, io, fingerprint, hello, first)
                else -> serveProbe(io, first)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The other device went away or sent nonsense. Nothing to do.
        }
    }

    private suspend fun handleOffer(sock: SSLSocket, io: FrameIO, fingerprint: String, hello: Hello, offer: Offer) {
        val receiver = Receiver(io, config.receiveDir, File(config.receiveDir, ".proxsh-partial"))
        val known = trustStore.get(fingerprint)
        val mode = _mode.value

        if (mode == ReceiveMode.OFF) return receiver.reject(offer.offerId, "This device is not receiving right now")
        if (mode == ReceiveMode.TRUSTED_ONLY && known == null) return receiver.reject(offer.offerId, "This device only accepts from trusted devices")
        receiver.check(offer)?.let { return receiver.reject(offer.offerId, it) }

        val localId = UUID.randomUUID().toString()
        val summaries = offer.items.map { ItemSummary(it.name, it.size, it.kind == "text") }
        val total = offer.items.sumOf { it.size }
        var trustNow = false

        if (known?.autoAccept != true) {
            val deferred = CompletableDeferred<Answer>()
            pending[localId] = deferred
            _events.emit(
                IncomingOffer(localId, hello.name, fingerprint, CheckCode.of(identity.fingerprint, fingerprint), summaries, total),
            )
            sock.soTimeout = 0
            val answer = try {
                withTimeoutOrNull(ANSWER_WAIT_MILLIS.toLong() - 10_000) { deferred.await() }
            } finally {
                pending.remove(localId)
            }
            if (answer == null || !answer.accept) {
                return receiver.reject(offer.offerId, if (answer == null) "No answer" else "Declined")
            }
            trustNow = answer.always
        }
        if (trustNow) trustStore.add(fingerprint, hello.name) else if (known != null) trustStore.touch(fingerprint)

        sock.soTimeout = TRANSFER_IDLE_MILLIS
        var lastEmit = 0L
        var firstNanos = 0L
        var firstBytes = 0L
        try {
            val result = receiver.accept(offer, fingerprint) { p ->
                val now = System.nanoTime()
                if (firstNanos == 0L) {
                    firstNanos = now
                    firstBytes = p.doneBytes
                }
                if (now - lastEmit > PROGRESS_EVERY_NANOS || p.doneBytes == p.totalBytes) {
                    lastEmit = now
                    val seconds = (now - firstNanos) / 1e9
                    val speed = if (seconds > 0.05) (p.doneBytes - firstBytes) / seconds else 0.0
                    _events.tryEmit(
                        TransferProgress(localId, Direction.RECEIVE, hello.name, p.name, p.doneBytes, p.totalBytes, speed, LinkKind.LAN),
                    )
                }
            }
            _events.emit(TransferFinished(localId, Direction.RECEIVE, hello.name, true, "Received from ${hello.name}", result.files, result.texts))
        } catch (e: CancelledByPeer) {
            _events.emit(TransferFinished(localId, Direction.RECEIVE, hello.name, false, "${hello.name} cancelled"))
        } catch (e: IOException) {
            _events.emit(TransferFinished(localId, Direction.RECEIVE, hello.name, false, e.message ?: "Connection lost"))
        }
    }

    /** Answers speed tests from other devices choosing which link to use. */
    private fun serveProbe(io: FrameIO, first: Msg) {
        var msg: Msg = first
        while (true) {
            when (msg) {
                is Ping -> io.writeControl(Pong(msg.nonce))
                is ProbeStart -> {
                    var bytes = 0L
                    var startNanos = 0L
                    var sawFirst = false
                    while (true) {
                        val frame = io.readFrame()
                        if (frame.type == FrameType.BURST) {
                            if (!sawFirst) {
                                sawFirst = true
                                startNanos = System.nanoTime()
                            } else {
                                bytes += frame.payload.size
                            }
                            if (bytes > MAX_PROBE_BYTES) throw ProtocolException("Probe too large")
                        } else if (frame.type == FrameType.CONTROL && frame.control() is ProbeDone) {
                            break
                        } else {
                            throw ProtocolException("Unexpected frame during probe")
                        }
                    }
                    io.writeControl(ProbeResult(bytes, maxOf(1L, System.nanoTime() - startNanos)))
                }
                is Bye -> return
                else -> return
            }
            msg = io.readControl()
        }
    }

    /** Measures a link to another device: round-trip time plus a short burst of data. */
    private inner class TlsProber : LinkProber {
        override suspend fun probe(candidate: LinkCandidate): LinkScore? = withContext(Dispatchers.IO) {
            val sock = try {
                tls.connect(candidate.host, candidate.port, 2000)
            } catch (e: IOException) {
                return@withContext null
            }
            try {
                sock.soTimeout = 3000
                val io = FrameIO(sock.getInputStream(), sock.getOutputStream())
                io.writeControl(Hello(PROTOCOL_VERSION, config.deviceName, config.deviceKind))
                io.readControl() as? Hello ?: return@withContext null
                val t0 = System.nanoTime()
                io.writeControl(Ping(1))
                io.readControl() as? Pong ?: return@withContext null
                val rtt = (System.nanoTime() - t0) / 1e6

                io.writeControl(ProbeStart)
                val buffer = ByteArray(64 * 1024)
                val start = System.nanoTime()
                var sent = 0L
                while (true) {
                    val elapsed = System.nanoTime() - start
                    if (elapsed >= 300_000_000L && sent >= 256 * 1024) break
                    if (elapsed >= 1_500_000_000L) break
                    io.writeBurst(buffer, buffer.size)
                    sent += buffer.size
                }
                io.writeControl(ProbeDone)
                val result = io.readControl() as? ProbeResult ?: return@withContext null
                io.writeControl(Bye)
                LinkScore(candidate, rtt, result.bytes * 1e9 / result.nanos)
            } catch (e: IOException) {
                null
            } finally {
                runCatching { sock.close() }
            }
        }
    }

    override fun close() {
        runCatching { discovery?.close() }
        runCatching { server?.close() }
        for (s in openSockets) runCatching { s.close() }
        pending.values.forEach { it.complete(Answer(false, false)) }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val MAX_CONNECTIONS = 16
        const val FIRST_MESSAGE_MILLIS = 10_000
        const val ANSWER_WAIT_MILLIS = 120_000
        const val TRANSFER_IDLE_MILLIS = 30_000
        const val MAX_PROBE_BYTES = 64L * 1024 * 1024
        private const val PROGRESS_EVERY_NANOS = 150_000_000L
    }
}
