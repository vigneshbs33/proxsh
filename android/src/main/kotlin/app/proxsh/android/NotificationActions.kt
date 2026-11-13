package app.proxsh.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The Accept and Decline buttons on a request notification. */
class NotificationActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val offerId = intent.getStringExtra(EXTRA_OFFER) ?: return
        val accept = intent.getBooleanExtra(EXTRA_ACCEPT, false)
        val always = intent.getBooleanExtra(EXTRA_ALWAYS, false)
        (context.applicationContext as ProxshApp).model.answerById(offerId, accept, always)
    }

    companion object {
        private const val EXTRA_OFFER = "offer"
        private const val EXTRA_ACCEPT = "accept"
        private const val EXTRA_ALWAYS = "always"

        fun answer(context: Context, offerId: String, accept: Boolean, always: Boolean): PendingIntent {
            val intent = Intent(context, NotificationActions::class.java)
                .putExtra(EXTRA_OFFER, offerId)
                .putExtra(EXTRA_ACCEPT, accept)
                .putExtra(EXTRA_ALWAYS, always)
            val code = (offerId + accept + always).hashCode()
            return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
