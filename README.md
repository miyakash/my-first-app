
まず結論（9割ここ）

connect(_ peripheral:) に渡している CBPeripheral が無効 or 期限切れです。

主な原因とチェックポイント
① scanForPeripherals で取得した peripheralを保持していない
func centralManager(_ central: CBCentralManager,
                    didDiscover peripheral: CBPeripheral,
                    advertisementData: [String : Any],
                    rssi RSSI: NSNumber) {

    central.connect(peripheral, options: nil) // ← これだけだとNGになりやすい
}


❌ ローカル変数のまま connect するとエラーになることがある

✅ プロパティで強参照する

var targetPeripheral: CBPeripheral?

func centralManager(_ central: CBCentralManager,
                    didDiscover peripheral: CBPeripheral,
                    advertisementData: [String : Any],
                    rssi RSSI: NSNumber) {

    self.targetPeripheral = peripheral
    central.connect(peripheral, options: nil)
}

② CBCentralManager が .poweredOn になる前に connect()
central.connect(peripheral, options: nil) // ← state未確認


✅ 必ず確認

func centralManagerDidUpdateState(_ central: CBCentralManager) {
    guard central.state == .poweredOn else { return }
}

③ すでに切断された peripheral を再利用している

アプリ再起動

バックグラウンド復帰後

前回の peripheral を保存して再接続

❌ 古い CBPeripheral は 無効

✅ 再取得する

central.scanForPeripherals(withServices: nil)


または

central.retrievePeripherals(withIdentifiers: [uuid])


===============================================

ver2

Secure Enclave / Keychain 設計および回帰テスト方針について  
#### 1. なぜ XCTest では Secure Enclave が利用できないのか
※copilotと動作確認を元にした見解  

Secure Enclave は以下の特性を持つ。  
・実機のハードウェアに強く依存    
・アプリ単位でサンドボックスに紐づく  
・鍵は Secure Enclave 内部に生成・保持され、アプリ外（別 Bundle ID）から参照できない  

一方で XCTest 実行時の構成は以下の通り。  
XCTest は テスト対象アプリとは別アプリ（XCTest Runner） として起動される  
Bundle ID / Code Sign / Keychain Access Group が異なる  
エミュレータにおける Secure Enclave は 完全なエミュレーションが提供されていない 

このため、  
・アプリ実行時は動作する Secure Enclave 処理でも  
・XCTest 実行時には    
　・鍵生成に失敗する  
　・既存鍵にアクセスできない  
といった問題が発生する。  

→これは実装不備ではなく、iOS のセキュリティモデル上の制約である。  


#### 2. 暗号化されたデータを Keychain に保存する際の属性について  

本要件では、  
データは OS のセキュアな仕組みで保存したい  
バックアップ（iCloud / iTunes）は不可  
というセキュリティ要件がある。  

この前提において、  
暗号化済みデータを Keychain に保存する際の属性として  
以下を採用するのが適切。  

推奨属性  
kSecAttrAccessibleWhenUnlockedThisDeviceOnly  

理由  
・端末アンロック時のみアクセス可能  
・データは 当該端末にのみ紐づく  
・バックアップ対象外（iCloud / 端末移行不可）  
・実機・エミュレータ・XCTest すべてで動作可能  
※ この属性は 「暗号化されたデータの保存」 に対する設定であり、  
Secure Enclave 鍵の利用可否とは独立している。  

#### 3. 本番環境における Secure Enclave / Keychain 設定  

保存対象  
・Secure Enclave 内に生成された秘密鍵  
・鍵素材は Secure Enclave 外に出ず、Keychain には参照情報のみが保存される  

採用設定  
・Keychain 属性  
　kSecAttrAccessibleWhenUnlockedThisDeviceOnly  
・Access Control（ACL）  
　.privateKeyUsage  

理由  
・端末アンロック時のみ利用可能  
・端末に紐づき、バックアップ不可  
・鍵のエクスポート不可、暗号化処理にのみ使用可能  

