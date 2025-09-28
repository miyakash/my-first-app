AndroidTest + Jacoco レポート取得手順（まとめ）
1. 前提

Windows PC

Android Studio プロジェクト（Kotlin + Compose）

エミュレータ or 実機が利用可能

Gradle Kotlin DSL (build.gradle.kts) + Version Catalog (libs.〇〇) 使用

2. GradleにJacocoを追加
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

android {
    buildTypes {
        debug {
            isTestCoverageEnabled = true // AndroidTestカバレッジ有効化
        }
    }
}


debug ビルドで必ず isTestCoverageEnabled = true を設定

3. Jacocoレポートタスクの設定

build.gradle.kts の最後に追加：

tasks.register<JacocoReport>("jacocoAndroidTestReport") {
    group = "Reporting"
    description = "Generate Jacoco coverage report for AndroidTest"

    dependsOn("connectedDebugAndroidTest") // AndroidTest実行後にレポート生成

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(file("${buildDir}/reports/jacoco/androidTest"))
    }

    val javaClasses = fileTree("${buildDir}/intermediates/javac/debug/classes") {
        exclude(
            "**/R.class",
            "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*"
        )
    }

    val kotlinClasses = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
        exclude("**/*Test*.*")
    }

    classDirectories.setFrom(files(javaClasses, kotlinClasses))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

    executionData.setFrom(
        fileTree("${buildDir}/outputs/code_coverage/debugAndroidTest/connected") {
            include("**/*.ec")
        }
    )
}


ポイント：executionData を fileTree で指定すると、AVD名にスペースや括弧があっても正しく読み込まれる

classDirectories に Java + Kotlin クラスを両方含める

4. エミュレータ確認
1) AVDの場所確認
C:\Users\<ユーザー名>\.android\avd\

2) エミュリスト確認
cd %ANDROID_SDK_ROOT%\emulator
emulator -list-avds

3) エミュ起動
emulator -avd Pixel_6


PATH に SDK の emulator と platform-tools を追加しておくと便利

5. コマンドでテスト → レポート生成
cd C:\Users\81808\AndroidStudioProjects\DigitalKey

# AndroidTest実行（カバレッジ生成）
.\gradlew connectedDebugAndroidTest

# Jacocoレポート生成
.\gradlew jacocoAndroidTestReport


HTMLレポート：

app/build/reports/jacoco/androidTest/index.html


XMLレポート（CI/CD用）：

app/build/reports/jacoco/androidTest/report.xml

6. 注意点

エミュレータ/実機が起動していないと connectedDebugAndroidTest は失敗

.ec ファイルが生成されていれば、レポートは自動生成される

UnitTestカバレッジは別タスクが必要（今回は不要）

💡 これで AndroidTestの実行 → Jacocoレポート生成 が一連で完了する状態になります。


＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝
【修正したファイルをステージに挙げる方法】
git add 修正したファイル名

全部まとめてやるなら
git add .

【ステージに上げたやつを確認する方法】
git status
→赤字は未ステージ。緑はステージ済み。

差分を見るなら
git diff --cached

【ステージをリセットする方法】
git reset ファイル名

全部まとめて
git reset

【ファイルの変更自体をリセットするには】
git restore ファイル名

【普段のフローでは】
git add . → git commit -m "メッセージ" → git push で十分です

＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝

ざっくり流れ（初心者向け）

Android（Ubuntu runner）

Android SDK をセットアップ（android-actions/setup-android）。

AVD（エミュレータ）を作成・起動（ReactiveCircus/android-emulator-runner を利用）。

connectedDebugAndroidTest を実行して UI テストを走らせる。

Jacoco 用の gradle タスクで coverage ファイル（.exec / .ec）を集めて HTML/XML レポートを生成する。
（testCoverageEnabled true などの設定が必要）｡ 
GitHub
+1

iOS（macOS runner）

macOS ランナーで xcodebuild test を -enableCodeCoverage YES で実行してカバレッジデータを生成。

slather を使って HTML / Cobertura などのレポートに変換して保存。スキーム側で「Gather coverage data」を有効にする必要あり。 
GitHub
+1

事前準備チェックリスト（必ず確認）

Android

