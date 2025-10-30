package com.example.bleapp

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

/**
 * BLEModule
 *
 * - BluetoothGatt 等の OS リソースは内部で管理（UI に渡さない）
 * - Service / Characteristic UUID は外部で管理（UI/VM/Config 側で保持）
 * - スキャン / 接続 / サービス探索 / 書き込み / 通知 を提供
 * - 異常時は必ず listener に通知し、UI がハングしないようにする
 */
class BLEModule(private val context: Context) {

    private val TAG = "BLEModule"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null

    private var isScanning = false
    private var isConnecting = false

    // 書き込みタイムアウト管理用マップ
    private val WRITE_TIMEOUT_MS = 5000L
    private val writeTimeoutMap = mutableMapOf<UUID, Runnable>()

    // --------------------------------------------------
    // Listener: UI / ViewModel に通知するイベント（OSオブジェクトは渡さない）
    // --------------------------------------------------
    interface Listener {
        fun onScanResult(deviceName: String?, deviceAddress: String)
        fun onScanFailed(errorCode: Int)
        fun onScanTimeout()

        fun onConnecting(deviceAddress: String)
        fun onConnected(deviceAddress: String)
        fun onDisconnected(deviceAddress: String)
        fun onConnectionFailed(deviceAddress: String?, reason: String)

        fun onServicesDiscovered(deviceAddress: String, services: List<UUID>)

        fun onCharacteristicChanged(characteristicUuid: UUID, value: ByteArray)
        fun onWriteSuccess(characteristicUuid: UUID)
        fun onWriteFailed(characteristicUuid: UUID?, reason: String)
    }

    var listener: Listener? = null

    // ==============================================================
    // Scan
    // ==============================================================
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            listener?.onScanResult(result.device.name, result.device.address)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { listener?.onScanResult(it.device.name, it.device.address) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.e(TAG, "scan failed: $errorCode")
            listener?.onScanFailed(errorCode)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = 10_000L) {
        if (isScanning) return

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            listener?.onScanFailed(-1)
            return
        }

        isScanning = true
        scanner?.startScan(scanCallback)
        Log.d(TAG, "scan start")

