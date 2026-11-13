package app.proxsh.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.proxsh.android.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val app get() = application as ProxshApp

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) app.model.selectedPeer()?.let { app.model.sendUris(it, uris) }
    }

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProxshService.start(this)
        requestPermissionsOnce()
        setContent {
            HomeScreen(app.model, app.deviceName, onPickFiles = { pickFiles.launch(arrayOf("*/*")) })
        }
    }

    /** Notifications (to show requests) and, on the newest Android, the local-network permission. */
    private fun requestPermissionsOnce() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
        }
        val missing = wanted.firstOrNull { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing != null) askPermission.launch(missing)
    }
}
