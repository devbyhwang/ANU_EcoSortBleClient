package com.camellon.anu_ecosortbleclient

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class NotificationHelper(private val context: Context) {
    private val channelId = "waste-alert-channel"

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Waste Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Raspberry Pi BLE alerts"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showAlertSafe(title: String, message: String, category: String = "Unknown") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w("NotificationHelper", "POST_NOTIFICATIONS not granted")
                return
            }
        }

        // 카테고리에 맞는 이미지 정확하게 매칭 (이전 상태)
       val lowerCat = category.trim().lowercase()
        val imageResId = when {
            lowerCat.contains("plastic") || lowerCat.contains("플라스틱") -> R.drawable.plastic
            lowerCat.contains("can") || lowerCat.contains("캔") -> R.drawable.can
            lowerCat.contains("glass") || lowerCat.contains("유리") -> R.drawable.glass
            lowerCat.contains("paper") || lowerCat.contains("종이") -> R.drawable.paper
            else -> R.drawable.paper // 모르는 글자면 종이로!
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, imageResId)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(message)
            .setLargeIcon(bitmap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}