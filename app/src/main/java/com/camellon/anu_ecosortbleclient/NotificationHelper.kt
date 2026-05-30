package com.camellon.anu_ecosortbleclient

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {
    private val channelId = "smart_bin_channel"
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Smart Bin Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications from Smart Bin"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showAlertSafe(title: String, message: String, category: String = "Unknown") {
        // 알림 권한 체크 (안드로이드 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // 카테고리에 맞춰 소문자 이미지 매핑 (Unknown도 paper로 처리)
        val imageResId = when (category) {
            "Plastic" -> R.drawable.plastic
            "Can" -> R.drawable.can
            "Glass" -> R.drawable.glass
            "Paper", "Unknown" -> R.drawable.paper
            else -> R.drawable.standby
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, imageResId)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more) // 기본 제공 아이콘 사용
            .setContentTitle(title)
            .setContentText(message)
            .setLargeIcon(bitmap) // 알림창 우측에 썸네일 이미지 표시
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}