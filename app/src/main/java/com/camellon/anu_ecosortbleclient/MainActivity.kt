package com.camellon.anu_ecosortbleclient

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var currentImageRes by mutableIntStateOf(R.drawable.standby) // 대기 상태로 시작
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var bleClient: BleClient

    private val handler = Handler(Looper.getMainLooper())
    private var pendingConnectAfterPermission = false
    private var scanning = false
    private var receiverRegistered = false

    private val trashSortedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_TRASH_SORTED) return
            currentImageRes = imageResForCategory(intent.getStringExtra(EXTRA_TRASH_CATEGORY))
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
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

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName
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
        registerTrashSortedReceiver()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = currentImageRes),
                            contentDescription = "분류 결과 이미지",
                            modifier = Modifier.size(200.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
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

    private fun registerTrashSortedReceiver() {
        val filter = IntentFilter(ACTION_TRASH_SORTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(trashSortedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(trashSortedReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        return permissions.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsThenConnect() {
        pendingConnectAfterPermission = true
        permissionLauncher.launch(requiredPermissions())
    }

    private fun startConnection() {
        if (!hasAllPermissions()) {
            requestPermissionsThenConnect()
            return
        }

        if (!bleClient.isBluetoothReady()) {
            Toast.makeText(this, "블루투스를 켜주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        startScan()
    }

    @SuppressLint("MissingPermission")
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

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false

        try {
            bleClient.getAdapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
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
        if (receiverRegistered) {
            unregisterReceiver(trashSortedReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun imageResForCategory(category: String?): Int {
        val lower = category?.trim()?.lowercase() ?: return R.drawable.paper
        return when {
            lower.contains("plastic") || lower.contains("플라스틱") -> R.drawable.plastic
            lower.contains("can") || lower.contains("캔") -> R.drawable.can
            lower.contains("glass") || lower.contains("유리") -> R.drawable.glass
            lower.contains("paper") || lower.contains("종이") -> R.drawable.paper
            else -> R.drawable.paper // 모르는 글자("Unknown" 등)면 종이로!
        }
    }

    companion object {
        const val ACTION_TRASH_SORTED = "com.camellon.ACTION_TRASH_SORTED"
        const val EXTRA_TRASH_CATEGORY = "category"
    }
}