app/build.gradle に testCoverageEnabled（または対応する新しい設定） を入れておく。※ AGP バージョンで設定名が変わる場合あり（トラブル時は AGP ドキュメント参照）。 
Google Issue Tracker

UI テスト（Espresso 等）がローカルで通っていること（CI での再現性のため）。

エミュレータは x86_64 + google_apis が CI では安定しやすい。

iOS

GitHub の macOS ランナーを使用（runs-on: macos-latest）。

テスト実行するスキームで「Gather coverage data（コードカバレッジ取得）」が ON になっていること。 
GitHub

CocoaPods を使っているなら pod install を workflow に入れる。

app/build.gradle に入れる（例）

これは Groovy DSL の例。app モジュールに置いてください。プロジェクトによってパスやクラスディレクトリが変わるので必要に応じ調整を。

plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'jacoco'
}

android {
    compileSdkVersion 33

    defaultConfig {
        applicationId "com.example.app"
        minSdkVersion 21
        targetSdkVersion 33
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // UI (connectedAndroidTest) 用に coverage データを出す設定
            testCoverageEnabled true
        }
    }
}

// Jacoco レポートを connectedAndroidTest の実行後にまとめるタスクの例
// (AGP/Gradle のバージョンによりパスが変わるのでエラー時はパスを調整)
tasks.register("jacocoAndroidTestReport", JacocoReport) {
    dependsOn "connectedDebugAndroidTest"

    reports {
        xml.required = true
        html.required = true
    }

    def fileFilter = [
        '**/R.class', '**/R$*.class', '**/BuildConfig.*', '**/Manifest*.*',
        '**/*Test*.*', 'android/**/*.*'
    ]

    def debugTree = fileTree(dir: "$buildDir/intermediates/javac/debug", excludes: fileFilter)
    def kotlinDebugTree = fileTree(dir: "$buildDir/tmp/kotlin-classes/debug", excludes: fileFilter)

    sourceDirectories.setFrom(files(["src/main/java", "src/main/kotlin"]))
    classDirectories.setFrom(files([debugTree, kotlinDebugTree]))
    executionData.setFrom(fileTree(dir: buildDir, includes: [
        "outputs/code_coverage/debugAndroidTest/connected/*coverage.ec",
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
    ]))
}


補足：AGP のバージョンで testCoverageEnabled の扱いや Jacoco の設定方法が変わることがあります（うまく出力されない場合は AGP のリリースノートや Issue を確認してください）。 
Google Issue Tracker

コメント入り：GitHub Actions（.github/workflows/ci.yml）サンプル

以下は Android と iOS を同じワークフロー内に並列ジョブで実行するサンプルです。ワークフロー内にたっぷりコメントを入れてあるので、初心者の方でも読みやすいはずです。

