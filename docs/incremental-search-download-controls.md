# 下载、组合搜索、返回导航与播放器面板增量

基线为 `b5947f9cb346419ca8617aa1301558fec0d04c91`，继续使用 `feature/history-tags-refresh` 开发分支。最新 main 为 `6be8a62`，原分支已有成果保留。包名 `com.sakugapp`、versionName `1.2.0`、versionCode `11` 及正式证书不变；不创建标签或公开 Release。

## 改动

- 新视频名为 `Artist_来源集数_PostID.ext`，例如 `yoshinari_you_ep_12_123456.mp4`。Artist 来自真实分类，多个标签按原顺序以 `__` 连接。缺失值分别使用 `unknown_artist`、`unknown_source`。Artist 最多 64 UTF-8 字节、来源最多 72 字节，完整名不超过 180 字节，始终保留 ID 和真实扩展名。
- Source 仅在明确的 `ep 12`、`episode 12`、`#12` 等标记对应唯一整数集数时使用 `ep_12`。日期、分辨率、URL 数字 ID、查询参数、URL 片段、集数范围及小数不猜测。URL 路径中的明确 ep 标记可识别；无明确集数时采用清理后的文本，网址仅保留主机名以排除凭据、签名路径和查询参数。原下载索引与文件均保留，不重命名。单个文档下载与内部下载仍共用 `VideoDownloadPlan`；重名保护沿用系统 CreateDocument 和内部序号机制。
- 组合搜索使用已有搜索历史，选择后仅载入草稿，点击应用才搜索。唯一且已知的 order / rating 条件会选中对应字段；重复、未知、负向或带引号的原始语法留在查询框。手工编辑、添加条件和字段选项保持同步，标签联想及原历史持久化不变。
- 搜索结果增加 NavController 的 `search` 层级，组合搜索仍使用原弹窗。首页列表快照、分页和网格位置保留；取消组合搜索返回当前结果，系统返回结果页可回到原首页。返回提交前不还原快照；迟到请求有代次保护。搜索编辑态先由输入法处理返回，再折叠编辑区；没有新增全局滑动拦截，播放器触摸逻辑不变。
- 高级控制按播放速度、A–B、逐帧分析、声音/画面分组，以主题浅层背景区分；倍率和循环状态使用选中样式。继续使用原动画、播放器、循环、时间映射和严格帧解码实现，面板整体可折叠，小屏可滚动。

## 验证

本地基线 Debug 构建、单测和 Lint 通过。改动后 Debug / Release 各 112 项单测、两种 Lint（0 错误）、Debug APK、Android 测试 APK及未签名 Release 预检通过。签名候选和设备结果以本提交的 [Actions 证据报告](https://github.com/Milenal82/sakugapp/blob/evidence/history-tags-refresh/report.json) 为准，不把待执行项当作通过。

```sh
python3 scripts/check-localization.py
python3 -m unittest discover -s scripts/tests -v
./gradlew assembleDebugAndroidTest testDebugUnitTest
./gradlew lintDebug assembleDebug testReleaseUnitTest lintRelease assembleRelease -PreleaseUnsignedCheck=true
```

设备组保留原回归测试，追加组合历史/返回/输入法测试和真实播放器布局测试。独立手机、平板显示组也检查分组面板；API 35 使用系统手势导航验证左右屏幕边缘返回和取消预测性返回。原倍速、实际 A–B 回跳、严格帧、缩放、双击和左右切帖回归继续执行。原始截图从设备导出到同一证据分支，不生成界面图片。

本机没有 KVM；设备行为只以云端实际执行结果为准。翻译新增两项标题并通过八语资源检查，不宣称母语审校。没有扩大真机、全部 Android 版本和全部媒体格式/解码器验证范围。

## 文件清单

| 范围 | 文件 |
| --- | --- |
| 文件名及下载元数据 | `data/MediaFileNames.kt`、`ui/VideoDownloadButton.kt` |
| 查询字段和历史草稿 | `data/SearchSyntax.kt`、`ui/SearchDialog.kt` |
| 返回栈、列表快照和定位 | `MainActivity.kt`、`ui/FeedViewModel.kt` |
| 高级面板 | `ui/VideoPanel.kt` |
| 标题资源 | `res/values{,-zh-rCN,-zh-rTW,-ja,-fr,-es,-ru,-ko}/strings.xml` |
| 单测 | `PersonalDataTest.kt`、`AppearanceAndDownloadTest.kt`、`SearchAndCaptionTest.kt`、`FeedViewModelTest.kt` |
| 设备测试 | `SearchNavigationDeviceTest.kt`、`SearchImeGestureDeviceTest.kt`、`AdvancedControlsLayoutDeviceTest.kt`、`OfflineLibraryDeviceTest.kt`、`PlayerRotationDeviceTest.kt` |
| 旧界面断言适配 | `UiSmokeTest.kt`、`PublicBrowseTest.kt` |
| 验证流程 | `.github/workflows/android.yml`、`scripts/run-device-checks.sh`、`scripts/run-six-column-checks.sh`、`scripts/run-search-back-checks.sh`、`scripts/record-ci-evidence.py` |
| 文档 | `README.md`、`docs/test-report.md`、本文件 |

Kotlin 主代码路径前缀为 `app/src/main/java/com/sakugapp/`；测试分别位于 `app/src/test/java/com/sakugapp/` 与 `app/src/androidTest/java/com/sakugapp/`。

首轮运行 [40873887531](https://github.com/Milenal82/sakugapp/actions/runs/40873887531) 的单测、两种构建和签名成功，设备 37/40 通过。两项旧用例仍期待取消后展开的搜索框及方括号倍率文案，现改为重新打开并验证原查询、验证倍率选中语义；不删除原查询保持与播放断言。API 35 的输入法返回未关闭问题改为在输入法可见时停用编辑态 BackHandler，交由原生输入法优先处理。补充首页和结果页两种键盘上下文、真实返回前后截图；此首轮失败不能算完整验收通过。

第二轮 [40876305761](https://github.com/Milenal82/sakugapp/actions/runs/40876305761) 的设备为 39/40；唯一失败来自 API 35 的 Compose 测试绘制同步（SnapshotStateObserver 多线程访问异常），发生在键盘返回断言前。API 35 的键盘场景改用 ActivityScenario + UiAutomator，保留真实平台时钟和相同的两种页面、左右边缘、键盘消失、不退出及不提交检查；返回手势从键盘触摸区域上方触发。组合搜索、列表状态和预测性返回取消仍保留原 Compose/真实手势测试，未减少用例数量或跳过失败断言。API 28 的编辑态检查也继续执行。

复核中补充了带引号 Source、标签内撇号、反斜线转义和未闭合输入的测试。原空格切分会把 Source 文本中的 `order:` / `score:` 误当作独立筛选字段并删除，已由失败用例复现后修正为保留引号区域的词法切分；不解析的内容留在原框中，框外可识别字段仍可选中。API 28 回归脚本增加 15 分钟 Gradle 上限和失败时的测试名/异常栈日志，确保异常等待有诊断产物。

第三轮 [40878965968](https://github.com/Milenal82/sakugapp/actions/runs/40878965968) 已实际通过 API 35 两项测试、手机和平板显示组四项测试，以及单测、签名和覆盖安装；键盘前后原始截图确认输入法关闭、原查询保留。完整 API 28 组在约 25 分钟任务上限被取消，未上传 JUnit，因此没有记为通过，原因尚不能从已有产物确定。后续有界回归和上述引号边界修正以最新证据报告为准。
