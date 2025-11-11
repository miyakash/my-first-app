🎯 原因の正体：AARに外部依存ライブラリが「含まれない」こと

AAR（Androidライブラリ）は、自分が依存している外部ライブラリの .jar や .aar を同梱しません。
Gradleがそれらを「依存グラフを通じて解決」してくれる想定です。

つまり：

SDKの build.gradle で

implementation "androidx.preference:preference-ktx:1.2.1"


としていると、
その preference-ktx は SDK内部専用扱い で、AARには同梱されません。

その状態でUI側にAARを導入しても、
UIプロジェクトから見ると androidx.preference は存在しない。

→ 結果、実行時（またはビルド時）にクラスが見つからないエラーが発生。

✅ 対応策

この場合、2通りの解決策があります。

① SDK側で api に変更する（推奨）
dependencies {
    api "androidx.preference:preference-ktx:1.2.1"
}


→ UI側に依存が自動で伝わり、
UI側の gradle に何も書かなくても動作します。

② UI側に明示的に依存追加（応急処置）
dependencies {
    implementation "androidx.preference:preference-ktx:1.2.1"
}


→ これで動くけど、
本来は「SDKが内部で必要としているもの」なので、UIが知る必要はない。
SDKの責任範囲にした方がよい（= api にする方が設計として正しい）。

💬 まとめ
状況	対応
SDK内部で PreferenceManager / SharedPreferences を使っている	SDK側 build.gradle の dependencies を api に変更
UI側に依存を追加しないと動かない	→ SDKのAARが依存を伝播できていないサイン
Android標準APIを使っているだけ（android.content.SharedPreferences）	通常は不要（androidx.preferenceを使っていないなら）


🔍 結論から言うと
SDKの記載方法	UI側に追加が必要？	説明
implementation	✅ 必要になることがある	UIからは依存ライブラリが「見えない」
api	❌ 不要	UIに依存関係が伝わる（AAR経由で自動解決される）
💡 具体例
✅ 例1：UI側に追加が不要なケース
// SDKの build.gradle
dependencies {
    api "androidx.preference:preference-ktx:1.2.1"
}


このように api を使うと、
SDKを使うUI側プロジェクトでも自動的に androidx.preference が使えるようになります。
（UIのgradleに何も書かなくてOK）

⚠️ 例2：UI側に追加が必要なケース
// SDKの build.gradle
dependencies {
    implementation "androidx.preference:preference-ktx:1.2.1"
}


この場合、UIアプリがSDK経由で PreferenceManager や PreferenceFragmentCompat などにアクセスすると、
ビルド時に以下のようなエラーになります：

Unresolved reference: PreferenceManager


なぜかというと、implementation は「SDK内部でしか使わない依存」とみなされ、
AARとして配布された際にUIには伝播しない からです。

🧱 どう使い分けるか
用途	推奨指定	理由
SDKの内部だけで使うライブラリ（UIから見えない）	implementation	内部カプセル化できる
UI側でも必要になる（UIから型やクラスを参照する）	api	UIに伝播させる必要あり
🔧 あなたのケース（SharedPreferences系）

SharedPreferences は Android 標準APIなので、

androidx.preference などを使っていなければ → UI側に追加不要。

androidx.preference をSDKで使っている場合 → SDK側で api にしておけばUI側は何もしなくてOK。



**＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝
＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝
＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝**

**新版**

package com.example.bleapp.ble

import java.util.*

/**
 * BLEデバイス情報を保持するデータクラス
 * - address: デバイスのMACアドレス
 * - name: デバイス名（nullの可能性あり）
 */
data class BLEDeviceInfo(
    val address: String,
    val name: String?
)

/**
 * サービス情報
 * - BLEのサービスUUIDを格納
 */
data class BLEServiceInfo(
    val uuid: UUID
)

/**
 * キャラクタリスティック情報
 * - 所属するサービスUUIDとキャラクタリスティックUUIDを保持
 */
