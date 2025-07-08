# my-first-app
My first web app

🔁 通信の仕組み（基本構造）
BLEは**「ペリフェラル（Peripheral）」と「セントラル（Central）」**という2つの役割で通信します。

用語	説明
Central	スマートフォンやPCなど「スキャンして接続する側」
Peripheral	心拍センサーなど「広告（アドバタイズ）して接続される側」



📚 GATTとATT
BLEではデータ構造として**GATT（Generic Attribute Profile）とATT（Attribute Protocol）**を使います。

GATT構造：
BLEデバイスは「サービス（Service）」と「キャラクタリスティック（Characteristic）」という単位でデータをやり取りします。

Service：機能のまとまり（例：心拍数サービス）

Characteristic：1つのデータ項目（例：現在の心拍数）

Descriptor：Characteristicに関する追加情報（例：単位、説明）



📡 アドバタイズ（Advertising）
BLEデバイスは、接続前にアドバタイズパケットをブロードキャストします。これには以下のような情報が含まれます：

デバイス名

サービスUUID

メーカー情報

16バイト程度の任意データ

ビーコン（例：iBeacon、Eddystone）もこの仕組みを利用しています。


🔧 BLEサービスとは？
サービスは、**キャラクタリスティック（Characteristic）**というデータ項目の「グループ」です。

例えば「心拍数サービス」には「現在の心拍数」や「測定のタイミング」などのデータが含まれます。

各サービスには**UUID（識別子）**が割り当てられており、標準化されたものと、カスタム（独自）サービスがあります。


📚 サービスの種類
BLEサービスは大きく分けて以下の2種類です：

種類	内容
標準サービス（Standard Services）	Bluetooth SIG によって定義された共通のサービス。UUIDは16ビット（例：0x180D = 心拍数）
カスタムサービス（Custom Services）	開発者が独自に定義するサービス。UUIDは128ビット（例：12345678-1234-5678-1234-56789abcdef0）


| サービス名                         | UUID     | 説明                           |
| ----------------------------- | -------- | ---------------------------- |
| **Generic Access**            | `0x1800` | デバイス名や接続パラメータなど              |
| **Generic Attribute**         | `0x1801` | サービスの一覧情報（Service Changedなど） |
| **Device Information**        | `0x180A` | メーカー名、モデル番号など                |
| **Battery Service**           | `0x180F` | バッテリー残量の情報                   |
| **Heart Rate**                | `0x180D` | 心拍センサー用                      |
| **Health Thermometer**        | `0x1809` | 体温計用                         |
| **Blood Pressure**            | `0x1810` | 血圧計用                         |
| **Cycling Speed and Cadence** | `0x1816` | 自転車の速度・ケイデンス                 |
| **Current Time Service**      | `0x1805` | 現在時刻を取得・設定する                 |
| **Environmental Sensing**     | `0x181A` | 温度・湿度・気圧などの環境センサデータ          |



🧪 カスタムサービスとは？
独自のアプリケーションやハードウェアのために定義するサービスです。

UUIDは128ビット（例：f000aa00-0451-4000-b000-000000000000）

複数のキャラクタリスティックを含むことができる

例：あなたの独自の温湿度センサーが、"MyEnvSensorService" としてサービスを持つ

📄 キャラクタリスティックとの関係
サービスは複数のキャラクタリスティックを含みます：

java
コピーする
編集する
Service: Heart Rate (0x180D)
├── Characteristic: Heart Rate Measurement (0x2A37)
├── Characteristic: Body Sensor Location (0x2A38)
└── Characteristic: Heart Rate Control Point (0x2A39)


✅ あなたのコードを解剖
0000180D-0000-1000-8000-00805f9b34fb
0x180D → 心拍数サービス（Heart Rate Service）

完全な128ビット形式に変換したもの

00002A37-0000-1000-8000-00805f9b34fb
0x2A37 → 心拍数測定キャラクタリスティック（Heart Rate Measurement Characteristic）

📦 なぜ128ビットで指定するのか？
AndroidやBLEライブラリは内部的にすべてのUUIDを128ビット形式で扱うため、getService() や getCharacteristic() を使うときには128ビットのUUIDを明示的に指定する必要があります。

Heart Rate Service	0x180D	心拍数データ提供
Heart Rate Measurement	0x2A37	実際の心拍データ
Battery Service	0x180F	バッテリー情報
Battery Level	0x2A19	残量（%）

📌 ベースUUIDの詳細
標準のベースUUID（Bluetooth Base UUID）：

コピーする
編集する
00000000-0000-1000-8000-00805F9B34FB
BLEの16ビットUUIDを128ビットに拡張するには、以下のように埋め込みます：

mathematica
コピーする
編集する
0x180D → 0000180D-0000-1000-8000-00805F9B34FB
0x2A37 → 00002A37-0000-1000-8000-00805F9B34FB


🔍 値の中身は基本的に byte配列（ByteArray, byte[]）
→ なので、アプリ側でデコードが必要です。

Alert Level	0x2A06	0x01（Mild）など	デバイスを振動・音で警告
LED Control（カスタム）	独自UUID	0x01 → 点灯、0x00 → 消灯	自作BLEライト制御
Sampling Rate（カスタム）	独自UUID	例：100（ms単位）	センサーデータの頻度設定

