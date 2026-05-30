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
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID

class BleClient(
    context: Context,
    private val notificationHelper: NotificationHelper,
    private val onReady: (() -> Unit)? = null,
    private val onDisconnected: (() -> Unit)? = null,
    private val onFailure: ((String) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null

    private val serviceUuid = UUID.fromString("f82d9a22-3dc9-430e-875d-583c9ced1904")
    private val notifyUuid = UUID.fromString("2c5bba85-ac1c-46c2-a8d3-db389101a028")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun getAdapter(): BluetoothAdapter? = bluetoothAdapter

    fun isBluetoothReady(): Boolean = bluetoothAdapter?.isEnabled == true

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
            fail("BLUETOOTH_CONNECT permission missing")
            return
        }

        close()

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                Log.i("BleClient", "onConnectionStateChange status=$status newState=$newState")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failAndClose(gatt, "GATT connection failed. status=$status")
                    onDisconnected?.invoke()
                    return
                }

                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        Log.i("BleClient", "Connected. Discovering services...")
                        if (!gatt.discoverServices()) {
                            failAndClose(gatt, "discoverServices() returned false")
                        }
                    }

                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.w("BleClient", "Disconnected")
                        closeGatt(gatt)
                        onDisconnected?.invoke()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.i("BleClient", "onServicesDiscovered status=$status")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failAndClose(gatt, "Service discovery failed. status=$status")
                    return
                }

                val service: BluetoothGattService = gatt.getService(serviceUuid) ?: run {
                    failAndClose(gatt, "Service UUID not found: $serviceUuid")
                    return
                }

                val characteristic: BluetoothGattCharacteristic =
                    service.getCharacteristic(notifyUuid) ?: run {
                        failAndClose(gatt, "Notify characteristic UUID not found: $notifyUuid")
                        return
                    }

                val notifySet = gatt.setCharacteristicNotification(characteristic, true)
                Log.i("BleClient", "setCharacteristicNotification=$notifySet")
                if (!notifySet) {
                    failAndClose(gatt, "setCharacteristicNotification() returned false")
                    return
                }

                val descriptor = characteristic.getDescriptor(cccdUuid) ?: run {
                    failAndClose(gatt, "CCCD descriptor not found")
                    return
                }

                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val writeRequested = gatt.writeDescriptor(descriptor)
                Log.i("BleClient", "CCCD write requested=$writeRequested")

                if (!writeRequested) {
                    failAndClose(gatt, "writeDescriptor() returned false")
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.i("BleClient", "onDescriptorWrite status=$status uuid=${descriptor.uuid}")

                if (descriptor.uuid == cccdUuid && status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("BleClient", "BLE notify subscription ready")
                    onReady?.invoke()
                } else {
                    failAndClose(gatt, "CCCD write failed. status=$status")
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid != notifyUuid) return
                val raw = characteristic.value?.toString(Charsets.UTF_8) ?: return
                handlePayload(raw)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid != notifyUuid) return
                handlePayload(value.toString(Charsets.UTF_8))
            }
        }

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, callback)
        }

        Log.i("BleClient", "connectGatt requested to ${device.address}")
    }

    private fun handlePayload(raw: String) {
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
            appContext.sendBroadcast(
                Intent(MainActivity.ACTION_TRASH_SORTED).apply {
                    putExtra(MainActivity.EXTRA_TRASH_CATEGORY, message)
                    setPackage(appContext.packageName)
                }
            )
            Log.i("BleClient", "Notification shown. event=$event")
        }.onFailure {
            Log.e("BleClient", "Invalid BLE payload: $raw", it)
        }
    }

    private fun fail(message: String) {
        Log.e("BleClient", message)
        onFailure?.invoke(message)
    }

    private fun failAndClose(targetGatt: BluetoothGatt, message: String) {
        fail(message)
        closeGatt(targetGatt)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(targetGatt: BluetoothGatt) {
        try {
            targetGatt.disconnect()
        } catch (_: Exception) {
        }

        try {
            targetGatt.close()
        } catch (_: Exception) {
        }

        if (gatt == targetGatt) {
            gatt = null
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        val current = gatt ?: return
        gatt = null

        try {
            current.disconnect()
        } catch (_: Exception) {
        }

        try {
            current.close()
        } catch (_: Exception) {
        }
    }
}
