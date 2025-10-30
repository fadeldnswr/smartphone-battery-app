package com.example.smartphonebatteryprediction.bg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import com.example.smartphonebatteryprediction.R

object NotifHelper {
    const val CHANNEL_ID = "metrics_sync"
    const val NOTIF_ID = 1001

    fun ensureChannel(ctx: Context){
        if(Build.VERSION.SDK_INT >= 26){
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, "metrics_sync", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground sync for device analytics"
                enableLights(false)
                enableVibration(false)
                lightColor = android.graphics.Color.WHITE
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }
    fun build(ctx: Context, text: String): Notification {
        ensureChannel(ctx)
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("Device Analytics")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stats_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}