data class BLECharacteristicInfo(
    val serviceUUID: UUID,
    val characteristicUUID: UUID
)


===================

package com.example.bleapp.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

/**
 * BLE通信の下位モジュール
 *
 * AndroidのBluetoothGattを直接扱い、
 * スキャン・接続・サービス探索・通知設定・書き込みなどを行う。
 * 上位層（BLECore）にイベントコールバックを通知する。
 */
@SuppressLint("MissingPermission")
class BLEModule(
    private val context: Context,
    private val listener: Listener
) {

    /**
     * BLEの主要イベントを通知するためのリスナー
     */
    interface Listener {
        fun onDeviceFound(deviceInfo: BLEDeviceInfo)                         // スキャンでデバイス発見時
        fun onConnected(deviceInfo: BLEDeviceInfo)                           // 接続成功時
        fun onDisconnected(deviceInfo: BLEDeviceInfo)                        // 切断時
        fun onServicesDiscovered(deviceInfo: BLEDeviceInfo, services: List<BLEServiceInfo>) // サービス探索完了時
        fun onCharacteristicChanged(
            deviceInfo: BLEDeviceInfo,
            characteristic: BLECharacteristicInfo,
            value: ByteArray
        )                                                                    // 通知（Notify）受信時
        fun onWriteSuccess(deviceInfo: BLEDeviceInfo, characteristic: BLECharacteristicInfo) // 書き込み成功時
        fun onError(error: ErrorType, message: String? = null)               // エラー時
    }

    /**
     * 各種エラー分類
     */
    enum class ErrorType {
        SCAN_FAILED, CONNECT_FAILED, DISCOVER_FAILED, WRITE_FAILED, GATT_ERROR, NOTIFY_FAILED
    }

    // Bluetooth関連オブジェクト
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null

    private val handler = Handler(Looper.getMainLooper())

    /**
     * デバイススキャンを開始
     * 一定時間後に自動停止する（timeoutMs）
     */
    fun startScan(timeoutMs: Long = 10000L) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            listener.onError(ErrorType.SCAN_FAILED, "Bluetooth unavailable")
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        scanner?.startScan(scanCallback)

        // タイムアウト後にスキャン停止
        handler.postDelayed({ stopScan() }, timeoutMs)
    }

    /** スキャン停止 */
    fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    /** スキャン結果コールバック */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let {
                listener.onDeviceFound(BLEDeviceInfo(it.address, it.name))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onError(ErrorType.SCAN_FAILED, "Scan failed: $errorCode")
        }
    }

    /**
     * 指定したアドレスのデバイスへ接続
     */
    fun connect(address: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
                ?: return listener.onError(ErrorType.CONNECT_FAILED, "Device not found for address: $address")

            // false = 自動再接続しない
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            listener.onError(ErrorType.CONNECT_FAILED, e.message)
        }
    }

    /** 切断処理 */
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    /**
     * GATT通信に関するイベントコールバック
     */
    private val gattCallback = object : BluetoothGattCallback() {

        /** 接続状態変更時（接続・切断など） */
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val device = gatt?.device ?: return
            val info = BLEDeviceInfo(device.address, device.name)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError(ErrorType.GATT_ERROR, "Connection error: $status")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> listener.onConnected(info)
                BluetoothProfile.STATE_DISCONNECTED -> listener.onDisconnected(info)
            }
        }

        /** サービス探索完了時 */
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val device = gatt?.device ?: return
            val info = BLEDeviceInfo(device.address, device.name)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services.map { BLEServiceInfo(it.uuid) }
                listener.onServicesDiscovered(info, services)
            } else {
                listener.onError(ErrorType.DISCOVER_FAILED, "Service discovery failed: $status")
            }
        }

        /** キャラクタリスティック書き込み完了時 */
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
        ) {
            val device = gatt?.device ?: return
            val info = BLEDeviceInfo(device.address, device.name)
            val charInfo = characteristic?.let { BLECharacteristicInfo(it.service.uuid, it.uuid) } ?: return

            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onWriteSuccess(info, charInfo)
            } else {
                listener.onError(ErrorType.WRITE_FAILED, "Write failed: $status")
            }
        }

        /** 通知（Notify）受信時 */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?
        ) {
            val device = gatt?.device ?: return
            val info = BLEDeviceInfo(device.address, device.name)
            val charInfo = characteristic?.let { BLECharacteristicInfo(it.service.uuid, it.uuid) } ?: return

            listener.onCharacteristicChanged(info, charInfo, characteristic.value ?: byteArrayOf())
        }
    }

    /** サービス探索を実行 */
    fun discoverServices() {
        bluetoothGatt?.discoverServices()
            ?: listener.onError(ErrorType.DISCOVER_FAILED, "Gatt not connected")
    }

    /**
     * キャラクタリスティックに値を書き込む
     */
    fun writeCharacteristic(serviceUUID: UUID, charUUID: UUID, value: ByteArray) {
        val gatt = bluetoothGatt ?: return listener.onError(ErrorType.WRITE_FAILED, "No GATT connection")
        val service = gatt.getService(serviceUUID)
            ?: return listener.onError(ErrorType.WRITE_FAILED, "Service not found: $serviceUUID")
        val characteristic = service.getCharacteristic(charUUID)
            ?: return listener.onError(ErrorType.WRITE_FAILED, "Characteristic not found: $charUUID")

        characteristic.value = value

        // 非同期で結果は onCharacteristicWrite に通知される
        if (!gatt.writeCharacteristic(characteristic)) {
            listener.onError(ErrorType.WRITE_FAILED, "writeCharacteristic() returned false")
        }
    }

    /**
     * 通知（Notify）を有効化
     * - setCharacteristicNotification() に加えて、
     *   CCCD (Client Characteristic Configuration Descriptor) の設定も必須。
     */
    fun enableNotification(serviceUUID: UUID, charUUID: UUID, enable: Boolean = true) {
        val gatt = bluetoothGatt ?: return listener.onError(ErrorType.NOTIFY_FAILED, "No GATT connection")
        val service = gatt.getService(serviceUUID)
            ?: return listener.onError(ErrorType.NOTIFY_FAILED, "Service not found: $serviceUUID")
        val characteristic = service.getCharacteristic(charUUID)
            ?: return listener.onError(ErrorType.NOTIFY_FAILED, "Characteristic not found: $charUUID")

        val success = gatt.setCharacteristicNotification(characteristic, enable)
        if (!success) {
            listener.onError(ErrorType.NOTIFY_FAILED, "setCharacteristicNotification failed")
            return
        }

        // CCCD（通知有効化デスクリプタ）の設定
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            descriptor.value = if (enable)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

            if (!gatt.writeDescriptor(descriptor)) {
                listener.onError(ErrorType.NOTIFY_FAILED, "writeDescriptor failed")
            }
        } else {
            listener.onError(ErrorType.NOTIFY_FAILED, "CCCD descriptor not found")
        }
    }
}

