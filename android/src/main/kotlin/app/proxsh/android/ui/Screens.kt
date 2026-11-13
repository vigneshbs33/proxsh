package app.proxsh.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proxsh.android.Entry
import app.proxsh.android.Live
import app.proxsh.android.PhoneModel
import app.proxsh.android.Storage
import app.proxsh.android.formatBytes
import app.proxsh.core.Direction
import app.proxsh.core.IncomingOffer
import app.proxsh.core.ReceiveMode
import app.proxsh.core.discovery.Peer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(model: PhoneModel, deviceName: String, onPickFiles: () -> Unit) {
    val peers by model.node.peers.collectAsState()
    val selected = peers.firstOrNull { it.id == model.selectedId } ?: peers.firstOrNull()
    var text by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Paper.canvas)) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 32.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText("ProxSh", style = Type.title.copy(fontSize = 24.sp))
                        BasicText("Visible as $deviceName", style = Type.caption)
                    }
                    TextAction("Settings", { model.showSettings = true }, color = Paper.inkSoft)
                }
                Spacer(Modifier.height(26.dp))
                if (selected != null) {
                    BasicText("Send to", style = Type.headline)
                    BasicText(selected.name, style = Type.headlineTurn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    BasicText("Waiting for", style = Type.headline)
                    BasicText("your laptop", style = Type.headlineTurn)
                }
                Spacer(Modifier.height(22.dp))
            }

            item {
                if (peers.isEmpty()) {
                    EmptyNearby()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (peer in peers) key(peer.id) {
                            PeerRow(peer, isSelected = peer.id == selected?.id, onClick = { model.selectedId = peer.id })
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            item {
                PaperCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PaperButton("Choose files", onPickFiles, Modifier.fillMaxWidth(), primary = true, enabled = selected != null)
                        PaperButton(
                            "Send what I copied",
                            { selected?.let { peer -> model.readClipboard()?.let { model.sendText(peer, it) } } },
                            Modifier.fillMaxWidth(),
                            enabled = selected != null,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Field(text, { text = it }, "Type text or a link", Modifier.weight(1f))
                            PaperButton("Send", { selected?.let { model.sendText(it, text); text = "" } }, enabled = selected != null && text.isNotBlank())
                        }
                        BasicText(
                            if (selected != null) "Or share anything from another app and pick ProxSh." else "Open ProxSh on your laptop and it will show up here.",
                            style = Type.caption,
                        )
                    }
                }
            }

            model.notice?.let { notice ->
                item {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paper.warnSoft).padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(notice, style = Type.bodyText.copy(color = Paper.warn, fontSize = 14.sp), modifier = Modifier.weight(1f))
                        TextAction("Dismiss", { model.notice = null }, color = Paper.warn)
                    }
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                BasicText("Activity", style = Type.title)
                Spacer(Modifier.height(12.dp))
                if (model.live.isEmpty() && model.activity.isEmpty()) {
                    BasicText("Files you send and receive show up here.", style = Type.soft)
                }
            }
            items(model.live.values.toList(), key = { "live-" + it.id }) { LiveRow(it); Spacer(Modifier.height(8.dp)) }
            items(model.activity.toList(), key = { it.id }) { EntryRow(it, model); Spacer(Modifier.height(8.dp)) }
        }

        model.incoming?.let { IncomingDialog(it, model) }
        if (model.showSettings) SettingsOverlay(model)
    }
}

@Composable
private fun EmptyNearby() {
    PaperCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Paper.sunken), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Tray, Paper.inkFaint, 26.dp)
            }
            BasicText("Open ProxSh on your laptop", style = Type.label)
            BasicText(
                "It will appear here by itself. Both devices need to be on the same Wi-Fi, or this phone's hotspot.",
                style = Type.soft,
            )
        }
    }
}

@Composable
fun PeerRow(peer: Peer, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .appearSoftly()
            .clip(shape)
            .background(if (isSelected) Paper.accentSoft else Paper.surface)
            .border(1.dp, if (isSelected) Paper.accent else Paper.line, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.rippleOnce(Paper.accent)) {
            DeviceBadge(if (peer.device == "phone") Glyph.Phone else Glyph.Laptop, size = 46.dp)
        }
        Column(Modifier.weight(1f)) {
            BasicText(peer.name, style = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(peer.candidates.firstOrNull()?.kind?.label?.let { "Ready · $it" } ?: "Ready", style = Type.caption)
        }
        if (isSelected) GlyphIcon(Glyph.Check, Paper.accent, 20.dp)
    }
}

@Composable
private fun RowShell(glyph: Glyph, tint: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit, actions: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Paper.surface).border(1.dp, Paper.line, shape).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Paper.sunken), contentAlignment = Alignment.Center) {
                GlyphIcon(glyph, tint, 20.dp)
            }
            Column(Modifier.weight(1f)) { content() }
        }
        actions()
    }
}

@Composable
fun LiveRow(t: Live) {
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
private fun EntryRow(entry: Entry, model: PhoneModel) {
    val context = LocalContext.current
    val time = remember(entry.at) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.at)) }
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
            BasicText("${entry.detail} · $time", style = Type.caption.copy(color = if (entry.ok) Paper.inkSoft else Paper.danger), maxLines = 3)
        },
        {
            if (entry.uri != null || entry.text != null) {
                Row(Modifier.padding(top = 4.dp)) {
                    entry.uri?.let { uri ->
                        TextAction("Open", { openUri(context, uri, entry.title) })
                        TextAction("Share", { shareUri(context, uri, entry.title) })
                    }
                    entry.text?.let { t -> TextAction("Copy", { model.copyToClipboard(t) }) }
                }
            }
        },
    )
}

