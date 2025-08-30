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