=====================


package com.example.bleapp.ble

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

/**
 * BLE通信の上位制御クラス
 *
 * BLEModuleのイベントを受け取り、
 * 一連のフロー（スキャン → 接続 → サービス探索 → 通知登録 → 書き込み）を管理する。
 */
class BLECore(context: Context) : BLEModule.Listener {

    private val bleModule = BLEModule(context, this)
    private val handler = Handler(Looper.getMainLooper())

    private var connectedAddress: String? = null
    private var currentState = State.IDLE

    // 対象デバイスの想定サービス/キャラクタリスティック
    private val targetServiceUUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb") // Heart Rate Service
    private val targetCharUUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")   // Measurement Characteristic

    private val writeData = byteArrayOf(0x01, 0x02, 0x03)

    /** 内部状態管理 */
    private enum class State {
        IDLE, SCANNING, CONNECTING, DISCOVERING, SUBSCRIBING, WRITING, COMPLETED, ERROR
    }

    /** フロー開始 */
    fun startFlow() {
        Log.d("BLECore", "===== BLE FLOW START =====")
        transitionTo(State.SCANNING)
        bleModule.startScan()

        // スキャンタイムアウト監視
        handler.postDelayed({
            if (currentState == State.SCANNING) error("Scan timeout")
        }, 10000L)
    }

