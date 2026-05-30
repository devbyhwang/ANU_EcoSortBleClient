package com.camellon.anu_ecosortbleclient

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class BleService : Service() {
    private lateinit var bleClient: BleClient
    private lateinit var notificationHelper: NotificationHelper

    private val handler = Handler(Looper.getMainLooper())
    private var deviceAddress: String? = null
    private var shouldReconnect = false

    override fun onCreate() {
        super.onCreate()

        notificationHelper = NotificationHelper(this)
        notificationHelper.ensureChannel()

        bleClient = BleClient(
            this,
            notificationHelper,
            onReady = {
                Log.i("BleService", "BLE 연결 완료: notify 구독 준비됨")
            },
            onDisconnected = {
                Log.w("BleService", "BLE 연결 끊김")
                scheduleReconnect()
            },
            onFailure = { reason ->
                Log.e("BleService", "BLE 실패: $reason")
                scheduleReconnect()
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "waste-alert-channel")
            .setContentTitle("EcoSort 서비스 가동 중")
            .setContentText("라즈베리 파이 신호를 대기하고 있습니다.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        startForeground(99, notification)

        val address = intent?.getStringExtra("device_address")
        if (address.isNullOrBlank()) {
            Log.e("BleService", "device_address is null/blank")
            return START_STICKY
        }

        deviceAddress = address
        shouldReconnect = true
        connectNow()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun connectNow() {
        val address = deviceAddress
        if (address.isNullOrBlank()) return

        val adapter = bleClient.getAdapter()
        if (adapter == null) {
            Log.e("BleService", "Bluetooth adapter is null")
            return
        }

        try {
            val device = adapter.getRemoteDevice(address)
            Log.i("BleService", "Connecting to device=$address")
            bleClient.connect(device)
        } catch (e: Exception) {
            Log.e("BleService", "Failed to connect: ${e.message}", e)
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            Log.i("BleService", "Reconnecting BLE...")
            connectNow()
        }, 3000L)
    }

    override fun onDestroy() {
        shouldReconnect = false
        handler.removeCallbacksAndMessages(null)
        bleClient.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}