# Sakugapp

当前开发：1.2.0 / code 11，分支 `feature/history-tags-refresh`，不替换已公开 v1.1.0。

本轮增量保持版本号不变：下载文件名识别明确集数，组合搜索可载入历史草稿，搜索结果返回保留原列表，高级播放控制按功能分组。[改动及验证范围](docs/incremental-search-download-controls.md)。

本轮加入安全下载命名、Posts 下拉刷新、法/西/俄/韩界面、六列、本地浏览历史、独立标签收藏及标签备注。[功能、迁移与限制](docs/development-1.2.0.md) · [设备与构建证据](https://github.com/Milenal82/sakugapp/blob/evidence/history-tags-refresh/report.json) · [真实截图](https://github.com/Milenal82/sakugapp/tree/evidence/history-tags-refresh/screenshots)。新译文未经母语审校；候选测试 APK 见报告链接的 Actions 构件。

应用代码已通过 Debug/Release 各 107 项单测与 33 项设备测试。新页面的原始模拟器截图（本地页面使用合法捕获的公开样本）：

<p>
  <img src="https://raw.githubusercontent.com/Milenal82/sakugapp/evidence/history-tags-refresh/screenshots/history-page.png" width="220" alt="浏览历史">
  <img src="https://raw.githubusercontent.com/Milenal82/sakugapp/evidence/history-tags-refresh/screenshots/tag-favorites-page.png" width="220" alt="标签收藏与备注">
</p>


[繁體中文](#繁體中文) | [日本語](#日本語) | [English](#english)

---

## 繁體中文

# Sakugapp

Sakuga Mobile（安裝名稱 Sakugapp）是瀏覽 Sakugabooru 的非官方 Android 客戶端，使用 Kotlin、Jetpack Compose 和 Media3 建構原生介面。與 Sakugabooru 及 Sakuga Extended 無隸屬關係；媒體和標籤來自原網站。

支援 Android 8.0 以上。介面提供簡體中文、繁體中文、英語和日語；官網頁面使用其支援的英語或日語。

### 主要功能

**瀏覽與搜尋**：Post 連續載入、按 ID 去重、返回保留位置；向下捲動隱藏搜尋區，按需展開輸入、組合條件和歷史。支援原站標籤語法、AND / OR / 排除、排序和 Score 快捷條件，標籤聯想與最多 50 條搜尋歷史。平均色工具計算可見縮圖的均色和 HEX，並非服務端顏色相似搜尋。

**帖子與版面**：手機提供 1、2、3、4 欄，平板另有 8、12 欄。長方形縮圖保留比例，顯示已取得的時長和真實 Score；版權標籤為黃色，動畫師標籤為紫色。詳情頁的上一帖、下一帖、下載、原帖、收藏五個圖示同排；Post ID、來源、Score 等中繼資料緊湊換行，保留分類標籤和評論。

**播放器**：播放、拖動、靜音、音量、0.1×–3× 倍速、全螢幕旋轉、A–B 循環；雙指縮放、雙擊暫停、左右切換目前列表中的帖子。進階控制可折疊，提供畫面調整、鏡像和截圖分享。嚴格幀分析按實際解碼索引步進並顯示幀號，不用固定毫秒跳轉代替逐幀。

**收藏夾**：本地多收藏夾、詳情頁快速收藏和取消收藏、封面與排序、批次管理、JSON 匯入匯出。首頁只顯示收藏夾，進入後使用相同的 Post 網格；可下載選定媒體供離線瀏覽。

**外觀**：系統 / 深色 / 淺色主題，統一的 Post 浮塊、圓角、按鈕和展開動畫。搜尋與收藏管理預設收起次要操作，減少常駐控制項。

**帳戶與社群**：帳戶登入、Vote、評論和 Blog / Forum 入口沿用已有實作。需要帳戶的操作以官網回應為準；本次發布沒有自動執行真實帳戶的投票或評論寫入。

### 截圖

以下為實際 Android 模擬器截圖；來源及隔離測試說明見「截圖記錄」。

帖子詳情 簡體中文設定

### 下載與安裝

從 GitHub Releases 下載正式簽名 APK；v1.1.0 對應 APK、完整原始碼和 SHA-256 校驗檔。允許下載所用瀏覽器安裝未知來源應用後，開啟 APK 安裝。

包名保持 `com.sakugapp`。v1.1.0 使用 versionCode 10，高於 v1.0.0 及此前開發候選版。相同正式證書簽名的版本直接覆蓋安裝，不要卸載；建議先匯出收藏 JSON。收藏庫、設定鍵和搜尋歷史沿用，資料庫升級不清空收藏資料。

Debug 版與正式版證書不同，不能相互覆蓋。切換前匯出收藏 JSON，再卸載 Debug、安裝正式版並匯入。JSON 不包含帳號、設定、搜尋歷史或下載的媒體，卸載會清除這些本地資料。

### 建置與發布

需要 JDK 17、Android SDK 35 / Build Tools 35.0.0。倉庫包含 Gradle 8.13 Wrapper，使用 AGP 8.9.2、Kotlin 2.1.20 和 Compose BOM 2025.04.01；最低 API 26，目標 API 35。

```bash
python3 scripts/check-localization.py
python3 -m unittest discover -s scripts/tests -v
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。連接支援的裝置後，可執行 `./scripts/run-device-checks.sh` 執行裝置測試並匯出截圖。

正式簽名透過受保護的 GitHub Actions Secrets 完成，設定見「簽名說明」。已設定簽名環境時執行：

```bash
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

Release APK：`app/build/outputs/apk/release/app-release.apk`。缺少簽名設定時正式建置會失敗；`-PreleaseUnsignedCheck=true` 僅用於不可發布的未簽名預檢。發布建置先上傳草稿，驗證後再公開，既有 Release 不被覆蓋。

### 限制與驗證

A–B 循環按時間戳執行，存在播放器與解碼延遲。嚴格幀分析需 Android 9（API 28）以上、支援索引解碼的裝置和格式，單檔最多 200 MiB、畫面不超過 3840×2160；無法可靠對應目前播放時間時從第 1 幀開始，並提示限制。未知時長顯示「—」。

離線批次下載最多 20 個/批、200 MiB/檔、1 GiB 總額，下載期間需保持頁面開啟。站點存取失敗顯示錯誤或空狀態，不以虛構帖子填充。真機、其他 Android 版本和解碼器的表現仍需測試。

本版建置、自動化測試與升級驗證見 v1.1.0 發布檢查；介面與擴充調查見「資料來源」和「功能對照」。

### 授權

專案採用 GPL-3.0-only。每個正式版本同時提供對應提交的完整原始碼 ZIP；APK 內含開源授權和第三方聲明。參考專案為 Sakuga Extended，未複製其網頁腳本、樣式或圖片；依賴聲明見 `THIRD_PARTY_NOTICES.md`。

---

## 日本語

# Sakugapp

Sakuga Mobile（インストール名 Sakugapp）は、Sakugabooru を閲覧するための非公式 Android クライアントです。Kotlin、Jetpack Compose、Media3 でネイティブ UI を構築しています。Sakugabooru および Sakuga Extended とは提携していません。メディアとタグは元サイトから取得します。

Android 8.0 以上に対応。UI は簡体字中国語、繁体字中国語、英語、日本語を提供します。公式ページは英語または日本語を使用します。

### 主な機能

**閲覧と検索**：Post の連続読み込み、ID による重複排除、戻る時の位置保持。下スクロールで検索領域を隠し、必要に応じて入力・複合条件・履歴を展開。元サイトのタグ構文、AND / OR / 除外、並び替え、Score クイック条件、タグ候補、最大 50 件の検索履歴に対応。平均色ツールは表示中のサムネイルの平均色と HEX を計算するもので、サーバー側の色類似検索ではありません。

**投稿とレイアウト**：スマートフォンは 1 / 2 / 3 / 4 列、タブレットはさらに 8 / 12 列。長方形サムネイルは比率を保持し、取得できた再生時間と実際の Score を表示。著作権タグは黄色、アニメータータグは紫。詳細ページでは前の投稿、次の投稿、ダウンロード、元投稿、お気に入りの 5 アイコンを同じ行に配置。Post ID、出典、Score などのメタデータはコンパクトに折り返し、カテゴリタグとコメントを保持します。

**プレーヤー**：再生、シーク、ミュート、音量、0.1×–3× 速度、全画面回転、A–B リピート。ピンチズーム、ダブルタップ一時停止、左右で現在のリスト内の投稿に切り替え。詳細コントロールは折りたたみ可能で、画面調整、ミラー、スクリーンショット共有を提供。厳密フレーム解析は実際のデコードインデックスでステップしフレーム番号を表示。固定ミリ秒ジャンプでフレーム単位を代替しません。

**お気に入り**：ローカル複数フォルダ、詳細ページからのクイック登録/解除、カバーと並び替え、一括管理、JSON インポート/エクスポート。ホームはフォルダのみ表示し、開くと同じ Post グリッド。選択メディアをダウンロードしてオフライン閲覧可能。

**外観**：システム / ダーク / ライトテーマ、統一された Post フローティングカード、角丸、ボタン、展開アニメーション。検索とお気に入り管理は二次操作を既定で折りたたみ、常設コントロールを削減。

**アカウントとコミュニティ**：アカウントログイン、Vote、コメント、Blog / Forum 入口は既存実装を踏襲。アカウントが必要な操作は公式サイトの応答に従います。今回のリリースでは実アカウントでの投票・コメント書き込みを自動実行していません。

### スクリーンショット

以下は実際の Android エミュレーターのスクリーンショット。出典と分離テストの説明は「スクリーンショット記録」を参照。

投稿詳細 簡体字中国語設定

### ダウンロードとインストール

GitHub Releases から正式署名 APK をダウンロード。v1.1.0 は APK、完全ソース、SHA-256 チェックファイルに対応。ブラウザに未知の提供元アプリのインストールを許可し、APK を開いてインストールします。

パッケージ名は `com.sakugapp` のまま。v1.1.0 は versionCode 10 で、v1.0.0 および以前の開発候補版より大きい。同じ正式証明書で署名された版は上書きインストールし、アンインストールしないでください。先にお気に入り JSON をエクスポートすることを推奨。お気に入りライブラリ、設定キー、検索履歴は引き継がれ、DB アップグレードでお気に入りデータは消去されません。

Debug 版と正式版は証明書が異なり相互上書き不可。切り替え前に お気に入り JSON をエクスポートし、Debug をアンインストール、正式版をインストールしてインポート。JSON にはアカウント、設定、検索履歴、ダウンロードしたメディアは含まれず、アンインストールでこれらのローカルデータは消去されます。

### ビルドとリリース

JDK 17、Android SDK 35 / Build Tools 35.0.0 が必要。リポジトリには Gradle 8.13 Wrapper が含まれ、AGP 8.9.2、Kotlin 2.1.20、Compose BOM 2025.04.01 を使用。min API 26、target API 35。

```bash
python3 scripts/check-localization.py
python3 -m unittest discover -s scripts/tests -v
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。対応デバイス接続後、`./scripts/run-device-checks.sh` でデバイステストとスクリーンショット出力を実行できます。

正式署名は保護された GitHub Actions Secrets で行います。設定は「署名説明」を参照。署名環境設定済みの場合：

```bash
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

Release APK：`app/build/outputs/apk/release/app-release.apk`。署名設定がない場合、正式ビルドは失敗します。`-PreleaseUnsignedCheck=true` は公開不可の未署名プリチェック専用。リリースビルドはまず下書きをアップロードし、検証後に公開。既存 Release は上書きされません。

### 制限と検証

A–B リピートはタイムスタンプで実行され、プレーヤーとデコードの遅延があります。厳密フレーム解析は Android 9（API 28）以上、インデックスデコード対応のデバイスと形式が必要。単一ファイル最大 200 MiB、画面最大 3840×2160。現在の再生時間に確実に対応できない場合は第 1 フレームから開始し、制限を表示。未知の再生時間は「—」と表示。

オフライン一括ダウンロードは最大 20 件/バッチ、200 MiB/ファイル、1 GiB 合計。ダウンロード中はページを開いたままにしてください。サイトアクセス失敗時はエラーまたは空状態を表示し、架空の投稿で埋めません。実機、他の Android バージョン、デコーダーでの動作は引き続きテストが必要です。

本版のビルド、自動テスト、アップグレード検証は v1.1.0 リリースチェックを参照。インターフェースと拡張の調査は「データソース」と「機能対照」を参照。

### ライセンス

プロジェクトは GPL-3.0-only。各正式版は対応するコミットの完全ソース ZIP を提供；APK にはオープンソースライセンスと第三者声明を同梱。参考プロジェクトは Sakuga Extended で、その Web スクリプト、スタイル、画像は複製していません。依存声明は `THIRD_PARTY_NOTICES.md` を参照。

---

## English

# Sakugapp

Sakuga Mobile (installed as Sakugapp) is an unofficial Android client for browsing Sakugabooru, built with Kotlin, Jetpack Compose, and Media3 for a native UI. It is not affiliated with Sakugabooru or Sakuga Extended; media and tags come from the original site.

Supports Android 8.0 and above. The UI is available in Simplified Chinese, Traditional Chinese, English, and Japanese; official site pages use their supported English or Japanese.

### Features

**Browsing and search**: Continuous Post loading, ID-based deduplication, and position restoration on back. Scrolling down hides the search area; input, combined conditions, and history expand on demand. Supports original-site tag syntax, AND / OR / exclude, sorting, Score quick conditions, tag suggestions, and up to 50 search history entries. The average color tool calculates the average color and HEX of visible thumbnails; it is not server-side color similarity search.

**Posts and layout**: Phones offer 1, 2, 3, and 4 columns; tablets add 8 and 12. Rectangular thumbnails preserve aspect ratio and show available duration and actual Score; copyright tags are yellow, animator tags purple. On the detail page, previous post, next post, download, original post, and favorite icons share one row; Post ID, source, Score, and other metadata wrap compactly, preserving category tags and comments.

**Player**: Play, seek, mute, volume, 0.1×–3× speed, fullscreen rotation, and A–B loop; pinch zoom, double-tap pause, and left/right switching between posts in the current list. Advanced controls are collapsible and provide display adjustments, mirroring, and screenshot sharing. Strict frame analysis steps by actual decoded index and shows frame numbers; it does not substitute fixed millisecond jumps for frame-by-frame stepping.

**Favorites**: Local multiple folders, quick favorite/unfavorite from the detail page, covers and sorting, batch management, and JSON import/export. Home shows only folders; opening one uses the same Post grid. Selected media can be downloaded for offline browsing.

**Appearance**: System / dark / light themes, unified Post floating cards, rounded corners, buttons, and expansion animations. Search and favorite management collapse secondary actions by default to reduce persistent controls.

**Account and community**: Account login, Vote, comments, and Blog / Forum entries follow existing implementations. Actions requiring an account depend on the official site's response; this release did not automatically perform real-account voting or comment writes.

### Screenshots

The following are actual Android emulator screenshots; see the Screenshot Log for sources and isolated test notes.

Post detail — Simplified Chinese settings

### Download and install

Download the officially signed APK from GitHub Releases; v1.1.0 provides the APK, complete source, and SHA-256 checksum file. Allow your browser to install unknown-source apps, then open the APK to install.

The package name remains `com.sakugapp`. v1.1.0 uses versionCode 10, higher than v1.0.0 and earlier development candidates. Versions signed with the same official certificate should be installed as an overwrite; do not uninstall. Export favorite JSON first. The favorites library, settings keys, and search history are preserved, and database upgrades do not clear favorite data.

Debug and official builds use different certificates and cannot overwrite each other. Before switching, export favorite JSON, uninstall Debug, install the official build, and import. JSON does not include accounts, settings, search history, or downloaded media; uninstalling clears these local data.

### Build and release

Requires JDK 17, Android SDK 35 / Build Tools 35.0.0. The repository includes Gradle 8.13 Wrapper and uses AGP 8.9.2, Kotlin 2.1.20, and Compose BOM 2025.04.01; min API 26, target API 35.

```bash
python3 scripts/check-localization.py
python3 -m unittest discover -s scripts/tests -v
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. With a supported device connected, run `./scripts/run-device-checks.sh` to execute device tests and export screenshots.

Official signing uses protected GitHub Actions Secrets; see the Signing Guide. With a signing environment configured, run:

```bash
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

Release APK: `app/build/outputs/apk/release/app-release.apk`. Official builds fail without signing configuration; `-PreleaseUnsignedCheck=true` is only for non-publishable unsigned prechecks. Release builds upload a draft first, publish after verification, and do not overwrite existing Releases.

### Limitations and verification

A–B loop runs by timestamp and is subject to player and decoder latency. Strict frame analysis requires Android 9 (API 28) or above and devices/formats supporting indexed decoding; single files are limited to 200 MiB and frames to 3840×2160. If the current playback time cannot be reliably mapped, analysis starts from frame 1 and shows the limitation. Unknown duration displays “—”.

Offline batch downloads are limited to 20 per batch, 200 MiB per file, and 1 GiB total; keep the page open during downloads. Site access failures show an error or empty state and are not filled with fictional posts. Real-device behavior, other Android versions, and decoders still need testing.

See the v1.1.0 release check for build, automated test, and upgrade verification; see Data Sources and Feature Comparison for interface and extension investigation.

### License

The project uses GPL-3.0-only. Each official release provides a complete source ZIP for the corresponding commit; the APK includes open-source licenses and third-party notices. The reference project is Sakuga Extended; its web scripts, styles, and images were not copied. See `THIRD_PARTY_NOTICES.md` for dependency notices.
