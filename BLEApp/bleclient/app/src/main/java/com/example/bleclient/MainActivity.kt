package com.example.bleclient

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = "BleClient"
        private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
        private val CHARACTERISTIC_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var textReadValue: TextView
    private lateinit var textWriteValue: TextView

    private val writeData = "HelloServer"

    private val handler = Handler(Looper.getMainLooper())

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d(TAG, "Discovered device: ${device.address} - ${device.name}")
            if (bluetoothGatt == null) {
                // 停止して接続開始
                bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                bluetoothGatt = device.connectGatt(this@MainActivity, false, gattCallback)
                Log.d(TAG, "Connecting to device: ${device.address}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server. Discovering services...")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server.")
                    bluetoothGatt = null
                }
            } else {
                Log.e(TAG, "Connection state change error: $status")
                bluetoothGatt = null
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    // 読み取り開始
                    val readSuccess = gatt.readCharacteristic(characteristic)
                    Log.d(TAG, "Reading characteristic: success=$readSuccess")
                } else {
                    Log.e(TAG, "Characteristic not found!")
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == CHARACTERISTIC_UUID) {
                val value = characteristic.value.toString(Charsets.UTF_8)
                Log.d(TAG, "Characteristic read value: $value")
                handler.post {
                    textReadValue.text = "読み取り値: $value"
                }

                // 読み取り完了後に書き込み開始
                characteristic.value = writeData.toByteArray()
                val writeSuccess = gatt.writeCharacteristic(characteristic)
                Log.d(TAG, "Writing characteristic: success=$writeSuccess")
                handler.post {
                    textWriteValue.text = "書き込み値: $writeData"
                }
            } else {
                Log.e(TAG, "Characteristic read failed with status: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write succeeded")
            } else {
                Log.e(TAG, "Characteristic write failed with status: $status")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textReadValue = findViewById(R.id.textReadValue)
        textWriteValue = findViewById(R.id.textWriteValue)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "BLE not supported")
            finish()
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled")
            finish()
            return
        }

        startScan()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        Log.d(TAG, "Starting BLE scan...")
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(scanFilter), settings, scanCallback)

        // スキャンを30秒後に停止（任意）
        handler.postDelayed({
            scanner.stopScan(scanCallback)
            Log.d(TAG, "Scan stopped by timeout")
        }, 30000)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        bluetoothGatt = null
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }
}