補足（不採用とした設定）  
・kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly  
　→ パスコード未設定端末で利用不可となるため不採用  
・生体認証（biometryCurrentSet 等）  
　→ 要件外かつ XCTest / 回帰テストで成立しないため不採用  

#### 4. 回帰テスト（XCTest）を踏まえた方針
前提  
・XCTest 環境では Secure Enclave が利用できない  

方針  
・XCTest 実行時は Secure Enclave を使用しない  
・暗号化処理は回避し、データはそのまま Keychain に保存する  
・Keychain の保存属性は 本番と同一 とする  

目的  
回帰テストでは以下にフォーカスする  
・保存／読み出しの処理フロー  
・Keychain 利用の成否    
・Secure Enclave による暗号化・耐タンパ性の担保は  
　実機・本番環境でのみ確認対象とする  



=====================================================================

[課題]  
Secure Enclave は実機専用のため、エミュレータでは使用できません。  
Keychain は利用可能ですが、本番想定の厳格な属性・ACL を設定すると  
エミュレータの前提条件を満たせず失敗します。  

そのため、本番設定を緩めるのではなく、  
回帰テスト用途に限定した設定切り替え、  
もしくは実機での検証を併用する設計が妥当と考えています。  


【背景 / 現状】  
現在、エミュレータ（iOS Simulator）上では  
Secure Enclave および Keychain を利用した処理が正常に動作していない。  

これは実装不備ではなく、iOS のプラットフォーム仕様および  
Keychain の属性設定に起因する挙動である。  
  
---

【Secure Enclave について】  
Secure Enclave は実機 SoC 内に存在する物理ハードウェアであり、  
iOS Simulator には存在しない。  

そのため  
・Secure Enclave を指定した鍵生成  
・Secure Enclave による暗号化／復号  
はエミュレータでは実行できない。  

この挙動は iOS の仕様であり、回避手段はない。  

---

【Keychain について】  
Keychain 自体は OS が提供する論理的なセキュアストレージであり、  
エミュレータでも利用可能である。  

ただし、Keychain は属性（kSecAttrAccessible）および  
Access Control（ACL）の設定内容によって  
アクセス条件が厳密に制御される。  

本番想定の設定では以下の制約が存在する：  
・パスコード必須  
・端末限定（バックアップ不可）  
・Secure Enclave 前提  
・生体認証（ACL）  

これらの条件はエミュレータ環境では満たせないため、  
Keychain API 自体は存在していても、操作が失敗する。  

---

【なぜ設定を緩くすると動くのか】  
Keychain の属性を緩める（例：WhenUnlocked）ことで、  
・パスコード不要  
・バックアップ可  
・認証不要  
となり、エミュレータでも利用可能になる。  

ただし、これは本番環境のセキュリティ要件から逸脱するため、  
本番用途での採用は不適切である。  

---

【回帰テストに関する考慮】  
回帰テスト（エミュレータ）の目的は  
「セキュリティ強度の検証」ではなく、  
「処理フローおよび機能の正しさの検証」である。  

そのため、回帰テスト環境では以下を許容する余地がある：  
・Secure Enclave を使用しない  
・Keychain の属性をテスト用途に限定して緩和する  

一方で、  
・Secure Enclave の利用可否  
・生体認証／パスコード連携  
・ハードウェアバックドの保証  
については、実機での検証を別途行う必要がある。  

---

【対応方針検討案】  
・本番（実機）：  
  Secure Enclave + 厳格な Keychain 属性／ACL を使用  
・回帰テスト（エミュレータ）：  
  Secure Enclave 非使用  
  Keychain はテスト用途に限定した緩和設定を使用  
・本番設定をエミュレータ都合で緩めることはしない  

---

【補足：Keychain 属性（kSecAttrAccessible）について】  
Keychain には「いつ・どの状態でアクセス可能か」を制御する  
属性（kSecAttrAccessible）が存在する。  

