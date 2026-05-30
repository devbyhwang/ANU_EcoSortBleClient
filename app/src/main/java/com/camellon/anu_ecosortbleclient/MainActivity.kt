package com.camellon.anu_ecosortbleclient

import android.Manifest
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var bleClient: BleClient

    private val handler = Handler(Looper.getMainLooper())
    private var pendingConnectAfterPermission = false
    private var scanning = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val bluetoothGranted = requiredBluetoothPermissions().all {
            result[it] == true || hasPermission(it)
        }

        if (bluetoothGranted) {
            Toast.makeText(this, "권한 허용됨", Toast.LENGTH_SHORT).show()
            if (pendingConnectAfterPermission) {
                pendingConnectAfterPermission = false
                startConnection()
            }
        } else {
            pendingConnectAfterPermission = false
            Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = getScanResultName(result)
            if (name == "RaspberryPi_BLE") {
                stopScan()

                val address = result.device.address
                Toast.makeText(
                    this@MainActivity,
                    "라즈베리 파이 발견: $address",
                    Toast.LENGTH_SHORT
                ).show()

                startBleService(address)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            Toast.makeText(
                this@MainActivity,
                "BLE 스캔 실패: $errorCode",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationHelper = NotificationHelper(this)
        notificationHelper.ensureChannel()
        bleClient = BleClient(this, notificationHelper)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("EcoSort BLE Client", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { startConnection() }) {
                            Text("라즈베리 파이 연결하기")
                        }
                    }
                }
            }
        }
    }

    private fun requiredBluetoothPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        return permissions.toTypedArray()
    }

    private fun permissionsToRequest(): Array<String> {
        val permissions = requiredBluetoothPermissions().toMutableList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        return permissions.distinct().filterNot(::hasPermission).toTypedArray()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermissions(): Boolean {
        return requiredBluetoothPermissions().all(::hasPermission)
    }

    private fun requestPermissionsThenConnect() {
        pendingConnectAfterPermission = true
        permissionLauncher.launch(permissionsToRequest())
    }

    private fun startConnection() {
        if (!hasBluetoothPermissions()) {
            requestPermissionsThenConnect()
            return
        }

        if (!bleClient.isBluetoothReady()) {
            Toast.makeText(this, "블루투스를 켜주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        startScan()
    }

    private fun startScan() {
        if (scanning) return

        val scanner = bleClient.getAdapter()?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "BLE 스캐너를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
            return
        }

        scanning = true
        Toast.makeText(this, "라즈베리 파이 검색 중...", Toast.LENGTH_SHORT).show()

        try {
            scanner.startScan(scanCallback)
        } catch (e: SecurityException) {
            scanning = false
            Toast.makeText(this, "BLE 권한이 없습니다.", Toast.LENGTH_LONG).show()
            return
        }

        handler.postDelayed({
            if (scanning) {
                stopScan()
                Toast.makeText(
                    this,
                    "RaspberryPi_BLE를 찾지 못했습니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, 10000L)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false

        try {
            bleClient.getAdapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
    }

    private fun getScanResultName(result: ScanResult): String? {
        val advertisedName = result.scanRecord?.deviceName
        if (!advertisedName.isNullOrBlank()) return advertisedName

        return try {
            result.device.name
        } catch (e: SecurityException) {
            Log.w("MainActivity", "Cannot read Bluetooth device name without permission", e)
            null
        }
    }

    private fun startBleService(deviceAddress: String) {
        val intent = Intent(this, BleService::class.java).apply {
            putExtra("device_address", deviceAddress)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(this, "BLE 감시 서비스 시작", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        stopScan()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
