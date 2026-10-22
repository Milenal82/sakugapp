# 0.4.0 修复与验收（2026-10-22）

在原 main / `6d66b40` 上继续修改，未重新初始化。Downloads.zip 包含旧 0.3.0 源码、53 项单测及 5 项通过的设备测试报告；附件 Lint 含依赖更新、UseKtx、SetJavaScriptEnabled 建议。附件作为数据检查，未执行其中脚本、未覆盖工作区源码。

## 标签读取根因

现场读取 `https://www.sakugabooru.com/tag/summary.json` 得到 HTTP 200、版本 7358。分类数据中存在真实记录 `1\`emil_lundmark<U+2028>laura_begliomini\``；U+2028 是 Unicode 行分隔符。Android ICU 正则的空白行为与主机 JVM 不同，旧的按所有空白切分 + 任意坏记录中止，会让整份分类失败。此前设备测试只检查可打开真实帖子，未断言卡片中的分类，因此漏掉了此问题。

新解析器按数字类别 + 反引号的记录起点分隔，仅接纳结构完整且名称不含空白的记录。异常记录整体忽略，不把它拆成两个“已确认动画师”。其他明确的 1=artist / 3=copyright 保留；未知仍为 UNKNOWN，不猜测。整份响应无法取得或没有有效记录时仍显示错误，可重新加载；后续分页恢复分类时会修复已有卡片。

`PostCaption.kt` 统一格式化：只有黄色版权标签、紫色动画师标签、服务端 Score，空格分隔、默认自动换行。预览区域仍为 16:9 长方形，Fit 不拉伸。真实响应样本、请求地址及 SHA-256 在 `evidence/tag-summary-regression.json`；样本含少量原始公开记录，未含任何账户或 Cookie。新增 Android 运行时回归测试，避免仅依赖 JVM 的空白行为。

## 繁體中文与组合搜索

原生应用新增 zh-TW，DataStore 保留选择。官网只支持 en/ja；中文模式使用英文官网，不发送虚构的中文 locale。标签与正文保持原始数据，不机器翻译；密码及 Vote/评论协议保持既有流程。

搜索工具使用用户提供的备忘录，并检查 Moebooru `app/models/tag/parse_methods.rb`、`app/models/post/sql_methods.rb`：普通标签 AND、`~` 标签 OR、`-` 排除、通配符、用户/收藏、哈希、rating、source、ID/尺寸/分数/像素/日期范围及全部列出的排序。完整示例位于 `SearchGuide.kt`，应用内可查看、点入草稿。

客户端不自行求值或重写搜索逻辑。打开工具默认保留现有 order/rating（包括 -rating）；只有明确选择新筛选才替换对应字段。补全保留 `~`/`-`，不对 source/id 等字段执行普通标签补全。通配符组合、混合 OR、重复同类字段及缺少 vote 条件的 order:vote 给出提示，保留原式交由网站处理，不承诺网站不支持的组合。

现场只读验证 AND、OR、不同字段组合，结果在 `evidence/search-live-2026-10-22.json`。38 条备忘录示例加一个含 URL `&` 的边界查询均通过实际网络请求层编码测试；不把 MockWebServer 当作网站语义验证。

## 逐帧

播放器下方独立的“逐幀播放／分析”入口，下载确认后显示真实解码帧、当前/总帧数、上一幀、下一幀、幀編號及索引滑杆。增加下载/解码失败重试。Android API 28+ 的 `getFrameAtIndex` 继续作为唯一严格索引路径，不复制扩展中的 FPS/时间偏移估算；没有把普通 seekTo 改名为逐帧。

本轮再次检查 Sakuga Extended `js/frames.js`（固定来源与许可同功能对照表）。扩展提供视频时间估算、帧输入与 GIF 控制；Android 采用原生索引解码实现帧输入/滑杆。严格模式明确从原始第 1 帧开始，独立于当前播放时间，避免声称能从时间精确反推 VFR 帧号。

限制：Android 9+、原文件 ≤200 MiB、像素 ≤3840×2160，设备和格式必须提供实际帧数/索引解码。不支持时明确失败，不做伪逐帧降级；GIF 严格逐帧仍未实现。

## 验证与安装

本地执行 `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`。增加 10 项 JVM 回归后共 63 项；设备任务扩展为 7 项，包含 Android Unicode 分类回归、繁体中文持久化/切换、搜索备忘录、真实卡片颜色/Score、实际视频前后帧与指定第 3 帧。最终执行结果以具体 CI 运行及交付报告为准，不把已编译的测试标记成已运行。

工具链维持 JDK 17 / Gradle 8.13 / AGP 8.9.2 / SDK 35；APK versionCode=4、versionName=0.4.0。无 ABI 限制，仍含 arm64-v8a、armeabi-v7a、x86、x86_64。CI 上传 APK、完整源码 ZIP 与测试报告。

新 Debug 包可能与旧 CI 包签名不同；签名不同时无法覆盖安装，卸载会清除本地设置与登录状态。未要求用户提供密码，未执行真实投票或评论写入。用户反馈 Vote 正常单独记录，不能扩展为我们本轮独立验证了所有账户功能。
