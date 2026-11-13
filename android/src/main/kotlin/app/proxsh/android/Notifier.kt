package app.proxsh.android

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.proxsh.core.IncomingOffer

/** Everything ProxSh says outside its own window: the ready notice, requests, and received files. */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_READY, "Ready to receive", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shows that ProxSh is running so other devices can find this phone."
                setShowBadge(false)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_REQUESTS, "Requests to send", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A nearby device wants to send you something."
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_RECEIVED, "Received", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Files and text that arrived."
            },
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun ready(deviceName: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_READY)
            .setSmallIcon(R.drawable.ic_stat_proxsh)
            .setContentTitle("ProxSh is ready")
            .setContentText("Visible as $deviceName")
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .build()

    @SuppressLint("MissingPermission")
    fun offer(offer: IncomingOffer) {
        if (!manager.areNotificationsEnabled()) return
        val first = offer.items.first()
        val more = if (offer.items.size > 1) " and ${offer.items.size - 1} more" else ""
        val what = if (first.isText) "Text$more" else "${first.name}$more"
        val builder = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(R.drawable.ic_stat_proxsh)
            .setContentTitle("${offer.peerName} wants to send you something")
            .setContentText("$what · code ${offer.checkCode}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$what\nCheck that ${offer.peerName} shows the code ${offer.checkCode} too."))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setTimeoutAfter(110_000)
            .addAction(0, "Decline", NotificationActions.answer(context, offer.offerId, accept = false, always = false))
            .addAction(0, "Accept", NotificationActions.answer(context, offer.offerId, accept = true, always = false))
            .addAction(0, "Always accept", NotificationActions.answer(context, offer.offerId, accept = true, always = true))
        manager.notify(offer.offerId.hashCode(), builder.build())
    }

    fun cancelOffer(offerId: String) = manager.cancel(offerId.hashCode())

    @SuppressLint("MissingPermission")
    fun receivedFile(id: String, name: String, size: String, from: String, uri: Uri?) {
        if (!manager.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_RECEIVED)
            .setSmallIcon(R.drawable.ic_stat_proxsh)
            .setContentTitle("Received $name")
            .setContentText("$size from $from")
            .setAutoCancel(true)
        if (uri != null) {
            val mime = Storage.mimeOf(name)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            builder.setContentIntent(PendingIntent.getActivity(context, id.hashCode(), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            // One tap to pass a file from the laptop on to a WhatsApp chat.
            val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.addAction(0, "Share", PendingIntent.getActivity(context, id.hashCode() + 1, chooser, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        manager.notify(id.hashCode(), builder.build())
    }

    @SuppressLint("MissingPermission")
    fun receivedText(id: String, text: String, from: String) {
        if (!manager.areNotificationsEnabled()) return
        manager.notify(
            id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_RECEIVED)
                .setSmallIcon(R.drawable.ic_stat_proxsh)
                .setContentTitle("Text from $from")
                .setContentText(text.lineSequence().first().take(120))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(600) + "\n\nCopied to your clipboard."))
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_READY = "ready"
        const val CHANNEL_REQUESTS = "requests"
        const val CHANNEL_RECEIVED = "received"
        const val READY_ID = 1
    }
}
