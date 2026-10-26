# Sakugapp

[简体中文](#简体中文) | [繁體中文](#繁體中文) | [日本語](#日本語) | [English](#english)

---

## 简体中文

本轮在已发布 1.0.0 上增量开发 **1.1.0 / versionCode 7**，分支 `feature/browsing-search-zh-cn`；包名和正式签名配置不变，不覆盖已有 Release。九项升级、兼容性、实际测试与限制见 [升级记录](docs/release-1.1.0.md)，[接口验证](docs/evidence/browse-upgrade-2026-10-26.json)。

新增每批 60 条和预加载、搜索区滚动收放、红色 Score、紧凑详情、简体中文、全屏旋转、1–4 列、统一的 50 条历史和带真实分类的标签联想。系统简体中文默认显示简体中文，其他系统语言默认英文；原有手动语言选择保留。所有界面文案使用 Android 资源，不自动翻译网站标签、作品、姓名或评论。

在 [GitHub Actions](https://github.com/Milenal82/sakugapp/actions/workflows/android.yml) 选择本开发分支的已完成运行，下载 `sakugapp-debug` 或通过既有正式签名构建的 `sakugapp-signed-candidate`；同时提供源码 ZIP 和测试报告。只有后者经过证书核对后可覆盖正式版；**不要为测试 Debug APK 卸载含有重要数据的正式版**。Debug 版可在独立设备/模拟器安装。安装升级结论以实际 CI 报告为准。

```sh
python3 scripts/check-localization.py
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

历史和设置继续使用 `settings_v1`，保留旧键；缓存、会话路径不变。GPL 和第三方声明保留，新增 AppCompat 1.7.1；JDK 17 / Gradle 8.13 / Android SDK 35 工具链不变。

---

## 繁體中文

Kotlin + Jetpack Compose 開發的原生 Android 客戶端，用於瀏覽 [Sakugabooru](https://www.sakugabooru.com/post) 的標籤與貼文。

### 功能

- 可切換 1–4 欄的貼文網格（預設 2 欄），顯示版權標籤、動畫師標籤與真實 Score
- 組合搜尋（AND / OR / 排除）、排序與 rating 篩選、搜尋歷史
- 觸屏預覽與 Media3 播放器（倍速、鏡像、畫面調整、截圖）
- 原視頻下載與嚴格逐幀分析（需 API 28+）
- 官方帳號登入、Vote、評論、Blog / Forum 瀏覽
- 介面支援 **English / 简体中文 / 日本語 / 繁體中文**

### 下載與安裝

正式版本見 [GitHub Releases](https://github.com/Milenal82/sakugapp/releases)。

- `v1.0.0` 已正式發布；本輪 1.1.0 候選只透過開發分支 Actions 提供，不替換既有 Release。
- 最低支援 Android 8.0（API 26）；嚴格逐幀分析需 API 28+。
- 升級時請確保安裝**相同正式證書簽名**且 `versionCode` 更高的 APK，直接覆蓋以保留資料。
- Debug 版無法直接覆蓋正式版；切換前請先備份設定與歷史。

### 建置

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK 輸出路徑：`app/build/outputs/apk/debug/app-debug.apk`

正式簽名流程見 [簽名操作手冊](https://github.com/Milenal82/sakugapp/blob/main/docs/release-signing.md)。

### 技術棧

JDK 17、Gradle 8.13、AGP 8.9.2、Kotlin 2.1.20、Compose BOM 2025.04.01、compile/target SDK 35、min SDK 26。

### 授權

專案代碼採用 **GPL-3.0-only**，見 [LICENSE](https://github.com/Milenal82/sakugapp/blob/main/LICENSE)。APK 隨附第三方授權全文與來源聲明。

---

## 日本語

Kotlin + Jetpack Compose で開発された Android ネイティブクライアント。[Sakugabooru](https://www.sakugabooru.com/post) のタグと投稿を閲覧できます。

### 機能

- 1–4 列の投稿グリッド（既定 2 列）。著作権タグ、アニメータータグ、実際の Score を表示
- 複合検索（AND / OR / 除外）、並び替え、rating フィルター、検索履歴
- タッチプレビューと Media3 プレーヤー（倍速、ミラー、画質調整、スクリーンショット）
- 元動画のダウンロードと厳密なフレーム解析（API 28+ が必要）
- 公式アカウントログイン、Vote、コメント、Blog / Forum 閲覧
- UI は **English / 简体中文 / 日本語 / 繁體中文** に対応

### ダウンロードとインストール

正式版は [GitHub Releases](https://github.com/Milenal82/sakugapp/releases) を参照してください。

- `v1.0.0` は公開済みです。1.1.0 候補は開発ブランチの Actions で提供し、公開済み Release を置き換えません。
- 最低要件は Android 8.0（API 26）。厳密なフレーム解析には API 28+ が必要です。
- アップデート時は、**同じ正式証明書で署名**され、`versionCode` がより大きい APK を上書きインストールしてください。データが保持されます。
- Debug 版から正式版への上書きはできません。切り替え前に設定と履歴をバックアップしてください。

### ビルド

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK の出力先：`app/build/outputs/apk/debug/app-debug.apk`

正式署名の手順は [署名操作マニュアル](https://github.com/Milenal82/sakugapp/blob/main/docs/release-signing.md) を参照してください。

### 技術スタック

JDK 17、Gradle 8.13、AGP 8.9.2、Kotlin 2.1.20、Compose BOM 2025.04.01、compile/target SDK 35、min SDK 26。

### ライセンス

プロジェクトコードは **GPL-3.0-only** です。[LICENSE](https://github.com/Milenal82/sakugapp/blob/main/LICENSE) を参照してください。APK にはサードパーティライセンスの全文と出典が同梱されています。

---

## English


A native Android client built with Kotlin + Jetpack Compose for browsing tags and posts on [Sakugabooru](https://www.sakugabooru.com/post).

### Features

- Configurable 1–4 column post grid (default 2) showing copyright tags, animator tags, and real Score
- Combined search (AND / OR / exclude), sorting, rating filters, and search history
- Touch preview and Media3 player (speed, mirror, display adjustments, screenshots)
- Original video download and strict frame analysis (requires API 28+)
- Official account login, voting, comments, Blog / Forum browsing
- UI available in **English / 简体中文 / 日本語 / 繁體中文**

### Download & Install

See [GitHub Releases](https://github.com/Milenal82/sakugapp/releases) for official builds.

- `v1.0.0` is published. The 1.1.0 candidate is distributed through development-branch Actions without replacing the existing Release.
- Minimum requirement: Android 8.0 (API 26). Strict frame analysis requires API 28+.
- To upgrade, install an APK signed with the **same official certificate** and a higher `versionCode`. Overwrite installation preserves local data.
- Debug builds cannot overwrite official builds. Back up settings and history before switching.

### Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`

For the official signing workflow, see the [Signing Guide](https://github.com/Milenal82/sakugapp/blob/main/docs/release-signing.md).

### Tech Stack

JDK 17, Gradle 8.13, AGP 8.9.2, Kotlin 2.1.20, Compose BOM 2025.04.01, compile/target SDK 35, min SDK 26.

### License

Project code is licensed under **GPL-3.0-only**. See [LICENSE](https://github.com/Milenal82/sakugapp/blob/main/LICENSE). The APK bundles full third-party license texts and attribution notices.