    /** デバイス発見時 */
    override fun onDeviceFound(deviceInfo: BLEDeviceInfo) {
        Log.d("BLECore", "Device found: ${deviceInfo.name} (${deviceInfo.address})")
        // 対象名を含むデバイスを検出したら接続
        if (deviceInfo.name?.contains("TargetDevice") == true) {
            bleModule.stopScan()
            transitionTo(State.CONNECTING)
            bleModule.connect(deviceInfo.address)
        }
    }

    /** 接続成功時 */
    override fun onConnected(deviceInfo: BLEDeviceInfo) {
        connectedAddress = deviceInfo.address
        transitionTo(State.DISCOVERING)
        bleModule.discoverServices()
    }

    /** 切断通知 */
    override fun onDisconnected(deviceInfo: BLEDeviceInfo) {
        Log.w("BLECore", "Disconnected: ${deviceInfo.address}")
        transitionTo(State.ERROR)
    }

    /** サービス探索結果 */
    override fun onServicesDiscovered(deviceInfo: BLEDeviceInfo, services: List<BLEServiceInfo>) {
        val target = services.find { it.uuid == targetServiceUUID }
        if (target == null) {
            error("Target service not found")
            return
        }

        // 通知を有効化
        transitionTo(State.SUBSCRIBING)
        bleModule.enableNotification(targetServiceUUID, targetCharUUID)

        // 書き込み実行例
        transitionTo(State.WRITING)
        bleModule.writeCharacteristic(targetServiceUUID, targetCharUUID, writeData)
    }

    /** 通知受信 */
    override fun onCharacteristicChanged(deviceInfo: BLEDeviceInfo, characteristic: BLECharacteristicInfo, value: ByteArray) {
        Log.d("BLECore", "Notify from ${characteristic.characteristicUUID}: ${value.joinToString()}")
    }

    /** 書き込み成功時 */
    override fun onWriteSuccess(deviceInfo: BLEDeviceInfo, characteristic: BLECharacteristicInfo) {
        Log.d("BLECore", "Write success: ${characteristic.characteristicUUID}")
        transitionTo(State.COMPLETED)
        Log.d("BLECore", "===== BLE FLOW COMPLETE =====")
    }

    /** モジュール側からのエラー通知 */
    override fun onError(error: BLEModule.ErrorType, message: String?) {
        error("BLEModule Error[$error]: $message")
    }

    /** 状態遷移 */
    private fun transitionTo(newState: State) {
        Log.d("BLECore", "State: $currentState → $newState")
        currentState = newState
    }

    /** エラー処理（状態遷移＋切断） */
    private fun error(msg: String) {
        Log.e("BLECore", "Error: $msg")
        transitionTo(State.ERROR)
        bleModule.disconnect()
    }
}


＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝
ios
import Foundation
import CoreBluetooth

// MARK: - Info Structs (Kotlinのデータクラス相当)
// デバイス・サービス・キャラクタリスティックの基本情報を保持
struct BLEDeviceInfo {
    let identifier: UUID
    let name: String?
}

struct BLEServiceInfo {
    let uuid: CBUUID
}

struct BLECharacteristicInfo {
    let serviceUUID: CBUUID
    let characteristicUUID: CBUUID
}

// MARK: - BLEModule
// BLEの低レベル処理を担当（スキャン、接続、書き込み、通知登録など）
final class BLEModule: NSObject {

