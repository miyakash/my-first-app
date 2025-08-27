ios(swift)でのテスト実施方法とカバレッジ取得方法の検討

xcode標準機能ではC0の網羅率までしか取得できないので
ツールを使用したC1の取得する方法について調査した内容を整理。

#### 【SwiftにおけるC1（分岐網羅）カバレッジ自動測定に関する調査】

**1. 結論**  
・Swift現行のカバレッジ取得ツール（Xcode標準、Slather、xcov等）はC0のみ対応し、C1の自動測定・可視化は不可能。  
・実務上は、C0カバレッジをベースにテストケース設計を行い、分岐ごとの網羅は手動でマッピングしてエビデンス化するのが現実的。  
・推奨構成：  
　テストフレームワーク: XCTest  
　カバレッジ取得方法: Xcode標準（C0） + Slather/xcov（可視化）   
　C1対応: 自動測定不可のため、テストケース設計で論理的に担保。  
  
**2. C1自動測定ができない理由**  
**(1) LLVM(コンパイラ基盤)の仕組み上は対応しているが、Swiftでは未対応。**  
LLVMの カバレッジツールであるllvm-cov は分岐カバレッジをサポートするが、Swiftは分岐情報を含むカバレッジマップを生成しない
また、C/C++向けにはLLVM IRに分岐カウンタが挿入されるが、Swiftは言語仕様の影響でInstrumentationが限定的でC1情報が欠落する。
→SwiftコンパイラでのInstrumentation不足  
https://fortee.jp/iosdc-japan-2019/proposal/762f9e85-d71c-41e8-a891-d60d0129a355  

**(2) 実務報告でも確認されている**  
Swift公式GitHubでも「Swiftはブランチカバレッジを生成しないため」と明言され  
AppleのDeveloper Forumsでも「SwiftのUTカバレッジはブランチを含まない」との記載あり。  
https://github.com/swiftlang/swift/issues/81730  
https://developer.apple.com/forums/thread/785320  

 **3. Swift標準および周辺ツールでのカバレッジ取得**  
・Xcode標準機能: 行単位（C0）カバレッジを取得可能。ブランチ情報は含まれない。  
   https://blogs.halodoc.io/ios-code-coverage/  
・Slather / xcov: Xcodeのカバレッジデータを解析し、HTMLやCI用レポートを生成。  
　ブランチカバレッジ（C1）は出力されず、行カバレッジのみ可視化。  
   https://github.com/fastlane-community/xcov  
   https://qiita.com/uhooi/items/e1e464777d2163286c59  
