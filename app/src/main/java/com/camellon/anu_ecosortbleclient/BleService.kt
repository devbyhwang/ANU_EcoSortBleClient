package com.camellon.anu_ecosortbleclient

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BleService : Service() {
    private lateinit var bleClient: BleClient
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.ensureChannel()
        bleClient = BleClient(this, notificationHelper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "waste-alert-channel")
            .setContentTitle("EcoSort 서비스 가동 중")
            .setContentText("라즈베리 파이 신호를 대기하고 있습니다.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        startForeground(99, notification)

        val deviceAddress = intent?.getStringExtra("device_address")
        if (deviceAddress.isNullOrBlank()) {
            Log.e("BleService", "device_address is null/blank")
            return START_STICKY
        }

        try {
            val adapter = bleClient.getAdapter()
            if (adapter == null) {
                Log.e("BleService", "Bluetooth adapter is null")
                return START_STICKY
            }

            val device = adapter.getRemoteDevice(deviceAddress)
            Log.i("BleService", "Connecting to device=$deviceAddress")
            bleClient.connect(device)
        } catch (e: Exception) {
            Log.e("BleService", "Failed to connect: ${e.message}", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        bleClient.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}