    enum ErrorType {
        case scanFailed, connectFailed, discoverFailed, writeFailed, gattError, notifyFailed
    }

    protocol Listener: AnyObject {
        // ====== Output (O): BLEイベント通知 ======
        func onDeviceFound(_ deviceInfo: BLEDeviceInfo) // スキャンでデバイス検出時
        func onConnected(_ deviceInfo: BLEDeviceInfo) // 接続成功時
        func onDisconnected(_ deviceInfo: BLEDeviceInfo) // 切断時
        func onServicesDiscovered(_ deviceInfo: BLEDeviceInfo, services: [BLEServiceInfo]) // サービス発見時
        func onCharacteristicChanged(_ deviceInfo: BLEDeviceInfo, characteristic: BLECharacteristicInfo, value: Data) // 通知受信時
        func onWriteSuccess(_ deviceInfo: BLEDeviceInfo, characteristic: BLECharacteristicInfo) // 書き込み成功時
        func onError(_ error: ErrorType, message: String?) // エラー通知
    }

    private weak var listener: Listener?

    private var centralManager: CBCentralManager!
    private var connectedPeripheral: CBPeripheral?
    private var discoveredDevices: [UUID: CBPeripheral] = [:]
    private var scanTimer: Timer?

    // ====== Input (I): 初期化 ======
    init(listener: Listener) {
        super.init()
        self.listener = listener
        self.centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    // MARK: - Scan
    // ====== Input (I): スキャン開始要求 ======
    func startScan(timeout: TimeInterval = 10.0) {
        guard centralManager.state == .poweredOn else {
            listener?.onError(.scanFailed, message: "Bluetooth unavailable")
            return
        }
        discoveredDevices.removeAll()
        centralManager.scanForPeripherals(withServices: nil, options: nil)
        // タイムアウトで自動停止
        scanTimer?.invalidate()
        scanTimer = Timer.scheduledTimer(withTimeInterval: timeout, repeats: false) { [weak self] _ in
            self?.stopScan()
        }
    }

    // ====== Input (I): スキャン停止要求 ======
    func stopScan() {
        centralManager.stopScan()
    }

    // MARK: - Connect
    // ====== Input (I): デバイス接続要求 ======
    func connect(to identifier: UUID) {
        guard let peripheral = discoveredDevices[identifier] else {
            listener?.onError(.connectFailed, message: "Device not found for id \(identifier)")
            return
        }
        connectedPeripheral = peripheral
        peripheral.delegate = self
        centralManager.connect(peripheral, options: nil)
    }

    // ====== Input (I): 明示的な切断要求 ======
    func disconnect() {
        if let peripheral = connectedPeripheral {
            centralManager.cancelPeripheralConnection(peripheral)
        }
        connectedPeripheral = nil
    }

    // MARK: - Discover Services
    // ====== Input (I): サービス探索要求 ======
    func discoverServices() {
        guard let peripheral = connectedPeripheral else {
            listener?.onError(.discoverFailed, message: "No GATT connection")
            return
        }
        peripheral.discoverServices(nil)
    }

    // MARK: - Write
    // ====== Input (I): キャラクタリスティックへの書き込み要求 ======
    func writeCharacteristic(serviceUUID: CBUUID, charUUID: CBUUID, value: Data) {
        guard let peripheral = connectedPeripheral else {
            listener?.onError(.writeFailed, message: "No GATT connection")
            return
        }
        guard let service = peripheral.services?.first(where: { $0.uuid == serviceUUID }) else {
            listener?.onError(.writeFailed, message: "Service not found: \(serviceUUID)")
            return
        }
        guard let characteristic = service.characteristics?.first(where: { $0.uuid == charUUID }) else {
            listener?.onError(.writeFailed, message: "Characteristic not found: \(charUUID)")
            return
        }
        peripheral.writeValue(value, for: characteristic, type: .withResponse)
    }

    // MARK: - Enable Notification
    // ====== Input (I): 通知有効化/無効化要求 ======
    func enableNotification(serviceUUID: CBUUID, charUUID: CBUUID, enable: Bool = true) {
        guard let peripheral = connectedPeripheral else {
            listener?.onError(.notifyFailed, message: "No GATT connection")
            return
        }
        guard let service = peripheral.services?.first(where: { $0.uuid == serviceUUID }) else {
            listener?.onError(.notifyFailed, message: "Service not found: \(serviceUUID)")
            return
        }
        guard let characteristic = service.characteristics?.first(where: { $0.uuid == charUUID }) else {
            listener?.onError(.notifyFailed, message: "Characteristic not found: \(charUUID)")
            return
        }

        // Output(O): 通知設定リクエスト送信
        peripheral.setNotifyValue(enable, for: characteristic)
    }
}

// MARK: - CBCentralManagerDelegate
extension BLEModule: CBCentralManagerDelegate {

