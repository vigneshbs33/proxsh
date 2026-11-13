package app.proxsh.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import app.proxsh.android.ui.ShareScreen
import app.proxsh.android.ui.ShareState
import kotlinx.coroutines.delay

/**
 * What opens when you pick ProxSh in another app's Share menu, WhatsApp included.
 * Pick a nearby device and the files go straight there.
 */
class ShareActivity : ComponentActivity() {
    private val app get() = application as ProxshApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProxshService.start(this)

        val uris = sharedUris(intent)
        val text = if (uris.isEmpty()) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val summary = when {
            uris.size > 1 -> "${uris.size} files"
            uris.size == 1 -> "1 file"
            !text.isNullOrBlank() -> "Text"
            else -> "Nothing to send"
        }

        setContent {
            var state by remember { mutableStateOf<ShareState>(ShareState.Choosing) }
            // Close by itself a moment after a successful send.
            LaunchedEffect(state) {
                val s = state
                if (s is ShareState.Finished && s.ok) {
                    delay(1200)
                    finish()
                }
            }
            ShareScreen(
                model = app.model,
                summary = summary,
                state = state,
                onPick = { peer ->
                    if (uris.isEmpty() && text.isNullOrBlank()) {
                        state = ShareState.Finished(false, "There was nothing to send.")
                    } else {
                        state = ShareState.Sending(peer.name)
                        app.model.sendUris(peer, uris, text) { result ->
                            state = ShareState.Finished(result.ok, result.message)
                        }
                    }
                },
                onClose = { finish() },
            )
        }
    }

    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
}
