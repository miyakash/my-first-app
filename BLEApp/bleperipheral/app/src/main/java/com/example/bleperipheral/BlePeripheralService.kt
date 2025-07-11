package com.example.bleperipheral

import android.Manifest
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.*

class BlePeripheralService : Service() {

    companion object {
        private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
        private val CHARACTERISTIC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
        private const val TAG = "BlePeripheralService"
    }

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var latestValue = "初期値"

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager?.adapter

        bluetoothGattServer = bluetoothManager?.openGattServer(this, gattCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(characteristic)
        bluetoothGattServer?.addService(service)

        log("BLE GATTサーバ起動")
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        sendBroadcast(Intent("BLE_LOG").putExtra("log", message))
    }

    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val state = if (newState == BluetoothProfile.STATE_CONNECTED) "接続" else "切断"
            log("デバイス ${device.address} が$state")
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                val data = latestValue.toByteArray(Charsets.UTF_8)
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, data)
                log("読み取り要求 → \"$latestValue\" を送信")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                latestValue = value.toString(Charsets.UTF_8)
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                log("書き込み要求 → \"$latestValue\" を受信")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        bluetoothGattServer?.close()
        log("BLE GATTサーバ停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