    // ====== Output (O): Bluetooth状態変化通知 ======
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state != .poweredOn {
            listener?.onError(.scanFailed, message: "Bluetooth not powered on")
        }
    }

    // ====== Output (O): デバイス検出 ======
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        discoveredDevices[peripheral.identifier] = peripheral
        let info = BLEDeviceInfo(identifier: peripheral.identifier, name: peripheral.name)
        listener?.onDeviceFound(info)
    }

    // ====== Output (O): 接続成功 ======
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let info = BLEDeviceInfo(identifier: peripheral.identifier, name: peripheral.name)
        listener?.onConnected(info)
        peripheral.delegate = self
        peripheral.discoverServices(nil)
    }

    // ====== Output (O): 接続失敗 ======
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        listener?.onError(.connectFailed, message: error?.localizedDescription)
    }

    // ====== Output (O): 切断検出 ======
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let info = BLEDeviceInfo(identifier: peripheral.identifier, name: peripheral.name)
        listener?.onDisconnected(info)
    }
}

// MARK: - CBPeripheralDelegate
extension BLEModule: CBPeripheralDelegate {

    // ====== Output (O): サービス探索結果 ======
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            listener?.onError(.discoverFailed, message: error.localizedDescription)
            return
        }

        guard let services = peripheral.services else { return }
        let info = BLEDeviceInfo(identifier: peripheral.identifier, name: peripheral.name)
        let list = services.map { BLEServiceInfo(uuid: $0.uuid) }
        listener?.onServicesDiscovered(info, services: list)

        // I: 各サービスのキャラクタリスティックを探索
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }

    // ====== Output (O): 書き込み結果通知 ======
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        let info = BLEDeviceInfo(identifier: peripheral.identifier, name: peripheral.name)
        let charInfo = BLECharacteristicInfo(serviceUUID: characteristic.service.uuid, characteristicUUID: characteristic.uuid)
        if let error = error {
            listener?.onError(.writeFailed, message: error.localizedDescription)
        } else {
            listener?.onWriteSuccess(info, characteristic: charInfo)
        }
    }

    // ====== Output (O): 通知値更新 ======
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil else {
            listener?.onError(.notifyFailed, message: error?.localizedDescription)
            return
        }
        guard let value = characteristic.value else { return }
        let info = BLEDeviceInfo(identifier: peripheral.identifier, name: peripheral.name)
        let charInfo = BLECharacteristicInfo(serviceUUID: characteristic.service.uuid, characteristicUUID: characteristic.uuid)
        listener?.onCharacteristicChanged(info, characteristic: charInfo, value: value)
    }
}


=========================
import Foundation
import CoreBluetooth

// MARK: - BLECore
// BLEModuleを使って一連の処理フローを管理
final class BLECore: BLEModule.Listener {

    private let bleModule: BLEModule
    private var currentState: State = .idle
    private var connectedIdentifier: UUID?

