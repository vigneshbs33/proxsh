package app.proxsh.android

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.proxsh.core.Direction
import app.proxsh.core.IncomingOffer
import app.proxsh.core.NodeEvent
import app.proxsh.core.ProxshNode
import app.proxsh.core.ReceiveMode
import app.proxsh.core.SendResult
import app.proxsh.core.TransferFinished
import app.proxsh.core.TransferProgress
import app.proxsh.core.WaitingForAnswer
import app.proxsh.core.discovery.Peer
import app.proxsh.core.link.LinkKind
import app.proxsh.core.transfer.OutgoingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** One finished thing in the activity list. */
class Entry(
    val direction: Direction,
    val peerName: String,
    val title: String,
    val detail: String,
    val uri: Uri? = null,
    val text: String? = null,
    val ok: Boolean = true,
    val at: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
)

/** One transfer happening right now. */
class Live(
    val id: String,
    val direction: Direction,
    val peerName: String,
    val name: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Double,
    val link: LinkKind?,
    val waitingCode: String? = null,
)

/**
 * Everything the phone screens show, and every action they can take. It lives as long as the app,
 * so requests and finished transfers are noticed even when no screen is open.
 */
class PhoneModel(
    private val app: Application,
    val node: ProxshNode,
    private val scope: CoroutineScope,
    private val notifier: Notifier,
) {
    private val prefs = app.getSharedPreferences("proxsh", Context.MODE_PRIVATE)

    var selectedId by mutableStateOf<String?>(null)
    var incoming by mutableStateOf<IncomingOffer?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
    var showSettings by mutableStateOf(false)

    val activity = mutableStateListOf<Entry>()
    val live = mutableStateMapOf<String, Live>()
    private val waiting = ArrayDeque<IncomingOffer>()

    init {
        val saved = runCatching { ReceiveMode.valueOf(prefs.getString("mode", "ASK")!!) }.getOrDefault(ReceiveMode.ASK)
        node.setReceiveMode(saved)
        scope.launch { node.events.collect { handle(it) } }
    }

    fun selectedPeer(): Peer? {
        val peers = node.peers.value
        return peers.firstOrNull { it.id == selectedId } ?: peers.firstOrNull()
    }

    private fun handle(event: NodeEvent) {
        when (event) {
            is IncomingOffer -> {
                if (incoming == null) incoming = event else waiting.addLast(event)
                notifier.offer(event)
            }
            is WaitingForAnswer -> live[event.transferId] = Live(
                event.transferId, Direction.SEND, event.peerName, "Waiting for ${event.peerName} to accept", 0, 0, 0.0, null, event.checkCode,
            )
            is TransferProgress -> live[event.transferId] = Live(
                event.transferId, event.direction, event.peerName, event.currentName, event.doneBytes, event.totalBytes, event.bytesPerSecond, event.link,
            )
            is TransferFinished -> {
                live.remove(event.transferId)
                if (event.direction == Direction.RECEIVE) finishedReceive(event)
            }
        }
    }

    private fun finishedReceive(event: TransferFinished) {
        if (!event.ok) {
            activity.add(0, Entry(Direction.RECEIVE, event.peerName, "Receive stopped", event.message, ok = false))
            return
        }
        for (text in event.texts) {
            copyToClipboard(text)
            activity.add(0, Entry(Direction.RECEIVE, event.peerName, text.lineSequence().first().take(80), "Text from ${event.peerName} · copied to your clipboard", text = text))
            notifier.receivedText(UUID.randomUUID().toString(), text, event.peerName)
        }
        for (file in event.files) {
            scope.launch {
                val size = formatBytes(file.length())
                val uri = withContext(Dispatchers.IO) { Storage.saveToDownloads(app, file) }
                if (uri != null) {
                    activity.add(0, Entry(Direction.RECEIVE, event.peerName, file.name, "$size from ${event.peerName} · saved in Downloads/ProxSh", uri = uri))
                } else {
                    activity.add(0, Entry(Direction.RECEIVE, event.peerName, file.name, "Couldn't save it to Downloads. Free up some space and ask again.", ok = false))
                }
                notifier.receivedFile(UUID.randomUUID().toString(), file.name, size, event.peerName, uri)
            }
        }
    }

    // ---------------------------------------------------------------- actions

    fun answer(accept: Boolean, always: Boolean) {
        val offer = incoming ?: return
        answerById(offer.offerId, accept, always)
    }

    fun answerById(offerId: String, accept: Boolean, always: Boolean) {
        node.answer(offerId, accept, always)
        notifier.cancelOffer(offerId)
        if (incoming?.offerId == offerId) incoming = waiting.removeFirstOrNull()
        else waiting.removeAll { it.offerId == offerId }
    }

    fun sendUris(peer: Peer, uris: List<Uri>, text: String? = null, onDone: ((SendResult) -> Unit)? = null) {
        notice = null
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                buildList {
                    for (uri in uris) UriSource.of(app.contentResolver, uri)?.let { add(OutgoingItem.Content(it)) }
                    if (uris.isEmpty() && !text.isNullOrBlank()) add(OutgoingItem.Text(text))
                }
            }
            if (items.isEmpty()) {
                val none = SendResult(false, "Nothing to send.", null)
                notice = none.message
                onDone?.invoke(none)
                return@launch
            }
            val title = items.joinToString(", ") { if (it is OutgoingItem.Content) it.source.name else "Text" }.take(80)
            val bytes = items.sumOf { if (it is OutgoingItem.Content) it.source.size else 0L }
            val result = node.send(peer, items)
            finishedSend(peer, title, result, bytes, isText = uris.isEmpty())
            onDone?.invoke(result)
        }
    }

    fun sendText(peer: Peer, text: String) {
        if (text.isNotBlank()) sendUris(peer, emptyList(), text)
    }

    private fun finishedSend(peer: Peer, title: String, result: SendResult, bytes: Long, isText: Boolean) {
        if (result.ok) {
            val link = result.link?.label?.let { " · $it" }.orEmpty()
            val size = if (isText) "Text" else formatBytes(bytes)
            activity.add(0, Entry(Direction.SEND, peer.name, title, "$size sent to ${peer.name}$link"))
        } else {
            activity.add(0, Entry(Direction.SEND, peer.name, title, result.message, ok = false))
            notice = result.message
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("ProxSh", text)) }
    }

    fun readClipboard(): String? {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString() }.getOrNull()
    }

    fun setMode(mode: ReceiveMode) {
        node.setReceiveMode(mode)
        prefs.edit().putString("mode", mode.name).apply()
    }

    fun revoke(fingerprint: String) = node.revoke(fingerprint)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (value < 10) "%.1f %s".format(value, units[i]) else "%.0f %s".format(value, units[i])
}