        // タイマーでスキャンタイムアウトを管理
        mainHandler.postDelayed({
            if (isScanning) {
                stopScan()
                listener?.onScanTimeout()
            }
        }, timeoutMs)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        scanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "scan stop")
    }

    // ==============================================================
    // Connect / Disconnect / Close
    // ==============================================================
    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String) {
        if (isConnecting) return

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            listener?.onConnectionFailed(deviceAddress, "Bluetooth adapter disabled")
            return
        }

        val device = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            listener?.onConnectionFailed(null, "Invalid device address")
            return
        }

        isConnecting = true
        listener?.onConnecting(device.address)

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            isConnecting = false
            Log.e(TAG, "connectGatt exception: ${e.message}")
            listener?.onConnectionFailed(device.address, "connectGatt exception: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        // 書き込みタイマーも解除
        writeTimeoutMap.values.forEach { mainHandler.removeCallbacks(it) }
        writeTimeoutMap.clear()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnecting = false
        writeTimeoutMap.values.forEach { mainHandler.removeCallbacks(it) }
        writeTimeoutMap.clear()
    }

    // ==============================================================
    // discoverServices（UI側が明示的に呼ぶ）
    // ==============================================================
    @SuppressLint("MissingPermission")
    fun discoverServices() {
        val g = bluetoothGatt
        if (g == null) {
            listener?.onConnectionFailed(null, "GATT not connected")
            return
        }
        val started = g.discoverServices()
        if (!started) {
            listener?.onConnectionFailed(g.device.address, "discoverServices returned false")
        } else {
            Log.d(TAG, "discoverServices started")
        }
    }

    // ==============================================================
    // GATT Callback（内部で OS資源 を扱う）
    // ==============================================================
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            isConnecting = false

            if (status != BluetoothGatt.GATT_SUCCESS) {
                val reason = "GATT error status=$status"
                Log.e(TAG, "connection error: $reason")
                listener?.onConnectionFailed(gatt.device.address, reason)
                close()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "connected: ${gatt.device.address}")
                    listener?.onConnected(gatt.device.address)
                    // discoverServices は UI 側で呼ぶ
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "disconnected: ${gatt.device.address}")
                    listener?.onDisconnected(gatt.device.address)
                    close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val uuids = gatt.services.map { it.uuid }
                listener?.onServicesDiscovered(gatt.device.address, uuids)
            } else {
                val reason = "Service discovery failed: status=$status"
                Log.e(TAG, reason)
                listener?.onConnectionFailed(gatt.device.address, reason)
                close()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            listener?.onCharacteristicChanged(characteristic.uuid, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // 書き込みタイマー解除
            writeTimeoutMap[characteristic.uuid]?.let {
                mainHandler.removeCallbacks(it)
                writeTimeoutMap.remove(characteristic.uuid)
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener?.onWriteSuccess(characteristic.uuid)
            } else {
                listener?.onWriteFailed(characteristic.uuid, "Write failed status=$status")
            }
        }
    }

    // ==============================================================
    // Public API: write / enableNotification
    // - UUIDは呼び出し元で保持し、引数として渡す（UI側でUUIDを扱ってOK）
    // - BluetoothGatt / Descriptor 等の OS オブジェクトは内部で扱う
    // - 書き込みタイムアウトを追加
    // ==============================================================
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, data: ByteArray): Boolean {
        val g = bluetoothGatt ?: run {
            listener?.onWriteFailed(null, "GATT not connected")
            return false
        }

        val service = g.getService(serviceUuid)
        if (service == null) {
            listener?.onWriteFailed(null, "Service not found: $serviceUuid")
            return false
        }

        val characteristic = service.getCharacteristic(charUuid)
        if (characteristic == null) {
            listener?.onWriteFailed(null, "Characteristic not found: $charUuid")
            return false
        }

        characteristic.value = data
        val started = g.writeCharacteristic(characteristic)

        if (!started) {
            listener?.onWriteFailed(charUuid, "writeCharacteristic returned false")
            return false
        }

        // 書き込みタイマーセット
        val writeRunnable = Runnable {
            listener?.onWriteFailed(charUuid, "Write timeout after $WRITE_TIMEOUT_MS ms")
            writeTimeoutMap.remove(charUuid)
        }
        writeTimeoutMap[charUuid] = writeRunnable
        mainHandler.postDelayed(writeRunnable, WRITE_TIMEOUT_MS)

        return true
    }

    @SuppressLint("MissingPermission")
    fun enableNotification(serviceUuid: UUID, charUuid: UUID): Boolean {
        val g = bluetoothGatt ?: run {
            listener?.onConnectionFailed(null, "GATT not connected")
            return false
        }

        val service = g.getService(serviceUuid) ?: run {
            listener?.onConnectionFailed(null, "Service not found: $serviceUuid")
            return false
        }

        val characteristic = service.getCharacteristic(charUuid) ?: run {
            listener?.onConnectionFailed(null, "Characteristic not found: $charUuid")
            return false
        }

        val ok = g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeStarted = g.writeDescriptor(descriptor)
            if (!writeStarted) {
                listener?.onConnectionFailed(g.device.address, "writeDescriptor returned false")
            }
        } else {
            Log.w(TAG, "Client Characteristic Configuration Descriptor not found for $charUuid")
        }
        return ok
    }
}


====================================================================================================================

import CoreBluetooth
import Foundation

/// BLEModule
///
/// - CBCentralManager / CBPeripheral 等の OS リソースは内部で管理
/// - Service / Characteristic UUID は外部で管理（UI/VM側で保持）
/// - スキャン / 接続 / サービス探索 / 書き込み / 通知 を提供
/// - 異常時は必ず listener に通知し、UI がハングしないようにする
final class BLEModule: NSObject {

    // MARK: - Listener: UI / ViewModel に通知するイベント
    protocol Listener: AnyObject {
        /// デバイスが見つかった
        func onScanResult(name: String?, address: UUID)
        /// スキャンに失敗した
        func onScanFailed(error: Error)
        /// スキャンがタイムアウトした
        func onScanTimeout()
        