private fun openUri(context: Context, uri: Uri, name: String) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, Storage.mimeOf(name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun shareUri(context: Context, uri: Uri, name: String) {
    val send = Intent(Intent.ACTION_SEND).setType(Storage.mimeOf(name)).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ------------------------------------------------------------------ overlays

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Paper.ink.copy(alpha = 0.38f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Someone wants to send you something. Shown over everything until you answer. */
@Composable
fun IncomingDialog(offer: IncomingOffer, model: PhoneModel) {
    var always by remember(offer.offerId) { mutableStateOf(false) }
    Scrim {
        PaperCard(Modifier.fillMaxWidth().appearSoftly()) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                BasicText(offer.peerName, style = Type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText("wants to send you", style = Type.headlineTurn.copy(fontSize = 28.sp, lineHeight = 32.sp))
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (item in offer.items.take(5)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlyphIcon(if (item.isText) Glyph.Text else Glyph.File, Paper.inkSoft, 18.dp)
                            BasicText(
                                if (item.isText) "Text, ${formatBytes(item.size)}" else item.name,
                                style = Type.bodyText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            if (!item.isText) BasicText(formatBytes(item.size), style = Type.caption)
                        }
                    }
                    if (offer.items.size > 5) BasicText("and ${offer.items.size - 5} more", style = Type.caption)
                }
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Paper.sunken).padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(offer.checkCode, style = Type.code)
                    BasicText("Same code on ${offer.peerName}? Then it's really them.", style = Type.caption)
                }
                Spacer(Modifier.height(8.dp))
                CheckRow(always, "Always accept from this device", { always = it })
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperButton("Decline", { model.answer(false, false) }, Modifier.weight(1f))
                    PaperButton("Accept", { model.answer(true, always) }, Modifier.weight(1f), primary = true)
                }
            }
        }
    }
}

@Composable
fun SettingsOverlay(model: PhoneModel) {
    val trusted by model.node.trustStore.devices.collectAsState()
    val mode by model.node.receiveMode.collectAsState()
    val time = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    Scrim {
        PaperCard(Modifier.fillMaxWidth().appearSoftly()) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText("Your", style = Type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp))
                        BasicText("settings", style = Type.headlineTurn.copy(fontSize = 28.sp, lineHeight = 32.sp))
                    }
                    PaperButton("Done", { model.showSettings = false }, primary = true)
                }
                Spacer(Modifier.height(22.dp))
                BasicText("Who can send to you", style = Type.label)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(ReceiveMode.ASK to "Ask me", ReceiveMode.TRUSTED_ONLY to "Trusted only", ReceiveMode.OFF to "Off"),
                    selected = mode,
                    onSelect = { model.setMode(it) },
                )
                Spacer(Modifier.height(24.dp))
                BasicText("Trusted devices", style = Type.label)
                Spacer(Modifier.height(4.dp))
                BasicText("These send without asking. Remove one and it has to ask again.", style = Type.soft)
                Spacer(Modifier.height(12.dp))
                if (trusted.isEmpty()) {
                    BasicText("None yet. Tick \"Always accept\" when a device asks to send.", style = Type.soft)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (device in trusted) {
                            val shape = RoundedCornerShape(14.dp)
                            Row(
                                Modifier.fillMaxWidth().clip(shape).border(1.dp, Paper.line, shape).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    BasicText(device.name, style = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    BasicText("Added ${time.format(Date(device.addedAt))}", style = Type.caption)
                                }
                                TextAction("Remove", { model.revoke(device.fingerprint) }, color = Paper.danger)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                BasicText("Received files are saved in Downloads/ProxSh.", style = Type.caption)
            }
        }
    }
}

// ------------------------------------------------------------------ share menu

/** What the Share-menu screen is doing. */
sealed interface ShareState {
    data object Choosing : ShareState
    data class Sending(val peerName: String) : ShareState
    data class Finished(val ok: Boolean, val message: String) : ShareState
}

@Composable
fun ShareScreen(model: PhoneModel, summary: String, state: ShareState, onPick: (Peer) -> Unit, onClose: () -> Unit) {
    val peers by model.node.peers.collectAsState()
    Box(Modifier.fillMaxSize().background(Paper.canvas)) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("ProxSh", style = Type.title.copy(fontSize = 24.sp), modifier = Modifier.weight(1f))
                TextAction("Close", onClose, color = Paper.inkSoft)
            }
            Spacer(Modifier.height(24.dp))
            when (state) {
                is ShareState.Choosing -> {
                    BasicText("Send to", style = Type.headline)
                    BasicText("which device?", style = Type.headlineTurn)
                    Spacer(Modifier.height(6.dp))
                    BasicText(summary, style = Type.soft)
                    Spacer(Modifier.height(22.dp))
                    if (peers.isEmpty()) {
                        EmptyNearby()
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (peer in peers) key(peer.id) { PeerRow(peer, isSelected = false, onClick = { onPick(peer) }) }
                        }
                    }
                }
                is ShareState.Sending -> {
                    BasicText("Sending to", style = Type.headline)
                    BasicText(state.peerName, style = Type.headlineTurn, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(22.dp))
                    for (t in model.live.values) { LiveRow(t); Spacer(Modifier.height(8.dp)) }
                }
                is ShareState.Finished -> {
                    BasicText(if (state.ok) "Sent" else "Not sent", style = Type.headline)
                    BasicText(if (state.ok) "all done" else "try again", style = Type.headlineTurn)
                    Spacer(Modifier.height(12.dp))
                    BasicText(state.message, style = Type.soft)
                    Spacer(Modifier.height(22.dp))
                    PaperButton("Close", onClose, Modifier.fillMaxWidth(), primary = true)
                }
            }
        }
    }
}
