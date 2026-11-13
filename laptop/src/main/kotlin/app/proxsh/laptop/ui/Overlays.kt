package app.proxsh.laptop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proxsh.core.IncomingOffer
import app.proxsh.laptop.AppModel
import app.proxsh.laptop.formatBytes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** Dims the window behind a dialog and swallows clicks so nothing underneath is touched. */
@Composable
private fun Scrim(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Paper.ink.copy(alpha = 0.38f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Someone wants to send you something. Shown over everything until you answer. */
@Composable
fun IncomingDialog(offer: IncomingOffer, model: AppModel) {
    var always by remember(offer.offerId) { mutableStateOf(false) }
    Scrim {
        PaperCard(Modifier.width(460.dp).appearSoftly()) {
            Column(Modifier.padding(30.dp)) {
                BasicText(offer.peerName, style = Type.display.copy(fontSize = 28.sp, lineHeight = 32.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText("wants to send you", style = Type.displayTurn.copy(fontSize = 28.sp, lineHeight = 32.sp))
                Spacer(Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (item in offer.items.take(5)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlyphIcon(if (item.isText) Glyph.Text else Glyph.File, Paper.inkSoft, 18.dp)
                            BasicText(
                                if (item.isText) "Text, ${formatBytes(item.size)}" else item.name,
                                style = Type.body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!item.isText) BasicText(formatBytes(item.size), style = Type.caption)
                        }
                    }
                    if (offer.items.size > 5) BasicText("and ${offer.items.size - 5} more", style = Type.caption)
                }

                Spacer(Modifier.height(22.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Paper.sunken).padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(offer.checkCode, style = Type.code)
                    Spacer(Modifier.height(2.dp))
                    BasicText("Same code on ${offer.peerName}? Then it's really them.", style = Type.caption)
                }

                Spacer(Modifier.height(18.dp))
                CheckRow(always, "Always accept from this device", { always = it })
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    PaperButton("Decline", { model.answer(false, false) })
                    PaperButton("Accept", { model.answer(true, always) }, primary = true)
                }
            }
        }
    }
}

/** Your name, where files are saved, and the devices you trust. */
@Composable
fun SettingsPanel(model: AppModel, chooseFolder: () -> File?) {
    val trusted by model.node.trustStore.devices.collectAsState()
    var name by remember { mutableStateOf(model.settings.deviceName) }
    val time = remember { SimpleDateFormat("d MMM yyyy") }

    Scrim {
        PaperCard(Modifier.width(520.dp).fillMaxHeight(0.86f).appearSoftly()) {
            Column(Modifier.padding(30.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText("Your", style = Type.display.copy(fontSize = 28.sp, lineHeight = 32.sp))
                        BasicText("settings", style = Type.displayTurn.copy(fontSize = 28.sp, lineHeight = 32.sp))
                    }
                    PaperButton("Done", { model.showSettings = false }, primary = true)
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(24.dp))
                    BasicText("Name other devices see", style = Type.label)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field(name, { name = it.take(40) }, "My laptop", Modifier.weight(1f))
                        PaperButton("Save", { model.setDeviceName(name) }, enabled = name.isNotBlank() && name.trim() != model.settings.deviceName)
                    }
                    if (name.trim() != model.settings.deviceName) Unit else if (model.settings.deviceName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        BasicText("A new name shows up the next time ProxSh starts.", style = Type.caption)
                    }

                    Spacer(Modifier.height(24.dp))
                    BasicText("Files are saved to", style = Type.label)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicText(model.settings.receiveDir, style = Type.soft, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        PaperButton("Change", { chooseFolder()?.let { model.setReceiveDir(it) } })
                    }
                    Spacer(Modifier.height(4.dp))
                    BasicText("A new folder is used the next time ProxSh starts.", style = Type.caption)

                    Spacer(Modifier.height(28.dp))
                    BasicText("Trusted devices", style = Type.label)
                    Spacer(Modifier.height(4.dp))
                    BasicText("These send without asking. Remove one and it has to ask again.", style = Type.soft)
                    Spacer(Modifier.height(12.dp))
                    if (trusted.isEmpty()) {
                        BasicText("None yet. Tick \"Always accept\" when a device asks to send.", style = Type.soft)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (device in trusted) {
                                val shape = RoundedCornerShape(12.dp)
                                Row(
                                    Modifier.fillMaxWidth().clip(shape).border(1.dp, Paper.line, shape).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
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
                }
            }
        }
    }
}