代表的なもの：  
・kSecAttrAccessibleWhenUnlocked  
→・端末がロック解除されている間のみアクセス可能  
 ・iCloud / iTunes バックアップの対象になる  
 ・端末移行時に復元される  
 ・エミュレータでも利用可能  

・kSecAttrAccessibleAfterFirstUnlock  
→・端末起動後、最初にロック解除されて以降は常にアクセス可能  
 ・バックグラウンド実行時でもアクセスできる  
 ・iCloud / iTunes バックアップの対象になる  
 ・端末移行時に復元される  
 ・エミュレータでも利用可能  

・kSecAttrAccessibleWhenUnlockedThisDeviceOnly  
→・端末がロック解除されている間のみアクセス可能  
 ・この端末にのみ保存され、バックアップ対象外  
 ・端末移行時には復元されない  
 ・エミュレータでも利用可能  

kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly  
→・端末にパスコードが設定されている場合のみ使用可能  
 ・端末がロック解除されている間のみアクセス可能  
 ・この端末にのみ保存され、バックアップ対象外  
 ・Secure Enclave と連携して利用されることが多い
 ・エミュレータでは利用不可  

属性によって以下が制御される：  
・端末ロック状態  
・再起動後の可否  
・バックアップ可否  
・エミュレータでの利用可否  

---

【公式ドキュメント】  
・Keychain Services  
  https://developer.apple.com/documentation/security/keychain_services  

・Keychain Item Accessibility Constants  
  https://developer.apple.com/documentation/security/keychain_item_accessibility_constants  

・Secure Enclave  
  https://developer.apple.com/documentation/security/secure_enclave  










===========================================================================

#### **【課題】**

現在の実装では、端末が StrongBox 対応の場合のみ
setIsStrongBoxBacked(true) を指定して鍵生成を行っている。  
一方で、StrongBox 非対応端末における鍵の保存先
（TEE / Software Keystore）については
明示的な設計および検証が行われていない。

また、鍵生成後に保存先を確認・制御する仕組みがなく、
実行環境や端末差分によって
Software Keystore が使用される可能性がある。

#### **【問題点】**
**1. StrongBox 非対応時の挙動が未定義**

setIsStrongBoxBacked(true) を指定しない場合、
鍵の保存先は Android に委ねられるため、
TEE（ハードウェアバックド）または
Software Keystore のいずれになるか保証されない。

**2. Software Keystore 利用の検知・制御ができていない**

エミュレータや一部端末では
Software Keystore が利用される可能性があるが、
現状の実装ではこれを検知・拒否できない。

その結果、本番要件を満たさない状態でも
処理が継続してしまうリスクがある。
※回帰テスト時ではSoftware Keystoreが使われる想定

#### **【対策方針】**

**基本方針**  
・StrongBox / TEE / Software Keystore を明示的に区別する  
・鍵生成後に KeyInfo による保存先検証を必須化する  
・Software Keystore の利用可否を実行環境に応じて明確に定義する  

**利用可否の定義**  
・StrongBox：利用可  
・TEE（ハードウェアバックド）：利用可  
・Software Keystore：本番では利用不可。回帰テスト用途に限り許可  

**Software Keystore 許可条件**  
Software Keystore の利用は
以下の条件をすべて満たす場合に限定する。  
・debug ビルド  
・エミュレータ環境（回帰テスト用途）  

debug 実機および release 実機では
StrongBox または TEE を必須とする。

#### **【回帰テスト（エミュレータ）に関する考慮】**

エミュレータでは StrongBox および TEE が利用できず、  
必ず Software Keystore が使用される。  

そのため、回帰テスト環境では  
Software Keystore を明示的に許可し、  
以下の観点に限定してテストを行う。  
・暗号化／復号処理の正しさ  
・鍵生成および保存／読み出しの機能確認  

StrongBox / TEE の利用可否については実機端末を用いた検証を別途実施する。  