        /// 接続開始した
        func onConnecting(address: UUID)
        /// 接続成功
        func onConnected(address: UUID)
        /// 切断された
        func onDisconnected(address: UUID)
        /// 接続に失敗した
        func onConnectionFailed(address: UUID?, reason: String)
        
        /// サービス探索成功
        func onServicesDiscovered(address: UUID, services: [CBUUID])
        /// キャラクタリスティック通知を受信
        func onCharacteristicChanged(uuid: CBUUID, value: Data)
        /// 書き込み成功
        func onWriteSuccess(uuid: CBUUID)
        /// 書き込み失敗
        func onWriteFailed(uuid: CBUUID?, reason: String)
    }
    
    weak var listener: Listener?

    // MARK: - Internal state
    private var central: CBCentralManager!
    private var targetPeripheral: CBPeripheral?

    private var isScanning = false
    private var isConnecting = false

    // タイマーによるスキャン・書き込みタイムアウト管理
    private var scanTimeoutTimer: Timer?
    private var writeTimeoutTimers: [CBUUID: Timer] = [:]
    private let WRITE_TIMEOUT: TimeInterval = 5.0

    // MARK: - Init
    override init() {
        super.init()
        // メインスレッドで BLE イベントを受け取る
        central = CBCentralManager(delegate: self, queue: .main)
    }

    // MARK: - Scan
    /// BLE デバイスをスキャン開始
    /// - timeout: タイムアウト（秒）
    func startScan(timeout: TimeInterval = 10.0) {
        guard central.state == .poweredOn else {
            listener?.onScanFailed(error: NSError(domain: "BLE", code: -1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is off"]))
            return
        }

        guard !isScanning else { return }

        isScanning = true
        central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])

        // タイマーでスキャンタイムアウトを管理
        scanTimeoutTimer?.invalidate()
        scanTimeoutTimer = Timer.scheduledTimer(withTimeInterval: timeout, repeats: false) { [weak self] _ in
            guard let self else { return }
            if self.isScanning {
                self.stopScan()
                self.listener?.onScanTimeout()
            }
        }

        print("[BLE] scan started")
    }

    /// スキャン停止
    func stopScan() {
        guard isScanning else { return }
        central.stopScan()
        isScanning = false
        print("[BLE] scan stopped")
    }

    // MARK: - Connect / Disconnect
    /// デバイスに接続
    func connect(to peripheralId: UUID) {
        guard central.state == .poweredOn else {
            listener?.onConnectionFailed(address: peripheralId, reason: "Bluetooth powered off")
            return
        }

        // キャッシュから取得
        if let knownPeripheral = central.retrievePeripherals(withIdentifiers: [peripheralId]).first {
            isConnecting = true
            listener?.onConnecting(address: knownPeripheral.identifier)
            targetPeripheral = knownPeripheral
            targetPeripheral?.delegate = self
            central.connect(knownPeripheral, options: nil)
        } else {
            listener?.onConnectionFailed(address: nil, reason: "Peripheral not found in cache")
        }
    }

    /// 接続解除
    func disconnect() {
        guard let p = targetPeripheral else { return }
        central.cancelPeripheralConnection(p)
        invalidateAllWriteTimeouts() // 書き込みタイマーも解除
    }

    /// BLE モジュールを完全クローズ
    func close() {
        stopScan()
        invalidateAllWriteTimeouts()
        targetPeripheral = nil
        isConnecting = false
    }

    // MARK: - Discover Services
    /// サービス探索（UI側が明示的に呼ぶ）
    func discoverServices(_ serviceUUIDs: [CBUUID]? = nil) {
        guard let p = targetPeripheral else {
            listener?.onConnectionFailed(address: nil, reason: "Peripheral not connected")
            return
        }
        p.discoverServices(serviceUUIDs)
    }

