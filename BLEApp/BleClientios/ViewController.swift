import UIKit
import CoreBluetooth

class ViewController: UIViewController {

    @IBOutlet weak var labelReadValue: UILabel!
    @IBOutlet weak var labelWriteValue: UILabel!

    private var centralManager: CBCentralManager!
    private var targetPeripheral: CBPeripheral?

    private let serviceUUID = CBUUID(string: "12345678-1234-5678-1234-56789abcdef0")
    private let characteristicUUID = CBUUID(string: "12345678-1234-5678-1234-56789abcdef1")

    private let writeData = "HelloServer".data(using: .utf8)!

    override func viewDidLoad() {
        super.viewDidLoad()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
}

extension ViewController: CBCentralManagerDelegate, CBPeripheralDelegate {

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            centralManager.scanForPeripherals(withServices: [serviceUUID], options: nil)
            print("Scanning started")
        } else {
            print("Bluetooth not ready: \(central.state.rawValue)")
        }
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String : Any],
                        rssi RSSI: NSNumber) {
        print("Discovered: \(peripheral.name ?? "Unknown")")
        targetPeripheral = peripheral
        centralManager.stopScan()
        centralManager.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager,
                        didConnect peripheral: CBPeripheral) {
        print("Connected to \(peripheral.name ?? "device")")
        peripheral.delegate = self
        peripheral.discoverServices([serviceUUID])
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverServices error: Error?) {
        if let services = peripheral.services {
            for service in services {
                peripheral.discoverCharacteristics([characteristicUUID], for: service)
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverCharacteristicsFor service: CBService,
                    error: Error?) {
        if let characteristics = service.characteristics {
            for characteristic in characteristics {
                if characteristic.uuid == characteristicUUID {
                    // まず読み取り
                    peripheral.readValue(for: characteristic)
                }
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        if let data = characteristic.value,
           let stringValue = String(data: data, encoding: .utf8) {
            print("Read value: \(stringValue)")
            DispatchQueue.main.async {
                self.labelReadValue.text = "読み取り値: \(stringValue)"
            }
            // 読み取り完了後に書き込み開始
            if characteristic.properties.contains(.write) || characteristic.properties.contains(.writeWithoutResponse) {
                peripheral.writeValue(writeData, for: characteristic, type: .withResponse)
                DispatchQueue.main.async {
                    self.labelWriteValue.text = "書き込み値: HelloServer"
                }
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didWriteValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        if let error = error {
            print("Write failed: \(error.localizedDescription)")
        } else {
            print("Write succeeded")
        }
    }
}
