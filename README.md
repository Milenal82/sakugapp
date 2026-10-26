# Sakugapp

[简体中文](#简体中文) | [繁體中文](#繁體中文) | [日本語](#日本語) | [English](#english)

---

## 简体中文

开发测试版本 **1.2.1 / versionCode 9**，分支 `feature/compact-interface`，基于 1.2.0。包名 `com.sakugapp` 和正式签名保持不变，不改 main 或已发布 v1.0.0。

- 详情页五个统一图标：上一帖、下一帖、下载、原帖、收藏。长按查看名称，无法切帖时禁用。
- 一个收藏入口选择多个收藏夹、保存到默认夹或取消收藏。Score 与来源、Post ID 等信息紧凑换行。
- 搜索按需展开；组合搜索、历史和平均色彩位于展开区，历史默认收起，原搜索能力和记录保留。
- 收藏首页仅显示收藏夹，夹内复用 Post 网格。新建/导入/导出为描边按钮，批量管理、封面、重命名等位于更多菜单。
- 删除 Tags 翻译、词典/译名编辑和收藏页搜索；保留原标签、分类颜色、Post 搜索和四语界面。
- 保留深浅主题、平板列数、视频速度、严格帧分析、A–B 循环、手势、账户、Vote、评论和原视频下载。

[前后对比、删除清单与验证](docs/release-1.2.1.md) · [修改文件](docs/changed-files-1.2.1.md) · [版本说明](release-notes/v1.2.1.md)

在 [GitHub Actions](https://github.com/Milenal82/sakugapp/actions/workflows/android.yml) 选择本分支的成功运行，下载 **sakugapp-signed-candidate**：内含既有正式证书签名的候选 APK、同提交源码 ZIP、SHA-256 和验证报告。Debug APK 另见 **sakugapp-debug**。候选仅通过 Actions 提供，不替换正式 Release。

同证书旧版可以直接覆盖安装以保留数据。Debug 版不能覆盖正式签名版；切换前导出收藏 JSON，再卸载 Debug 并安装候选、导入 JSON。JSON 不含凭据、设置、搜索历史或媒体，卸载会清除这些应用内数据。

数据库升级只删除已废弃译名表，保留收藏夹、帖子、关系和媒体信息。旧 JSON 可导入，新备份不含译名。下载配额仍为 20 个/批、200 MiB/文件、1 GiB 总额，下载时保持页面打开。时长未知显示“—”，A–B 是带解码延迟的时间戳循环，严格帧分析要求 API 28+ 与受支持解码器。

```sh
python3 scripts/check-localization.py
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

JDK 17 / Gradle 8.13 / SDK 35 / Room 2.7.1。源码 GPL-3.0-only，完整许可证和第三方声明随项目/APK 提供。实际测试结果见升级记录；不把编译当成真机验证。

---

## 繁體中文

1.2.1 精簡詳情操作、按需搜尋與收藏夾首頁；移除 Tags 翻譯及收藏頁搜尋，保留原標籤、JSON、播放器及四語介面。[變更與驗證](docs/release-1.2.1.md)。

Kotlin + Jetpack Compose 開發的原生 Android 客戶端，用於瀏覽 [Sakugabooru](https://www.sakugabooru.com/post) 的標籤與貼文。

### 功能

- 可切換 1–4 欄的貼文網格（預設 2 欄；平板另有 8/12 欄），顯示版權標籤、動畫師標籤與真實 Score
- 組合搜尋（AND / OR / 排除）、排序與 rating 篩選、搜尋歷史
- 觸屏預覽與 Media3 播放器（倍速、鏡像、畫面調整、截圖）
- 原視頻下載與嚴格逐幀分析（需 API 28+）
- 官方帳號登入、Vote、評論、Blog / Forum 瀏覽
- 介面支援 **English / 简体中文 / 日本語 / 繁體中文**

### 下載與安裝

正式版本見 [GitHub Releases](https://github.com/Milenal82/sakugapp/releases)。

- `v1.0.0` 已正式發布；本輪 1.2.1 候選只透過開發分支 Actions 提供，不替換既有 Release。
- 最低支援 Android 8.0（API 26）；嚴格逐幀分析需 API 28+。
- 升級時請確保安裝**相同正式證書簽名**且 `versionCode` 更高的 APK，直接覆蓋以保留資料。
- Debug 版無法覆蓋正式版。切換前匯出收藏 JSON；JSON 不含設定、歷史或帳號，解除安裝會清除這些資料。

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

1.2.1 では詳細の5アイコン、必要時に開く検索、フォルダ中心のお気に入りに整理。タグ翻訳とお気に入り内検索を削除し、元のタグ、JSON、プレーヤーと4言語UIを保持。[変更と検証](docs/release-1.2.1.md)。

Kotlin + Jetpack Compose で開発された Android ネイティブクライアント。[Sakugabooru](https://www.sakugabooru.com/post) のタグと投稿を閲覧できます。

### 機能

- 1–4 列の投稿グリッド（既定 2 列、タブレットは 8/12 列にも対応）。著作権タグ、アニメータータグ、実際の Score を表示
- 複合検索（AND / OR / 除外）、並び替え、rating フィルター、検索履歴
- タッチプレビューと Media3 プレーヤー（倍速、ミラー、画質調整、スクリーンショット）
- 元動画のダウンロードと厳密なフレーム解析（API 28+ が必要）
- 公式アカウントログイン、Vote、コメント、Blog / Forum 閲覧
- UI は **English / 简体中文 / 日本語 / 繁體中文** に対応

### ダウンロードとインストール

正式版は [GitHub Releases](https://github.com/Milenal82/sakugapp/releases) を参照してください。

- `v1.0.0` は公開済みです。1.2.1 候補は開発ブランチの Actions で提供し、公開済み Release を置き換えません。
- 最低要件は Android 8.0（API 26）。厳密なフレーム解析には API 28+ が必要です。
- アップデート時は、**同じ正式証明書で署名**され、`versionCode` がより大きい APK を上書きインストールしてください。データが保持されます。
- Debug 版は正式署名版に上書きできません。切り替え前にお気に入り JSON を保存してください。設定・履歴・アカウントは JSON に含まれず、アンインストールで消えます。

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

1.2.1 simplifies detail actions, expands search on demand and opens Favorites with folders only. Tag translation and favorites search are removed; original tags, JSON backups, player tools and all four interface languages remain. [Changes and validation](docs/release-1.2.1.md).


A native Android client built with Kotlin + Jetpack Compose for browsing tags and posts on [Sakugabooru](https://www.sakugabooru.com/post).

### Features

- Configurable 1–4 column post grid (default 2; tablets also support 8/12) showing copyright tags, animator tags, and real Score
- Combined search (AND / OR / exclude), sorting, rating filters, and search history
- Touch preview and Media3 player (speed, mirror, display adjustments, screenshots)
- Original video download and strict frame analysis (requires API 28+)
- Official account login, voting, comments, Blog / Forum browsing
- UI available in **English / 简体中文 / 日本語 / 繁體中文**

### Download & Install

See [GitHub Releases](https://github.com/Milenal82/sakugapp/releases) for official builds.

- `v1.0.0` is published. The 1.2.1 candidate is distributed through development-branch Actions without replacing the existing Release.
- Minimum requirement: Android 8.0 (API 26). Strict frame analysis requires API 28+.
- To upgrade, install an APK signed with the **same official certificate** and a higher `versionCode`. Overwrite installation preserves local data.
- Debug builds cannot overwrite official builds. Export favorites JSON first; settings, history and accounts are not in JSON and are lost on uninstall.

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
