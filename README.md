了解です 👍
Swift / iOS プロジェクトに Slather を導入する手順を整理しますね。

1. Slatherとは

Xcode のカバレッジ情報（xccov や gcov のレポート）を読み取り、HTML や Cobertura 形式などに出力できるツール

Jenkins, GitLab CI, GitHub Actions など CI と組み合わせやすい

2. 導入手順
① Gem のインストール

Slather は RubyGem なので、まずインストールします。

# bundler を使う場合（推奨）
gem install bundler
echo 'gem "slather"' >> Gemfile
bundle install

# 直接インストールする場合
sudo gem install slather

② Xcodeプロジェクトの設定

Slather が正しくカバレッジを取得できるように、以下を設定してください。

カバレッジを有効化

Xcode → Scheme → Test → Gather coverage にチェックを入れる

もしくはコマンドラインで有効化:

xcodebuild -scheme MyApp -enableCodeCoverage YES test


Debug ビルド設定で最適化を切る（任意）

Build Settings > Optimization Level を None (-Onone) にするとカバレッジが正確になりやすい

③ Slather 設定ファイルを作成

プロジェクトルートに .slather.yml を置きます。

例:

coverage_service: html
xcodeproj: MyApp.xcodeproj
scheme: MyApp
output_directory: slather-report
ignore:
  - Pods/*
  - MyAppTests/*


coverage_service → 出力形式 (html, cobertura_xml, simple_output など)

xcodeproj → 対象の Xcode プロジェクト

scheme → テスト実行対象のスキーム

ignore → カバレッジ対象外にするパス

④ テスト実行 + カバレッジ生成

テストを実行してカバレッジを計測します。

# カバレッジ付きでテスト
xcodebuild -scheme MyApp -enableCodeCoverage YES -destination 'platform=iOS Simulator,name=iPhone 15' test

# Slatherでレポート生成
slather

⑤ 結果の確認

slather-report/ 以下に HTML が生成されるのでブラウザで開けばOK

CIで使う場合は XML 形式にしてレポートを収集することも可能

3. よくあるトラブル

カバレッジがゼロになる
→ Scheme の「Gather coverage」が有効か確認

テスト対象のモジュールがカバレッジに出ない
→ Build Settings > Defines Module を YES にする

CI環境で Simulator が見つからない
→ xcodebuild -destination を指定する

👉 ここまでで、基本的な Slather 導入～レポート生成の流れ ができます。

ご質問ですが、
あなたの環境は ローカルMacで手動実行 ですか？ それとも GitHub Actions / Jenkins / GitLab CI などCI環境 で回したい感じですか？

あなた:
2. 導入手順
① Gem のインストール

Slather は RubyGem なので、まずインストールします。

# bundler を使う場合（推奨）
gem install bundler
echo 'gem "slather"' >> Gemfile
bundle install

# 直接インストールする場合
sudo gem install slather

これが上手くいかない。
ChatGPT:

なるほど、インストールでつまづいてるんですね。
Slather は RubyGem なので、Ruby の環境が整っていないと失敗しやすいです。
Mac 環境の場合によくある原因と対処を整理します。

✅ よくある原因と解決法
1. Ruby のバージョンが古い

macOS 標準の Ruby は古いことが多く、Slather が依存する gem が入らない場合があります。

確認:

ruby -v


推奨: 2.7 以上（できれば 3.x 系）

👉 解決策: rbenv や rvm で新しい Ruby をインストールする

例 (rbenv):

brew install rbenv
rbenv install 3.2.4
rbenv global 3.2.4
ruby -v

2. 権限エラー (Permission denied / Operation not permitted)

sudo gem install が失敗するのは SIP (System Integrity Protection) が原因のこともあります。

対処法:

--user-install オプションを付ける

gem install slather --user-install


PATH に ~/.gem/ruby/X.X.X/bin を追加する

3. OpenSSL / ffi / nokogiri など依存ライブラリのエラー

ネイティブ拡張を含む gem のビルドで失敗することがあります。

対処法:

brew install openssl
bundle config --local build.nokogiri --use-system-libraries
bundle install

4. Bundler で管理していない

Gemfile を用意して bundler 管理する方が安定します。

# Gemfile
source "https://rubygems.org"

gem "slather"


実行:

bundle install
bundle exec slather --version

🎯 まず試して欲しいコマンド

（Ruby 3.x が入っている前提で）

gem install slather --user-install
slather --version


もし slather コマンドが見つからなければ：

echo 'export PATH="$HOME/.gem/ruby/$(ruby -e "puts RUBY_VERSION[/\d+\.\d+\.\d+/]")/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc


👉 ここで確認したいのですが、
エラーは「インストールできない（gem install が失敗）」のか、
それとも「インストール後に slather コマンドが見つからない」のか、
どちらで止まっていますか？
