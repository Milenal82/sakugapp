# Sakugapp

[简体中文](#简体中文) | [繁體中文](#繁體中文) | [日本語](#日本語) | [English](#english)

---

## 简体中文

本轮增量版本 **1.2.0 / versionCode 8**，分支 `feature/personalization-pro-player`，基于上一轮 1.1.0 开发成果。包名 `com.sakugapp`、正式证书及旧设置/历史/账户/下载保持兼容，不替换已发布 v1.0.0。

- 手机 1–4 列；平板 8/12 列、紧凑卡片；缩略图左下时长、右下红色 Score，标签及可选译文在图下。
- 本地 Room 收藏库：多收藏夹、封面、排序、批量移动/移除、本地搜索、JSON 导入/导出和顺序批量离线下载。
- 小型维护词典及可编辑的中文译名，长按复制/搜索/修改/删除覆盖；不猜测未知姓名，不改变原始搜索标签。
- 专业控制分组折叠，A–B 时间戳循环、真实解码逐帧、已有九档速度、1–5× 缩放和平移、双击播放暂停、按当前结果顺序切帖。
- 跟随系统/深/浅主题，四种 UI 语言，集中动画时长与可调卡片间距。

[逐项状态、数据兼容和精度限制](docs/release-1.2.0.md) · [实际修改文件](docs/changed-files-1.2.0.md) · [版本说明](release-notes/v1.2.0.md)

在 [GitHub Actions](https://github.com/Milenal82/sakugapp/actions/workflows/android.yml) 选择本分支的成功运行，下载 **sakugapp-signed-candidate**：内含正式证书签名候选 APK、同提交完整源码、SHA-256 校验、许可及验证报告。此候选可按证书和递增版本覆盖旧正式版；**不要卸载含有重要数据的旧版来试装 Debug APK**。正式 Release 保持原状。最终设备和升级结果以对应成功运行及草稿 PR 报告为准。

收藏 JSON 仅备份本地元数据/译名，不含账户凭据或视频文件。导入按 ID 和同名文件夹合并，保留既有记录；删除收藏关系保留媒体文件。新批量下载位于应用私有存储，最多 20 个/批、200 MiB/文件、1 GiB 总额，需保持页面开启；旧的系统文件选择器下载保留。元数据可离线查看，未完成媒体下载时不会标记可离线播放。

时长只有从真实播放器取得后才显示，未知为“—”。A–B 有 30ms 检查与解码 seek 延迟；严格帧分析需 Android 9/API 28+、可用解码器，无法核实时间映射时从第 1 帧开始，不用固定 FPS 冒充当前帧号。详见升级记录。

```sh
python3 scripts/check-localization.py
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

JDK 17 / Gradle 8.13 / SDK 35 保持不变，新增 Room 2.7.1。源码 GPL-3.0-only，随附完整许可证与第三方声明。附件未提供功能 2，已按用户确认排除；未擅自添加壁纸功能。

---

## 繁體中文

1.2.0 新增本機收藏夾與 JSON 備份、可編輯標籤譯名、A–B 循環、縮放手勢、折疊播放器和 8/12 欄平板瀏覽。[範圍與測試](docs/release-1.2.0.md)。

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

- `v1.0.0` 已正式發布；本輪 1.2.0 候選只透過開發分支 Actions 提供，不替換既有 Release。
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

1.2.0 ではローカルのお気に入り、JSON バックアップ、独自のタグ訳、A–B リピート、拡大・移動、折りたたみ操作、タブレットの 8/12 列を追加。[対応範囲と検証](docs/release-1.2.0.md)。

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

- `v1.0.0` は公開済みです。1.2.0 候補は開発ブランチの Actions で提供し、公開済み Release を置き換えません。
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

1.2.0 adds local collections and JSON backups, editable Chinese tag translations, time-based A–B loops, pinch/pan and playback gestures, collapsible controls, and 8/12-column tablet browsing. [Scope and validation](docs/release-1.2.0.md).


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

- `v1.0.0` is published. The 1.2.0 candidate is distributed through development-branch Actions without replacing the existing Release.
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
