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
