package app.proxsh.laptop

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
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.UUID

/** One finished thing in the activity list. */
class ActivityEntry(
    val direction: Direction,
    val peerName: String,
    val title: String,
    val detail: String,
    val file: File? = null,
    val text: String? = null,
    val ok: Boolean = true,
    val at: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
)

/** One transfer happening right now. */
class LiveTransfer(
    val id: String,
    val direction: Direction,
    val peerName: String,
    val name: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Double,
    val link: LinkKind?,
    /** Set while waiting for the other device to accept. */
    val waitingCode: String? = null,
)

/**
 * Everything the screens show and every action they can take. The node does the real work; this
 * only keeps the lists the window needs.
 */
class AppModel(
    val node: ProxshNode,
    private val scope: CoroutineScope,
    initial: Settings,
    private val saveSettings: (Settings) -> Unit,
    private val needAttention: (title: String, message: String) -> Unit,
) {
    var settings by mutableStateOf(initial)
        private set
    var selectedId by mutableStateOf<String?>(null)
    var incoming by mutableStateOf<IncomingOffer?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
    var showSettings by mutableStateOf(false)

    val activity = mutableStateListOf<ActivityEntry>()
    val live = mutableStateMapOf<String, LiveTransfer>()
    private val waiting = ArrayDeque<IncomingOffer>()

    init {
        node.setReceiveMode(settings.mode)
        scope.launch(Dispatchers.Main) { node.events.collect { handle(it) } }
    }

    private fun handle(event: NodeEvent) {
        when (event) {
            is IncomingOffer -> {
                if (incoming == null) incoming = event else waiting.addLast(event)
                needAttention("${event.peerName} wants to send you something", summary(event))
            }
            is WaitingForAnswer -> live[event.transferId] = LiveTransfer(
                event.transferId, Direction.SEND, event.peerName, "Waiting for ${event.peerName} to accept", 0, 0, 0.0, null, event.checkCode,
            )
            is TransferProgress -> live[event.transferId] = LiveTransfer(
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
            activity.add(0, ActivityEntry(Direction.RECEIVE, event.peerName, "Receive stopped", event.message, ok = false))
            return
        }
        for (text in event.texts) {
            copyToClipboard(text)
            activity.add(0, ActivityEntry(Direction.RECEIVE, event.peerName, text.lineSequence().first().take(80), "Text from ${event.peerName} · copied to your clipboard", text = text))
        }
        for (file in event.files) {
            activity.add(0, ActivityEntry(Direction.RECEIVE, event.peerName, file.name, "${formatBytes(file.length())} from ${event.peerName}", file = file))
        }
    }

    private fun summary(offer: IncomingOffer): String {
        val first = offer.items.first()
        val more = if (offer.items.size > 1) " and ${offer.items.size - 1} more" else ""
        return if (first.isText) "Text$more" else "${first.name}$more (${formatBytes(offer.totalBytes)})"
    }

    // ---------------------------------------------------------------- actions

    fun answer(accept: Boolean, always: Boolean) {
        val offer = incoming ?: return
        node.answer(offer.offerId, accept, always)
        incoming = waiting.removeFirstOrNull()
    }

    fun sendFiles(peer: Peer, files: List<File>) {
        val real = files.filter { it.isFile }
        if (real.isEmpty()) {
            notice = if (files.any { it.isDirectory }) "Folders can't be sent yet. Put them in a zip first." else "Nothing to send."
            return
        }
        notice = null
        scope.launch(Dispatchers.Main) {
            val result = node.send(peer, real.map { OutgoingItem.file(it) })
            finishedSend(peer, real.joinToString(", ") { it.name }, result, real.sumOf { it.length() })
        }
    }

    fun sendText(peer: Peer, text: String) {
        if (text.isBlank()) return
        notice = null
        scope.launch(Dispatchers.Main) {
            val result = node.send(peer, listOf(OutgoingItem.Text(text)))
            finishedSend(peer, text.lineSequence().first().take(80), result, text.length.toLong(), isText = true)
        }
    }

    private fun finishedSend(peer: Peer, title: String, result: SendResult, bytes: Long, isText: Boolean = false) {
        if (result.ok) {
            val link = result.link?.label?.let { " · $it" }.orEmpty()
            val size = if (isText) "Text" else formatBytes(bytes)
            activity.add(0, ActivityEntry(Direction.SEND, peer.name, title, "$size sent to ${peer.name}$link"))
        } else {
            activity.add(0, ActivityEntry(Direction.SEND, peer.name, title, result.message, ok = false))
            notice = result.message
        }
    }

    fun open(file: File) {
        runCatching { Desktop.getDesktop().open(file) }.onFailure { notice = "Couldn't open ${file.name}. It may have been moved." }
    }

    fun reveal(file: File) {
        if (!file.exists()) {
            notice = "${file.name} was moved or deleted."
            return
        }
        runCatching { ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start() }
    }

    fun copyToClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    fun readClipboard(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
    }.getOrNull()

    fun setMode(mode: ReceiveMode) {
        node.setReceiveMode(mode)
        update(settings.copy(mode = mode))
    }

    fun setDeviceName(name: String) = update(settings.copy(deviceName = name.trim().take(40).ifBlank { settings.deviceName }))

    fun setReceiveDir(dir: File) = update(settings.copy(receiveDir = dir.absolutePath))

    fun revoke(fingerprint: String) = node.revoke(fingerprint)

    private fun update(next: Settings) {
        settings = next
        saveSettings(next)
    }
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
