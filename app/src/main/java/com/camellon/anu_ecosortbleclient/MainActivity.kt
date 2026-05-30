package com.camellon.anu_ecosortbleclient

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // 실시간으로 변하는 상태 변수 (그림과 텍스트)
    private var currentImageRes by mutableIntStateOf(R.drawable.paper)
    private var statusText by mutableStateOf("쓰레기를 투입해 주세요")
    private var receiverRegistered = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            startBleService()
        } else {
            Toast.makeText(this, "BLE 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    // 라즈베리파이 신호를 엿듣는 수신기
    private val bleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.camellon.ACTION_TRASH_SORTED") {
                val category = intent.getStringExtra("category") ?: "Unknown"

                // 라디오 방송 내용에 따라 그림과 글씨 즉시 교체
                when (category) {
                    "Plastic" -> {
                        currentImageRes = R.drawable.plastic
                        statusText = "플라스틱 분류 완료!"
                    }
                    "Can" -> {
                        currentImageRes = R.drawable.can
                        statusText = "캔 분류 완료!"
                    }
                    "Glass" -> {
                        currentImageRes = R.drawable.glass
                        statusText = "유리 분류 완료!"
                    }
                    "Paper", "Unknown" -> {
                        currentImageRes = R.drawable.paper
                        statusText = "종이/일반 쓰레기 분류 완료!"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱이 켜질 때 수신기(Receiver) 등록
        val filter = IntentFilter("com.camellon.ACTION_TRASH_SORTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bleReceiver, filter)
        }
        receiverRegistered = true

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        if (hasAllPermissions()) {
            startBleService()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
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

    private fun startBleService() {
        val intent = Intent(this, BleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // 앱이 꺼질 때 메모리 누수를 막기 위해 수신기 해제
    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            unregisterReceiver(bleReceiver)
            receiverRegistered = false
        }
    }
}
