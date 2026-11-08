# Sakugapp

[简体中文](#简体中文) · [English](#english) · [日本語](#日本語) · [Releases](https://github.com/Milenal82/sakugapp/releases/latest)

## 简体中文

开发分支的 Debug 测试更新：六按钮详情栏、所有列数一致的 0–10 dp 卡片间距、Artist 统计联动及标签收藏多选。集数内 Artist 查询仅使用已加载的集数样本。[检查记录与限制](docs/debug-actions-grid-artist.md)。

Sakugapp（Sakuga Mobile）是 Sakugabooru 的非官方原生 Android 客户端，使用 Kotlin、Compose 和 Media3，不以 WebView 实现帖子列表。

- 搜索首页、Posts 与个人资料三个主入口；标签联想、组合条件、日期范围、搜索历史及 14 个发现入口。
- 连续加载、下拉刷新、可调网格和浏览记录；原始分类标签、真实 Score、作品与集数浏览、采样原画师统计。
- 原生视频详情，支持全屏、缩放、锁定、倍速、逐帧、A–B 循环及精确片段导出。
- 本地多收藏夹、二级分类、排序、标签收藏／备注和 JSON 备份；网站 1／2／3 星 Favorites 独立显示，需登录。
- 四选一原画猜谜，题数可选，支持最多 30 部已验证作品的自定义池。
- 本地头像昵称；默认、薄荷、樱花、琥珀配色，支持深浅外观、75%–125% 字号及 0–20 dp 标签行距。
- 单个、片段与批量下载使用独立目录。批量任务有进度、取消和失败重试。

从 [GitHub Releases](https://github.com/Milenal82/sakugapp/releases/latest) 下载正式 APK。Android 8.0 起可安装；实际解码帧索引需要 Android 9 或更高及兼容解码器。正式版使用同一签名，可直接覆盖升级并保留应用数据。Debug 与正式版签名不同，请先导出收藏备份，勿通过清除数据解决签名冲突。

[安装和签名说明](docs/release-signing.md) · [v1.3.0 说明](docs/v1.3.0.md) · [备份格式](docs/backup-format.md) · [许可证](LICENSE) · [第三方声明](docs/third-party-notices.md)

## English

Debug development update: six detail actions, consistent 0–10 dp grid spacing at every column count, artist-statistics links and saved-tag multi-selection. Episode artist results are limited to the loaded episode sample. [Checks and limitations](docs/debug-actions-grid-artist.md).

Sakugapp (Sakuga Mobile) is an unofficial native Android client for Sakugabooru, built with Kotlin, Compose and Media3.

- Search, Posts and Profile navigation; tag suggestions, combined conditions, date ranges, search history and 14 discovery entries.
- Continuous loading, pull-to-refresh, adjustable grids and browsing history; original classified tags, real Scores, show/episode browsing and sampled artist statistics.
- Native playback with fullscreen, zoom, locking, speed, frame stepping, A–B loops and precise clip export.
- Local folders, child classifications, ordering, saved tags/notes and JSON backups. Account-scoped 1/2/3-star website Favorites remain separate.
- A four-choice artist quiz with selectable question counts and a verified custom pool of up to 30 works.
- Local avatars/nicknames; Default, Mint, Sakura and Amber palettes in light/dark modes, 75%–125% text size and 0–20 dp preview tag spacing.
- Separate destinations for individual videos, clips and batches; batch progress, cancellation and retry.

Download the official APK from [Releases](https://github.com/Milenal82/sakugapp/releases/latest). Android 8.0 or newer; decoded frame indices require Android 9 and a compatible decoder. Official releases share a certificate and support in-place upgrades. Debug uses a different signature: export a backup before switching; do not clear data to work around signing errors.

[Signing and installation](docs/release-signing.md) · [v1.3.0](docs/v1.3.0.md) · [Backup format](docs/backup-format.md) · [License](LICENSE) · [Third-party notices](docs/third-party-notices.md)

## 日本語

開発ブランチの Debug 更新：詳細の6操作、全列数共通の 0–10 dp カード間隔、原画家統計への連携、保存タグの複数選択。話数内の原画家検索は読み込み済みの標本に限定されます。[確認記録と制限](docs/debug-actions-grid-artist.md)。

Sakugapp（Sakuga Mobile）は Kotlin、Compose、Media3 を使用した Sakugabooru 非公式の Android ネイティブクライアントです。

- 検索・Posts・プロフィールのナビゲーション。タグ候補、複合条件、日付範囲、検索履歴、14 の発見メニュー。
- 連続読み込み、更新、可変グリッドと閲覧履歴。元の分類タグ、実際の Score、作品・話数別閲覧、標本に基づくアニメーター統計。
- 全画面、ズーム、操作ロック、速度変更、コマ送り、A–B ループ、精密クリップ書き出し。
- ローカルフォルダー、子分類、並び替え、タグのお気に入りとメモ、JSON バックアップ。サイトの 1・2・3 星 Favorites は別管理です。
- 四択クイズ、問題数設定、検証済み作品を最大 30 件選べる作品リスト。
- ローカル画像とニックネーム。標準・ミント・桜・琥珀の配色、明暗モード、75%–125% の文字サイズ、0–20 dp のタグ行間。
- 通常動画・クリップ・一括ダウンロードの保存先を個別に設定。一括処理の進行状況、キャンセル、再試行。

公式 APK は [Releases](https://github.com/Milenal82/sakugapp/releases/latest) から取得できます。Android 8.0 以降に対応し、デコード済みフレーム索引には Android 9 以降と対応デコーダーが必要です。公式版同士は上書き更新できます。Debug 版は署名が異なるため、切り替え前にバックアップしてください。署名エラーを回避するためにデータを消去しないでください。

[署名とインストール](docs/release-signing.md) · [v1.3.0](docs/v1.3.0.md) · [バックアップ形式](docs/backup-format.md) · [ライセンス](LICENSE) · [サードパーティー](docs/third-party-notices.md)

## Screenshots / 截图 / スクリーンショット

Original emulator captures with local test fixtures; not live account data. / 模拟器原始截图，内容为测试样本。/ ローカル標本を使った実際の画面です。

<img src="https://raw.githubusercontent.com/Milenal82/sakugapp/evidence/v130/screenshots/phone-v130-home-default-light-100.png" width="240" alt="Search home" /> <img src="https://raw.githubusercontent.com/Milenal82/sakugapp/evidence/v130/screenshots/phone-v130-profile-dark-125.png" width="240" alt="Compact local profile, Amber dark, 125 percent text" /> <img src="https://raw.githubusercontent.com/Milenal82/sakugapp/evidence/v130/screenshots/phone-v130-quiz-answers.png" width="240" alt="Native four-choice quiz" />

## Build / 构建 / ビルド

JDK 17 · Android SDK 35 · Gradle Wrapper 8.13 · AGP 8.9.2 · Kotlin 2.1.20

```sh
python3 scripts/check-localization.py
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Official signing is restricted to the existing protected release workflow; see [release-signing.md](docs/release-signing.md). Source archives include the wrapper, tests, license and build scripts, and exclude local configuration and secrets.

## Limits / 限制 / 制限

Sakugabooru availability, authentication and request limits apply. Missing classifications and Scores are not invented. Sources are free text, so episode grouping can be incomplete; statistics cover sampled posts. Frame stepping distinguishes decoded indices from estimates; A–B playback and re-encoded clip boundaries have decoder/format limits. Offline batches retain the 20-item, 200 MiB/file and 1 GiB protections. History is bounded to 500 entries or 8 MiB. Website tags, comments and Sources remain original. Translations have resource checks, without native-speaker review. See [v1.3.0 verification and limits](docs/v1.3.0.md).
