package com.camellon.anu_ecosortbleclient

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BleService : Service() {
    private lateinit var bleClient: BleClient

    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this)
        bleClient = BleClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("EcoSort 서비스 가동 중")
            .setContentText("라즈베리 파이 신호를 대기하고 있습니다.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        startForeground(99, notification)

        Log.i("BleService", "Starting SmartBin scan")
        bleClient.startScan()
        return START_STICKY
    }

    override fun onDestroy() {
        bleClient.stopScan()
        bleClient.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
