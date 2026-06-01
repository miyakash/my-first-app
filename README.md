CoreBluetoothでは、Bluetooth OFF時の
didDisconnectPeripheral と state更新通知の順序が保証されていない。

そのため didDisconnectPeripheral 時点で
central.state を参照しても、
まだ .poweredOn の場合がある。

補足

didDisconnectPeripheral の error に：

CBError.bluetoothPoweredOff

が含まれる場合もありますが、

error == nil の場合がある
iOS version差異がある
常に保証されない

ため、補助情報として扱う必要があります。
