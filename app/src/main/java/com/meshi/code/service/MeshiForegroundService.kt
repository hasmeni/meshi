package com.meshi.code.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.meshi.code.MeshiConfig
import com.meshi.code.MainActivity
import com.meshi.code.MeshiApplication
import com.meshi.code.repository.ChatMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class MeshiForegroundService : Service() {

    companion object {
        private const val CHANNEL_STATUS = "meshi_status"
        private const val CHANNEL_MESSAGES = "meshi_messages"
        private const val CHANNEL_BATTERY = "meshi_battery"
        private const val NOTIF_ID_ONGOING = 1
        private const val NOTIF_ID_BATTERY = 10
        private const val EXTRA_NODE_NAME = "node_name"
        private val BATTERY_POLL_INTERVAL_MS = MeshiConfig.BATTERY_POLL_INTERVAL_MS

        fun startIntent(context: Context, nodeName: String): Intent =
            Intent(context, MeshiForegroundService::class.java).apply {
                putExtra(EXTRA_NODE_NAME, nodeName)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, MeshiForegroundService::class.java)
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastKnownMessageCount = 0
    private var batteryAlertFired = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nodeName = intent?.getStringExtra(EXTRA_NODE_NAME) ?: "Meshi Node"
        startForeground(NOTIF_ID_ONGOING, buildOngoingNotification(nodeName))
        batteryAlertFired = false
        observeMessages()
        pollBattery()
        return START_STICKY
    }

    private fun observeMessages() {
        val repo = (application as MeshiApplication).repository ?: return
        scope.launch {
            var baseline = repo.messages.value.size
            repo.messages
                .map { it.size }
                .distinctUntilChanged()
                .collect { count ->
                    if (count > baseline) {
                        val msgs = repo.messages.value
                        val newMsgs = msgs.takeLast(count - baseline)
                        newMsgs.filter { !it.isSentByMe }.forEach { msg ->
                            postMessageNotification(msg)
                        }
                    }
                    baseline = count
                }
        }
    }

    private fun pollBattery() {
        val app = application as MeshiApplication
        val repo = app.repository ?: return
        val prefs = app.prefs
        scope.launch {
            while (isActive) {
                delay(BATTERY_POLL_INTERVAL_MS)
                if (!prefs.batteryAlertEnabled || batteryAlertFired) continue
                repo.fetchNodeStats()
                // Wait a moment for the response to arrive
                delay(2000)
                val stats = repo.nodeStats.value ?: continue
                if (stats.battPct in 1..prefs.batteryAlertPct) {
                    postBatteryAlert(stats.battPct)
                    batteryAlertFired = true
                } else if (stats.battPct > prefs.batteryAlertPct) {
                    // Reset alert flag once battery recovers (e.g., charging)
                    batteryAlertFired = false
                }
            }
        }
    }

    private fun postBatteryAlert(pct: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_BATTERY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Node Battery Low")
            .setContentText("Battery at $pct% — charge your node soon.")
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(NOTIF_ID_BATTERY, notif)
    }

    private fun postMessageNotification(msg: ChatMessage) {
        val prefs = (application as MeshiApplication).prefs
        if (msg.channelIdx == -1 && !prefs.notifyDms) return
        if (msg.channelIdx >= 0 && !prefs.notifyChannel) return

        // Stable per-sender/channel ID so we can cancel it from the quick-reply receiver
        val notifId = if (msg.channelIdx == -1) {
            0x0100_0000 or (msg.senderDisplayId.hashCode().and(0x00FF_FFFF))
        } else {
            0x0200_0000 or msg.channelIdx
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = PendingIntent.getActivity(
            this, notifId,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Quick-reply action via RemoteInput
        val remoteInput = RemoteInput.Builder(QuickReplyReceiver.KEY_REPLY_TEXT)
            .setLabel("Reply…")
            .build()
        val replyIntent = Intent(this, QuickReplyReceiver::class.java).apply {
            putExtra(QuickReplyReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(QuickReplyReceiver.EXTRA_CHANNEL_IDX, msg.channelIdx)
            putExtra(QuickReplyReceiver.EXTRA_SENDER_ID, msg.senderDisplayId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this, notifId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyPendingIntent,
        ).addRemoteInput(remoteInput).build()

        val from = msg.senderName.ifBlank { msg.senderDisplayId }
        val title = if (msg.channelIdx == -1) "DM from $from"
                    else "Message in ${if (msg.channelIdx == 0) "Public" else "Channel ${msg.channelIdx}"}"

        val notif = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(msg.text.take(80))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .addAction(replyAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(notifId, notif)
    }

    private fun buildOngoingNotification(nodeName: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Meshi — Connected")
            .setContentText("Linked to $nodeName")
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS, "Connection Status",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Ongoing BLE connection indicator" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MESSAGES, "Incoming Messages",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Notifications for incoming mesh messages" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BATTERY, "Battery Alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Low battery alerts for connected node" }
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
