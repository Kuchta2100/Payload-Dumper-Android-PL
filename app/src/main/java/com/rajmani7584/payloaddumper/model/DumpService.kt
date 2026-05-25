package com.rajmani7584.payloaddumper.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import androidx.core.app.NotificationCompat
import com.rajmani7584.payloaddumper.MainActivity
import com.rajmani7584.payloaddumper.R

class DumpService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService() = this@DumpService
    }

    override fun onBind(intent: Intent) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Dumping partitions..."))
        return START_STICKY
    }

    fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(content: String): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Progress",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val toIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Payload Dumper")
            .setContentText(content)
            .setSmallIcon(R.drawable.outline_sync_24)
            .setOngoing(true)
            .setContentIntent(toIntent)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "payload_dump_channel"
    }
}