    private let targetServiceUUID = CBUUID(string: "180D")  // Heart Rate Service
    private let targetCharUUID = CBUUID(string: "2A37")     // Measurement Characteristic
    private let writeData = Data([0x01, 0x02, 0x03])

    private enum State {
        case idle, scanning, connecting, discovering, subscribing, writing, completed, error
    }

    init() {
        bleModule = BLEModule(listener: self)
    }

    // ====== Input (I): フロー開始要求 ======
    func startFlow() {
        print("===== BLE FLOW START =====")
        transition(to: .scanning)
        bleModule.startScan()

        // スキャンタイムアウト検出
        DispatchQueue.main.asyncAfter(deadline: .now() + 10) {
            if self.currentState == .scanning {
                self.error("Scan timeout")
            }
        }
    }

    // ====== Output (O): デバイス検出 ======
    func onDeviceFound(_ deviceInfo: BLEDeviceInfo) {
        print("Device found: \(deviceInfo.name ?? "Unknown") (\(deviceInfo.identifier))")
        if deviceInfo.name?.contains("TargetDevice") == true {
            bleModule.stopScan()
            transition(to: .connecting)
            bleModule.connect(to: deviceInfo.identifier)
        }
    }

    // ====== Output (O): 接続成功 ======
    func onConnected(_ deviceInfo: BLEDeviceInfo) {
        connectedIdentifier = deviceInfo.identifier
        transition(to: .discovering)
        bleModule.discoverServices()
    }

    // ====== Output (O): 切断検出 ======
    func onDisconnected(_ deviceInfo: BLEDeviceInfo) {
        print("Disconnected: \(deviceInfo.identifier)")
        transition(to: .error)
    }

    // ====== Output (O): サービス発見後の処理 ======
    func onServicesDiscovered(_ deviceInfo: BLEDeviceInfo, services: [BLEServiceInfo]) {
        guard services.contains(where: { $0.uuid == targetServiceUUID }) else {
            error("Target service not found")
            return
        }

        // I: 通知を有効化
        transition(to: .subscribing)
        bleModule.enableNotification(serviceUUID: targetServiceUUID, charUUID: targetCharUUID)

        // I: 書き込み実行
        transition(to: .writing)
        bleModule.writeCharacteristic(serviceUUID: targetServiceUUID, charUUID: targetCharUUID, value: writeData)
    }

    // ====== Output (O): 通知受信 ======
    func onCharacteristicChanged(_ deviceInfo: BLEDeviceInfo, characteristic: BLECharacteristicInfo, value: Data) {
        print("Notify from \(characteristic.characteristicUUID): \(Array(value))")
    }

    // ====== Output (O): 書き込み成功 ======
    func onWriteSuccess(_ deviceInfo: BLEDeviceInfo, characteristic: BLECharacteristicInfo) {
        print("Write success: \(characteristic.characteristicUUID)")
        transition(to: .completed)
        print("===== BLE FLOW COMPLETE =====")
    }

    // ====== Output (O): エラー発生 ======
    func onError(_ error: BLEModule.ErrorType, message: String?) {
        self.error("BLEModule Error[\(error)]: \(message ?? "")")
    }

    // ステート遷移管理
    private func transition(to newState: State) {
        print("State: \(currentState) → \(newState)")
        currentState = newState
    }

    // ====== Input (I): 内部エラー処理 ======
    private func error(_ msg: String) {
        print("Error: \(msg)")
        transition(to: .error)
        bleModule.disconnect()
    }
}


今は同じプロジェクト内でmainactivitysdk相当のクラスが一緒になっています。フォアグラウンドサービス上で動いています。その際に通知をタップした際に戻る画面をmainactivityにインテントを指定しているので、sdkとして配布しようとするとそんなmainactivityしていないよってエラーになってしまう。なので、フォアグラウンドサービスで動くsdkの前提をいったんやめて、サービスを起動させないで動かすような構成にしようと思っています。 その際の、それぞれのパターンを教えて欲しい。
