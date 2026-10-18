package com.meshi.code.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.meshi.code.MeshiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles inline quick-reply actions from message notifications.
 * Routes the reply text back through [MeshRepository] without opening the app.
 */
class QuickReplyReceiver : BroadcastReceiver() {

    companion object {
        const val KEY_REPLY_TEXT    = "quick_reply_text"
        const val EXTRA_NOTIF_ID   = "notif_id"
        const val EXTRA_CHANNEL_IDX = "channel_idx"
        const val EXTRA_SENDER_ID  = "sender_display_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = bundle.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return

        val repo = (context.applicationContext as MeshiApplication).repository ?: return
        val channelIdx    = intent.getIntExtra(EXTRA_CHANNEL_IDX, 0)
        val senderDisplayId = intent.getStringExtra(EXTRA_SENDER_ID) ?: return
        val notifId       = intent.getIntExtra(EXTRA_NOTIF_ID, 0)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (channelIdx == -1) {
                    val contact = repo.contacts.value.find { it.displayId == senderDisplayId }
                        ?: return@launch
                    repo.sendDirectMessage(contact, replyText)
                } else {
                    repo.sendChannelMessage(replyText, channelIdx)
                }
                // Dismiss the notification once reply is sent
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(notifId)
            } finally {
                pending.finish()
            }
        }
    }
}
