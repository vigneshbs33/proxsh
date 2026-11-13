package app.proxsh.android

import android.app.Application
import android.provider.Settings
import android.os.Build
import app.proxsh.core.NodeConfig
import app.proxsh.core.ProxshNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Holds the one ProxSh node for the whole app. The screens, the Share menu and the background
 * service all talk to this same node, so a transfer keeps going when a screen closes.
 */
class ProxshApp : Application() {
    lateinit var node: ProxshNode
        private set
    lateinit var model: PhoneModel
        private set
    lateinit var notifier: Notifier
        private set

    val deviceName: String by lazy {
        runCatching { Settings.Global.getString(contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
    }

    private var started = false

    override fun onCreate() {
        super.onCreate()
        notifier = Notifier(this)
        node = ProxshNode(
            NodeConfig(
                dataDir = File(filesDir, "data"),
                // Files land here first, then move to Downloads/ProxSh once complete.
                receiveDir = File(filesDir, "incoming"),
                deviceName = deviceName,
                deviceKind = "phone",
            ),
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        model = PhoneModel(this, node, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), notifier)
    }

    @Synchronized
    fun startNode() {
        if (started) return
        node.start()
        started = true
    }
}
