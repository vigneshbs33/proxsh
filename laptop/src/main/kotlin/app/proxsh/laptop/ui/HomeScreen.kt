package app.proxsh.laptop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proxsh.core.Direction
import app.proxsh.core.ReceiveMode
import app.proxsh.core.discovery.Peer
import app.proxsh.laptop.ActivityEntry
import app.proxsh.laptop.AppModel
import app.proxsh.laptop.LiveTransfer
import app.proxsh.laptop.formatBytes
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun HomeScreen(model: AppModel, pickFiles: () -> List<File>, chooseFolder: () -> File?) {
    val peers by model.node.peers.collectAsState()
    val mode by model.node.receiveMode.collectAsState()
    val trusted by model.node.trustStore.devices.collectAsState()
    val selected = peers.firstOrNull { it.id == model.selectedId } ?: peers.firstOrNull()

    Box(Modifier.fillMaxSize().background(Paper.canvas)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(model, peers, selected, mode, trustedCount = trusted.size)
            Desk(model, selected, pickFiles)
        }
        model.incoming?.let { IncomingDialog(it, model) }
        if (model.showSettings) SettingsPanel(model, chooseFolder)
    }
}

// ------------------------------------------------------------------ sidebar

@Composable
private fun Sidebar(model: AppModel, peers: List<Peer>, selected: Peer?, mode: ReceiveMode, trustedCount: Int) {
    Column(
        Modifier
            .width(304.dp)
            .fillMaxHeight()
            .background(Paper.surface)
            .drawBehind { drawLine(Paper.line, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), 1f) }
            .padding(24.dp),
    ) {
        BasicText("ProxSh", style = Type.title.copy(fontSize = 26.sp))
        Spacer(Modifier.height(2.dp))
        BasicText("Visible as ${model.settings.deviceName}", style = Type.caption)

        Spacer(Modifier.height(32.dp))
        BasicText("Nearby", style = Type.label)
        Spacer(Modifier.height(12.dp))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (peers.isEmpty()) {
                EmptyNearby()
            } else {
                for (peer in peers) key(peer.id) {
                    PeerRow(peer, isSelected = peer.id == selected?.id, onClick = { model.selectedId = peer.id })
                }
            }
        }

        BasicText("Who can send to you", style = Type.caption)
        Spacer(Modifier.height(8.dp))
        Segmented(
            options = listOf(ReceiveMode.ASK to "Ask me", ReceiveMode.TRUSTED_ONLY to "Trusted only", ReceiveMode.OFF to "Off"),
            selected = mode,
            onSelect = { model.setMode(it) },
        )
        Spacer(Modifier.height(10.dp))
        TextAction(
            if (trustedCount == 0) "Settings" else "Settings · $trustedCount trusted",
            onClick = { model.showSettings = true },
            color = Paper.inkSoft,
        )
    }
}

@Composable
private fun EmptyNearby() {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Paper.sunken), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.Tray, Paper.inkFaint, 26.dp)
        }
        BasicText("Open ProxSh on your phone", style = Type.label)
        BasicText(
            "It will appear here by itself. Both devices need to be on the same Wi-Fi, or the phone's hotspot.",
            style = Type.soft.copy(fontSize = 12.5.sp),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun PeerRow(peer: Peer, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .appearSoftly()
            .clip(shape)
            .background(if (isSelected) Paper.accentSoft else Paper.surface)
            .border(1.dp, if (isSelected) Paper.accent else Paper.line, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.rippleOnce(Paper.accent)) {
            DeviceBadge(if (peer.device == "phone") Glyph.Phone else Glyph.Laptop)
        }
        Column(Modifier.weight(1f)) {
            BasicText(peer.name, style = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(peer.candidates.firstOrNull()?.kind?.label?.let { "Ready · $it" } ?: "Ready", style = Type.caption)
        }
        if (isSelected) GlyphIcon(Glyph.Check, Paper.accent, 18.dp)
    }
}

// ------------------------------------------------------------------ the desk

@Composable
private fun Desk(model: AppModel, selected: Peer?, pickFiles: () -> List<File>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 36.dp)) {
        if (selected != null) {
            BasicText("Send to", style = Type.display)
            BasicText(selected.name, style = Type.displayTurn, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            BasicText("Waiting for", style = Type.display)
            BasicText("your phone", style = Type.displayTurn)
        }
        Spacer(Modifier.height(24.dp))

        DropZone(
            enabled = selected != null,
            onFiles = { files -> selected?.let { model.sendFiles(it, files) } },
            onPick = { selected?.let { peer -> pickFiles().takeIf { it.isNotEmpty() }?.let { model.sendFiles(peer, it) } } },
            onClipboard = { selected?.let { peer -> model.readClipboard()?.let { model.sendText(peer, it) } } },
            onText = { text -> selected?.let { model.sendText(it, text) } },
        )

        model.notice?.let {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Paper.warnSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(it, style = Type.body.copy(color = Paper.warn, fontSize = 13.sp), modifier = Modifier.weight(1f))
                TextAction("Dismiss", onClick = { model.notice = null }, color = Paper.warn)
            }
        }

        Spacer(Modifier.height(28.dp))
        BasicText("Activity", style = Type.title)
        Spacer(Modifier.height(12.dp))
        ActivityList(model)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun DropZone(
    enabled: Boolean,
    onFiles: (List<File>) -> Unit,
    onPick: () -> Unit,
    onClipboard: () -> Unit,
    onText: (String) -> Unit,
) {
    var over by mutableStateOf(false)
    var text by remember { mutableStateOf("") }
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
    val target = remember(enabled) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { over = true }
            override fun onExited(event: DragAndDropEvent) { over = false }
            override fun onEnded(event: DragAndDropEvent) { over = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                over = false
                if (!enabled) return false
                val data = runCatching { event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) }.getOrNull()
                val files = (data as? List<*>)?.filterIsInstance<File>().orEmpty()
                if (files.isNotEmpty()) onFiles(files)
                return files.isNotEmpty()
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (over) Paper.accentSoft else Paper.surface)
            .drawBehind {
                drawRoundRect(
                    if (over) Paper.accent else Paper.lineStrong,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.5f * density, pathEffect = dash),
                )
            }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
                target = target,
            )
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(Glyph.Tray, if (enabled) Paper.accent else Paper.inkFaint, 34.dp)
        Spacer(Modifier.height(10.dp))
        BasicText(
            if (enabled) "Drop files here" else "Nothing nearby yet",
            style = Type.title,
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            if (enabled) "They go straight to your phone, over the fastest link." else "Open ProxSh on your phone and it will show up on the left.",
            style = Type.soft,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperButton("Choose files", onPick, primary = true, enabled = enabled)
            PaperButton("Send clipboard", onClipboard, enabled = enabled)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(text, { text = it }, "Type text or paste a link", Modifier.weight(1f), onEnter = {
                if (enabled && text.isNotBlank()) { onText(text); text = "" }
            })
            PaperButton("Send", onClick = { if (text.isNotBlank()) { onText(text); text = "" } }, enabled = enabled && text.isNotBlank())
        }
    }
}

