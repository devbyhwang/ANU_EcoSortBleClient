package com.camellon.anu_ecosortbleclient

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var bleClient: BleClient

    private var currentImageRes by mutableIntStateOf(R.drawable.standby_img)
    private var statusText by mutableStateOf("쓰레기를 투입해 주세요")

    private val bleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.camellon.ACTION_TRASH_SORTED") {
                val category = intent.getStringExtra("category") ?: "Unknown"
                
                when (category) {
                    "Plastic" -> {
                        currentImageRes = R.drawable.plastic_img
                        statusText = "플라스틱 분류 완료!"
                    }
                    "Can" -> {
                        currentImageRes = R.drawable.can_img
                        statusText = "캔 분류 완료!"
                    }
                    "Glass" -> {
                        currentImageRes = R.drawable.glass_img
                        statusText = "유리 분류 완료!"
                    }
                    "Paper" -> {
                        currentImageRes = R.drawable.paper_img
                        statusText = "종이 분류 완료!"
                    }
                    else -> {
                        statusText = "알 수 없는 쓰레기입니다."
                    }
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            Toast.makeText(this, "권한 허용됨", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationHelper = NotificationHelper(this)
        notificationHelper.ensureChannel()
        bleClient = BleClient(this, notificationHelper)

        checkPermissions()

        val filter = IntentFilter("com.camellon.ACTION_TRASH_SORTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bleReceiver, filter)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("EcoSort 모니터", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(30.dp))
                        
                        Image(
                            painter = painterResource(id = currentImageRes),
                            contentDescription = "분류 결과 이미지",
                            modifier = Modifier.size(200.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(statusText, style = MaterialTheme.typography.titleLarge)
                        
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(onClick = { startConnection() }) {
                            Text("라즈베리 파이 연결하기")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bleReceiver)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startConnection() {
        if (!bleClient.isBluetoothReady()) {
            Toast.makeText(this, "블루투스를 켜주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val macAddress = "88:A2:9E:64:AD:6E"
        val intent = Intent(this, BleService::class.java).apply {
            putExtra("device_address", macAddress)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "백그라운드 감시 서비스 시작", Toast.LENGTH_SHORT).show()
    }
}