    // MARK: - Write Characteristic
    /// キャラクタリスティックに書き込み（タイムアウト付き）
    func writeCharacteristic(serviceUuid: CBUUID, charUuid: CBUUID, data: Data) {
        guard let p = targetPeripheral else {
            listener?.onWriteFailed(uuid: nil, reason: "Peripheral not connected")
            return
        }

        guard let service = p.services?.first(where: { $0.uuid == serviceUuid }),
              let characteristic = service.characteristics?.first(where: { $0.uuid == charUuid }) else {
            listener?.onWriteFailed(uuid: nil, reason: "Characteristic not found")
            return
        }

        // 書き込み実行
        p.writeValue(data, for: characteristic, type: .withResponse)

        // タイムアウト設定
        writeTimeoutTimers[charUuid]?.invalidate()
        writeTimeoutTimers[charUuid] = Timer.scheduledTimer(withTimeInterval: WRITE_TIMEOUT, repeats: false) { [weak self] _ in
            self?.listener?.onWriteFailed(uuid: charUuid, reason: "Write timeout (\(self?.WRITE_TIMEOUT ?? 0)s)")
            self?.writeTimeoutTimers.removeValue(forKey: charUuid)
        }
    }

    private func invalidateWriteTimeout(for uuid: CBUUID) {
        writeTimeoutTimers[uuid]?.invalidate()
        writeTimeoutTimers.removeValue(forKey: uuid)
    }

    private func invalidateAllWriteTimeouts() {
        writeTimeoutTimers.values.forEach { $0.invalidate() }
        writeTimeoutTimers.removeAll()
    }

    // MARK: - Enable Notification
    /// キャラクタリスティック通知開始
    func enableNotification(serviceUuid: CBUUID, charUuid: CBUUID) {
        guard let p = targetPeripheral else {
            listener?.onConnectionFailed(address: nil, reason: "Peripheral not connected")
            return
        }

        guard let service = p.services?.first(where: { $0.uuid == serviceUuid }),
              let characteristic = service.characteristics?.first(where: { $0.uuid == charUuid }) else {
            listener?.onConnectionFailed(address: nil, reason: "Characteristic not found")
            return
        }

        // 通知を有効化
        p.setNotifyValue(true, for: characteristic)
    }
}

// =====================================================
// MARK: - CBCentralManagerDelegate
// =====================================================

extension BLEModule: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            print("[BLE] powered on")
        case .unauthorized, .unsupported, .poweredOff:
            print("[BLE] unavailable: \(central.state.rawValue)")
        default: break
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        listener?.onScanResult(name: peripheral.name, address: peripheral.identifier)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        isConnecting = false
        targetPeripheral = peripheral
        targetPeripheral?.delegate = self
        listener?.onConnected(address: peripheral.identifier)
        print("[BLE] connected \(peripheral.identifier)")
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        isConnecting = false
        listener?.onConnectionFailed(address: peripheral.identifier, reason: error?.localizedDescription ?? "failed to connect")
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        invalidateAllWriteTimeouts()
        if let error = error {
            listener?.onConnectionFailed(address: peripheral.identifier, reason: "Disconnected with error: \(error.localizedDescription)")
        } else {
            listener?.onDisconnected(address: peripheral.identifier)
        }
    }
}

// =====================================================
// MARK: - CBPeripheralDelegate
// =====================================================

extension BLEModule: CBPeripheralDelegate {

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            listener?.onConnectionFailed(address: peripheral.identifier, reason: "Service discovery failed: \(error.localizedDescription)")
            return
        }

        guard let services = peripheral.services else { return }
        listener?.onServicesDiscovered(address: peripheral.identifier, services: services.map { $0.uuid })
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        invalidateWriteTimeout(for: characteristic.uuid)
        if let error = error {
            listener?.onWriteFailed(uuid: characteristic.uuid, reason: error.localizedDescription)
        } else {
            listener?.onWriteSuccess(uuid: characteristic.uuid)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("[BLE] notification error: \(error.localizedDescription)")
            return
        }
        if let value = characteristic.value {
            listener?.onCharacteristicChanged(uuid: characteristic.uuid, value: value)
        }
    }
}