name: CI - Android (UI tests + JaCoCo) & iOS (Unit tests + Slather)

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  # -----------------------
  # Android: UI tests + JaCoCo
  # -----------------------
  android-ui-test:
    name: Android UI tests + JaCoCo
    runs-on: ubuntu-latest
    steps:
      # リポジトリを取得
      - name: Checkout repo
        uses: actions/checkout@v4

      # JDK をセット（Android Gradle Plugin の要件に合わせる）
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      # Android SDK をセットアップ（platform-tools, build-tools 等を導入）
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
        with:
          # 必要なら packages を指定して特定の platform/build-tools/system-image を入れる
          packages: |
            platform-tools
            platforms;android-33
            build-tools;33.0.2
            system-images;android-33;google_apis;x86_64

      # エミュレータを作成して起動 → スクリプトで gradle 実行
      - name: Run Android Emulator and connected tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 33                # エミュレータのAPIレベル
          arch: x86_64                 # CIでは x86_64 が高速で安定
          target: google_apis          # google_api イメージを推奨
          profile: pixel_6            # AVD プロファイル名（任意）
          disable-animations: true     # UIテストの安定化に有効
          # 実行するコマンド。connectedDebugAndroidTest を走らせ、jacoco 用タスクでレポート生成
          script: ./gradlew connectedDebugAndroidTest jacocoAndroidTestReport --no-daemon

      # Jacoco レポートを artifacts として保存（HTML レポート）
      - name: Upload Android coverage report
        uses: actions/upload-artifact@v4
        with:
          name: android-coverage-report
          path: app/build/reports/jacoco/jacocoAndroidTestReport/html

  # -----------------------
  # iOS: Unit tests + Slather
  # -----------------------
  ios-test:
    name: iOS Unit tests + Slather
    runs-on: macos-latest
    steps:
      - name: Checkout repo
        uses: actions/checkout@v4

      # Ruby (Bundler) をセットアップ（slather は gem）
      - name: Set up Ruby
        uses: ruby/setup-ruby@v1
        with:
          ruby-version: 3.2

      # (任意) CocoaPods を使う場合は pod install
      - name: Install CocoaPods (if using CocoaPods)
        if: -f Podfile
        run: |
          gem install bundler
          bundle install --path vendor/bundle
          bundle exec pod install

      # Run xcodebuild tests with coverage enabled
      - name: Run iOS unit tests (xcodebuild) with coverage
        run: |
          # ワークスペース or プロジェクト、スキーム名は自分のプロジェクトに合わせて変更
          xcodebuild \
            -workspace MyApp.xcworkspace \
            -scheme "MyApp" \
            -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
            -enableCodeCoverage YES \
            test

      # Slather でカバレッジレポート生成
      - name: Install Slather and run coverage
        run: |
          gem install slather
          # slather 実行例（workspace を使っている場合）
          bundle exec slather coverage --scheme "MyApp" --workspace "MyApp.xcworkspace" MyApp.xcodeproj || slather coverage --scheme "MyApp" --workspace "MyApp.xcworkspace" MyApp.xcodeproj

      # Slather 出力（適宜出力先を指定したらアップロード）
      - name: Upload iOS coverage report
        uses: actions/upload-artifact@v4
        with:
          name: ios-coverage-report
          path: slather-report || cobertura.xml || ./coverage # slather の出力先に合わせて

iOS：.slather.yml の簡単な例（リポジトリルート）
# .slather.yml
coverage_service: cobertura
workspace: MyApp.xcworkspace
project: MyApp.xcodeproj
scheme: MyApp
output_directory: slather-report


Slather の基本的な使い方・フラグについては公式リポジトリやドキュメント参照を。 
GitHub
+1

トラブルシューティング（よくあるハマりどころ）

エミュレータが起動しない／テストが hang する

arch: x86_64、disable-animations: true、google_apis を試す。起動待ち（adb wait-for-device）やタイムアウトを明示することも。 
GitHub

Jacoco の coverage データが生成されない

testCoverageEnabled true の忘れ、または AGP のバージョン差による設定名の変化に注意。出力先パスが Gradle/AGP のバージョンで変わるのでパスを確認。 
Google Issue Tracker

Slather が coverage を拾わない

Xcode のスキームで「Gather coverage data」が ON になっているか確認。テストが成功して DerivedData に coverage データ（xcresult/xccov）が生成されていることが前提。 
GitHub

ローカルでの確認手順（CI に入れる前に）

Android：./gradlew connectedDebugAndroidTest jacocoAndroidTestReport をローカルで一度走らせて、app/build/reports/jacoco/... が作られるか確認。

iOS：Xcode 上でスキームの「Gather coverage data」を ON にしてから xcodebuild test -scheme MyApp -enableCodeCoverage YES ... を実行、slather coverage ... を実行してレポートが生成されるか確認。

参考（重要な外部リソース）

ReactiveCircus の Android emulator runner（エミュレータ起動用アクション）。このアクションを使うとエミュレータ作成 → 起動 → スクリプト実行が簡単です。 
GitHub

android-actions/setup-android（Android SDK をセットする公式アクション）。 
GitHub

JaCoCo / AGP 関連の注意（testCoverageEnabled の扱いなど、AGP のバージョンにより差があるため問題発生時は該当 Issue を参照）。 
Google Issue Tracker

Slather（Xcode のカバレッジをパースしてレポートを作るツール）公式 repo。スキーム側での設定や使い方がまとまっています。 
GitHub
+1

https://chatgpt.com/c/68d9358d-dd0c-8323-b79e-1986934e8f43
