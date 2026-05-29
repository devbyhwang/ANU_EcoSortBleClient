package com.camellon.anu_ecosortbleclient

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID

class BleClient(
    context: Context,
    private val notificationHelper: NotificationHelper
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null

    private val serviceUuid = UUID.fromString("f82d9a22-3dc9-430e-875d-583c9ced1904")
    private val notifyUuid = UUID.fromString("2c5bba85-ac1c-46c2-a8d3-db389101a028")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!hasConnectPermission()) {
            Log.e("BleClient", "BLUETOOTH_CONNECT permission missing")
            return
        }

        close()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.i("BleClient", "onConnectionStateChange status=$status newState=$newState")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("BleClient", "GATT connect failed, status=$status")
                    gatt.close()
                    return
                }

                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.i("BleClient", "Connected. Discovering services...")
                    gatt.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.w("BleClient", "Disconnected")
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.i("BleClient", "onServicesDiscovered status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("BleClient", "Service discovery failed: $status")
                    return
                }

                val service: BluetoothGattService = gatt.getService(serviceUuid) ?: run {
                    Log.e("BleClient", "Service UUID not found: $serviceUuid")
                    return
                }

                val characteristic: BluetoothGattCharacteristic =
                    service.getCharacteristic(notifyUuid) ?: run {
                        Log.e("BleClient", "Notify characteristic UUID not found: $notifyUuid")
                        return
                    }

                val setOk = gatt.setCharacteristicNotification(characteristic, true)
                Log.i("BleClient", "setCharacteristicNotification=$setOk")

                val descriptor: BluetoothGattDescriptor? = characteristic.getDescriptor(cccdUuid)
                if (descriptor == null) {
                    Log.e("BleClient", "CCCD descriptor not found")
                    return
                }

                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val writeOk = gatt.writeDescriptor(descriptor)
                Log.i("BleClient", "CCCD write requested=$writeOk")
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.i("BleClient", "onDescriptorWrite status=$status uuid=${descriptor.uuid}")
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid != notifyUuid) return

                val raw = characteristic.value?.toString(Charsets.UTF_8) ?: return
                Log.i("BleClient", "raw notify payload=$raw")

                runCatching {
                    val json = JSONObject(raw)
                    val event = json.optString("event", "UNKNOWN")
                    val message = json.optString("message", "No message")
                    val title = when (event) {
                        "BIN_FULL" -> "분류함 가득 참"
                        "OUTPUT_EXCEPTION" -> "출력 장치 예외"
                        else -> "EcoSort 알림"
                    }
                    notificationHelper.showAlertSafe(title, message)
                    Log.i("BleClient", "Notification shown. event=$event")
                }.onFailure {
                    Log.e("BleClient", "Invalid BLE payload: $raw", it)
                }
            }
        }

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, callback)
        }

        Log.i("BleClient", "connectGatt requested to ${device.address}")
    }

    fun isBluetoothReady(): Boolean = bluetoothAdapter?.isEnabled == true

    fun getAdapter(): BluetoothAdapter? = bluetoothAdapter

    @SuppressLint("MissingPermission")
    fun close() {
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }
}