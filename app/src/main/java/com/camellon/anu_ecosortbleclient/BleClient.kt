package com.camellon.anu_ecosortbleclient

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.UUID

@SuppressLint("MissingPermission") // 권한 체크는 UI 쪽에서 끝났다고 가정
class BleClient(private val appContext: Context) {

    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // 알림창 이미지를 띄워줄 헬퍼 객체 생성
    private val notificationHelper = NotificationHelper(appContext)

    // ⭐️ 주의: 아래 이름과 UUID는 라즈베리파이 쪽 코드에 설정된 값과 똑같이 맞춰주세요!
    private val TARGET_DEVICE_NAME = "SmartBin"
    private val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    // 1. 주변의 블루투스 기기를 찾는 스캐너 콜백
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown"

            // 우리가 찾는 스마트 휴지통을 발견했다면!
            if (deviceName == TARGET_DEVICE_NAME) {
                Log.d("BleClient", "타겟 기기 발견! 연결을 시도합니다.")
                stopScan() // 스캔을 멈추고
                connectToDevice(device) // 연결 시작
            }
        }
    }

    // 블루투스 스캔 시작 함수
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("BleClient", "블루투스가 켜져있지 않습니다.")
            return
        }
        if (isScanning) return

        isScanning = true
        bleScanner?.startScan(scanCallback)
        Log.d("BleClient", "BLE 스캔 시작됨")

        // 배터리를 아끼기 위해 10초 동안 못 찾으면 스캔 강제 종료
        handler.postDelayed({
            stopScan()
        }, 10000)
    }

    // 블루투스 스캔 종료 함수
    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        bleScanner?.stopScan(scanCallback)
        Log.d("BleClient", "BLE 스캔 종료됨")
    }

    // 기기에 연결 요청
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(appContext, false, gattCallback)
    }

    // 2. 블루투스 통신 상태를 관리하는 메인 콜백
    private val gattCallback = object : BluetoothGattCallback() {

        // 연결 상태가 바뀌었을 때 (연결됨 / 끊어짐)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BleClient", "GATT 서버에 성공적으로 연결되었습니다.")
                // 연결되면 기기가 제공하는 서비스(UUID) 목록을 탐색합니다.
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BleClient", "GATT 서버와 연결이 끊어졌습니다.")
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        // 서비스 탐색이 끝났을 때 (통신할 준비가 되었을 때)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHAR_UUID)

                if (characteristic != null) {
                    // 라즈베리파이가 보내는 신호를 앱이 실시간으로 엿듣도록(구독) 설정
                    gatt.setCharacteristicNotification(characteristic, true)

                    val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                    Log.d("BleClient", "실시간 알림(Notification)이 활성화되었습니다.")
                }
            }
        }

        // ⭐️ [가장 핵심] 라즈베리파이에서 새로운 데이터를 보내왔을 때 실행되는 곳!
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // 받아온 데이터를 글자(String)로 바꿉니다.
            val raw = characteristic.getStringValue(0)
            Log.d("BleClient", "수신된 데이터: $raw")

            runCatching {
                // 데이터를 JSON 형식으로 쪼개서 읽습니다.
                val json = JSONObject(raw)
                val event = json.optString("event", "UNKNOWN")
                val message = json.optString("message", "No message")

                val title = if (event == "BIN_FULL") "휴지통 가득 참!" else "스마트 휴지통 알림"

                // 1. 알림창에 이미지와 함께 띄우기 (NotificationHelper 호출)
                notificationHelper.showAlertSafe(title, message, message)

                // 2. 메인 화면의 이미지를 바꾸기 위해 앱 내부 방송(Broadcast) 쏘기
                val intent = Intent("com.camellon.ACTION_TRASH_SORTED").apply {
                    putExtra("category", message) // "Plastic", "Can" 등을 실어 보냄
                    setPackage(appContext.packageName)
                }
                appContext.sendBroadcast(intent)

            }.onFailure {
                Log.e("BleClient", "JSON 파싱 에러 발생", it)
                notificationHelper.showAlertSafe("알림", raw)
            }
        }
    }

    // 외부에서 연결을 수동으로 끊고 싶을 때 사용하는 함수
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }
}