package app.proxsh.laptop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberNotification
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import app.proxsh.core.NodeConfig
import app.proxsh.core.ProxshNode
import app.proxsh.laptop.ui.HomeScreen
import app.proxsh.laptop.ui.Paper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.BindException
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main() {
    val dataDir = SettingsStore.dataDir()
    val settings = SettingsStore.load(dataDir)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val node = ProxshNode(
        NodeConfig(dataDir, File(settings.receiveDir), settings.deviceName, "laptop"),
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
    try {
        node.start()
    } catch (e: BindException) {
        JOptionPane.showMessageDialog(null, "ProxSh is already running on this computer.", "ProxSh", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }

    application {
        var visible by remember { mutableStateOf(true) }
        var attention by remember { mutableStateOf<Pair<String, String>?>(null) }
        val trayState = rememberTrayState()
        val windowState = rememberWindowState(size = DpSize(1080.dp, 740.dp))

        val model = remember {
            AppModel(node, scope, settings, { SettingsStore.save(dataDir, it) }) { title, message ->
                attention = title to message
            }
        }

        // A request arrived: bring the window up so it can be answered, and say so in the tray.
        LaunchedEffect(attention) {
            attention?.let { (title, message) ->
                visible = true
                trayState.sendNotification(Notification(title, message))
                attention = null
            }
        }

        Tray(
            icon = TrayIcon,
            state = trayState,
            tooltip = "ProxSh · visible as ${model.settings.deviceName}",
            onAction = { visible = true },
        ) {
            Item("Open ProxSh", onClick = { visible = true })
            Item("Quit", onClick = {
                node.close()
                exitApplication()
            })
        }

        Window(
            onCloseRequest = { visible = false }, // closing the window keeps ProxSh listening in the tray
            visible = visible,
            state = windowState,
            title = "ProxSh",
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(900, 620) }
            HomeScreen(
                model = model,
                pickFiles = {
                    val dialog = FileDialog(null as Frame?, "Choose files to send", FileDialog.LOAD)
                    dialog.isMultipleMode = true
                    dialog.isVisible = true
                    dialog.files.toList()
                },
                chooseFolder = {
                    val chooser = JFileChooser(model.settings.receiveDir).apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        dialogTitle = "Where should received files go?"
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                },
            )
        }
    }
}

/** A small teal disc with a white arrow, drawn in code so there is no image file to ship. */
private object TrayIcon : Painter() {
    override val intrinsicSize = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        drawCircle(Paper.accent, radius = size.minDimension / 2f)
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        drawLine(Paper.onAccent, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.72f, h * 0.5f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Paper.onAccent, Offset(w * 0.54f, h * 0.32f), Offset(w * 0.72f, h * 0.5f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Paper.onAccent, Offset(w * 0.54f, h * 0.68f), Offset(w * 0.72f, h * 0.5f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
