package app.proxsh.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps ProxSh alive in the background so other devices can find this phone and send to it.
 * Android requires the small "ProxSh is ready" notification while it runs.
 */
class ProxshService : Service() {
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProxshApp
        ServiceCompat.startForeground(
            this,
            Notifier.READY_ID,
            app.notifier.ready(app.deviceName),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        // Without this lock Android drops the multicast announcements other devices send.
        if (multicastLock == null) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("proxsh").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        app.startNode()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { multicastLock?.release() }
        multicastLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ProxshService::class.java))
        }
    }
}