// ------------------------------------------------------------------ activity

@Composable
private fun ActivityList(model: AppModel) {
    val live = model.live.values.toList()
    val done = model.activity.toList()
    if (live.isEmpty() && done.isEmpty()) {
        BasicText("Files you send and receive show up here.", style = Type.soft)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(live, key = { "live-" + it.id }) { LiveRow(it) }
        items(done, key = { it.id }) { ActivityRow(it, model) }
    }
}

@Composable
private fun RowShell(glyph: Glyph, tint: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit, actions: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Paper.surface).border(1.dp, Paper.line, shape).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Paper.sunken), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, tint, 20.dp)
        }
        Column(Modifier.weight(1f)) { content() }
        actions()
    }
}

@Composable
private fun LiveRow(t: LiveTransfer) {
    val waiting = t.waitingCode != null
    RowShell(if (t.direction == Direction.SEND) Glyph.Arrow else Glyph.Tray, Paper.accent, {
        BasicText(t.name, style = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        if (waiting) {
            BasicText("Code ${t.waitingCode}. The same 4 digits should show on the other device.", style = Type.caption)
        } else {
            ProgressBar(if (t.totalBytes > 0) t.doneBytes.toFloat() / t.totalBytes else 0f)
            Spacer(Modifier.height(6.dp))
            val speed = if (t.bytesPerSecond > 0) " · ${formatBytes(t.bytesPerSecond.toLong())}/s" else ""
            val link = t.link?.label?.let { " · $it" }.orEmpty()
            BasicText("${formatBytes(t.doneBytes)} of ${formatBytes(t.totalBytes)}$speed$link", style = Type.caption)
        }
    })
}

@Composable
private fun ActivityRow(entry: ActivityEntry, model: AppModel) {
    val time = remember(entry.at) { SimpleDateFormat("HH:mm").format(Date(entry.at)) }
    val glyph = when {
        !entry.ok -> Glyph.Close
        entry.text != null -> Glyph.Text
        else -> Glyph.File
    }
    RowShell(
        glyph,
        if (entry.ok) Paper.inkSoft else Paper.danger,
        {
            BasicText(entry.title, style = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("${entry.detail} · $time", style = Type.caption.copy(color = if (entry.ok) Paper.inkSoft else Paper.danger), maxLines = 2)
        },
        {
            Row {
                entry.file?.let { f ->
                    TextAction("Open", { model.open(f) })
                    TextAction("Show in folder", { model.reveal(f) })
                }
                entry.text?.let { t -> TextAction("Copy", { model.copyToClipboard(t) }) }
            }
        },
    )
}

// ------------------------------------------------------------------ text field

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onEnter: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.clip(shape).background(Paper.canvas).border(1.dp, Paper.lineStrong, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) BasicText(placeholder, style = Type.body.copy(color = Paper.inkFaint))
        BasicTextField(
            value = value,
            onValueChange = { if (onEnter != null && it.endsWith("\n")) onEnter() else onChange(it) },
            textStyle = Type.body,
            cursorBrush = SolidColor(Paper.accent),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
