# 1.1.0 增量升级（开发分支）

基于远端 main `cbe04535f88471e070196ecc559e769d00895b5c`，保留用户更新的三语 README、已发布 1.0.0、`com.sakugapp` 和既有正式签名配置。开发分支 `feature/browsing-search-zh-cn`；versionName 1.1.0、versionCode 7。此轮不合并 main、不创建发布标签、不替换现有 Release APK。

| 九项要求 | 实现与源码 | 验证范围 |
| --- | --- | --- |
| 加载更多 | `SakugaRepository.kt` 每页由 20 调至实测支持的 60，构造参数可配置（1–100）；`MainActivity.kt` 剩余约 12 个条目时预取，保留无限滚动、加载进度与手动重试；`FeedViewModel.kt` 防重入、取消旧查询；完整重复页暂停自动请求 | 官网连续五页共 300 个不重复 ID；真实脱敏样本单测检查完整顺序、去重与末页停止；API 28 KVM 连续五批真实加载、去重与返回通过 |
| 搜索区收放 | `ScrollChrome.kt` 观察真实已消费滚动，上滑累计 64dp 收起、下滑 28dp 展开；200ms 位移动画；搜索区覆盖层与固定列表坐标避免重排跳动；输入、联想、历史面板展开时保持显示；到顶部自动展开；1dp OutlineVariant 分隔线 | 阈值/方向变化单测及设备上滑收起、下滑展开、历史开关验收通过 |
| Score 红色 | `AppTheme.kt` 深色 #FF5252、浅色 #B71C1C；`PostCaption.kt` 标签密度策略；Score 单独排版不会被长标签省略 | 深浅背景对比度均 ≥4.5:1；真实值与缺失值保留；设备用例检查颜色及 4 列可见性 |
| 紧凑详情 | `MainActivity.kt` 非标签元数据使用 FlowRow、较小字段名和正常字段值；文件大小来自已核实 file_size；长来源可选择复制，HTTPS 来源可打开 | 不改 Tags、Score/Vote、评论字体与布局；缺字段不编造，时长仍由 Media3 实际时长控制显示 |
| 简体中文 | `values/strings.xml`、`values-zh-rCN`、`values-zh-rTW`、`values-ja`；AppCompat Locale API 与 Android 13 localeConfig；首次默认系统简中→简中、其他系统语言→英文；手动保留 English/日本語/繁體中文及新增简体中文 | 298 个基础字符串，所有可翻译项的简中/繁中/日语资源和格式参数校验通过；不翻译标签、姓名、作品、评论、搜索语法及许可证原文 |
| 全屏旋转 | `VideoPanel.kt` 同一 ExoPlayer；普通页面竖屏，全屏 FULL_USER 尊重旋转锁；手动旋转、恢复跟随系统；返回优先退出全屏；Dialog 处理系统栏/刘海；`AndroidManifest.xml` 对旋转配置变更不重建 Activity | 本地测试视频设备用例通过：锁定系统旋转时手动横屏、单次返回竖屏；播放器实例、暂停/进度/倍速/循环/静音均保留 |
| 1–4 列 | `PreferencesStore.kt` 新增独立 columns 键，默认 2；首页紧凑按钮和设置入口；切换保持锚点，1/2/3/4 列自适应间距和 14/12/11/11sp 字体；高密度尽量保留一版权、一动画师及完整 Score | 不改变媒体 URL 或图片缓存；360dp 小屏 1–4 列、Score 颜色/可见性、设置持久化及详情返回通过 |
| 历史整合 | 只读 `settings_v1` 的旧 history 键；入口默认折叠，可再点击收起；提交搜索才记录，最近优先、去重、最多 50；支持单删、清空 | 旧 30 条历史及旧设置兼容、单删/清空设备测试通过；真实覆盖安装保留旧历史与设置通过 |
| 真实联想 | `TagSuggestions.kt` 使用实测 `/tag.json?name=...&limit=8`，分类只读 type；300ms debounce、IME 组合输入暂缓、过期请求取消、120 秒/32 查询缓存；与列表请求共用 650ms 起始间隔；`SearchEditing.kt` 按光标只替换当前词，保留 ~ / - 和所有其他条件；普通/组合搜索共用 | 真实响应解析、缓存/过期、403 不重试、错误不妨碍手动搜索单测通过；IME 组合输入暂缓、过期取消、真实响应样本选择及保留其他条件的设备用例通过 |

当前交付入口：[草稿 PR #1](https://github.com/Milenal82/sakugapp/pull/1)、[当前检查](https://github.com/Milenal82/sakugapp/pull/1/checks)、[完整文件清单](changed-files-1.1.0.md)。完整通过记录见下方；最终封装提交和 APK 的 SHA-256 见同一签名候选构件的 release-verification.json / SHA256SUMS.txt。

## 数据、兼容与性能

[现场接口证据](evidence/browse-upgrade-2026-10-26.json) 记录日期、URL、状态、摘要及五批 ID；测试样本只保留公开必要字段，移除上传者、创建者、来源等字段。不保存 Cookie、CSRF 或 HTML。网站的部署 JS 确认使用 TagCompletion/TagCompletionBox 与 `/tag/summary.json` 本地索引；应用用文档化的 tag.json 公开查询作为有界原生替代，不复制或执行网页 JS。

保留网站 page 从 1 开始的偏移语义、排序和完整查询表达式；动态插入/删除仍可能产生偏移遗漏，ID 去重不等于固定快照。网络失败不会推进 nextPage；已读列表保留，重试请求同页。全页重复数据停止自动链式请求，允许用户手动继续下一页。只有可见附近图片会由 LazyGrid/Coil 加载；一个活动预览，离开视区释放，没有为每批 60 条同时下载全部媒体。

DataStore 文件仍是 `settings_v1`，既有语言、设置和 history 键不变；新增 columns 和内部 locale_last_applied 协调键，历史上限由 30 扩为 50；会话加密、下载/帧缓存路径、包名和签名配置不变。`LocaleCoordinator.kt` 串行协调旧设置、应用内语言选择与 Android 的应用语言选择，恢复前台时重新检查系统语言，不用旧 DataStore 值覆盖用户在系统设置中刚选的应用语言。AppCompat 是新增 Apache-2.0 依赖，其他工具链与播放器版本保持不变。

## 构建与安装

本地已执行 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`：82 项 JVM 单测通过，Debug 和测试 APK 成功生成。`scripts/check-localization.py` 校验资源覆盖和参数；Actions 开发分支同时运行 KVM 设备测试。

普通运行上传 Debug APK、完整源码 ZIP、检查报告和设备报告。新增可选 `build_signed_candidate=true` 只复用既有 `release-signing` Secrets，运行 Release 单测/Lint/构建及证书固定校验，上传候选 APK、源码、校验文件、说明和公开验证报告作为 Actions 构件；没有创建/编辑 Release 或重新生成私钥的步骤。随后独立模拟器任务比较已发布 APK 与候选的包名/证书/递增版本号，实际覆盖安装并检查旧主题设置和历史保留。签名候选及实际覆盖升级已通过；下载时核对同一运行中的源码提交和 SHA-256。

Debug APK 与正式版证书不同，不能直接覆盖安装；卸载会丢失本地数据。若提供的同证书签名候选通过验证，可按覆盖安装升级，不需要卸载。真实设备和更多 Android/编解码器组合仍需后续验证，账号写入本轮不做实账号操作。

已知静态提示：API 33 localeConfig 在旧系统由 AppCompat 兼容；普通页面按需求限制竖屏，Lint 提示大屏形态可能受限；官网窗口保留必要 JavaScript；搜索备忘录 ISO 日期中的连字符保留原始查询语法。未通过全局关闭 Lint 隐藏问题。

## 实际验收结果（2026-10-26）

[验收提交 03612b8](https://github.com/Milenal82/sakugapp/commit/03612b8b4682fb0df9cad2635b66ffb38b0454c2) 的 [完整检查 40270216665](https://github.com/Milenal82/sakugapp/actions/runs/40270216665) 为 success：Debug JVM 单测、Lint、APK / 测试 APK / 源码构建通过；API 28 x86_64 KVM 的 **15 项设备测试全部通过**，日志记录 Starting / Finished 15 tests 和 BUILD SUCCESSFUL。82 项 JVM 单测无失败、错误或跳过；另有 14 项发布脚本安全测试通过，298 个基础资源的翻译覆盖/参数及 actionlint 检查通过。

设备覆盖：真实 ID 搜索、真实版权/动画师/Score、原生视频首帧/播放/倍速/全屏/严格帧索引/下载/wiki 返回；五批真实列表且 ≥300 个不重复 ID；360dp 小屏切换 1–4 列、Score 颜色与 ≥11sp、列数持久化、滚动收放、历史折叠与详情返回；简中资源及系统语言协调；旧 DataStore、Keystore 会话、严格帧像素、输入法组合/取消/补全；旋转锁开启时手动横屏、单次系统返回恢复竖屏，并保持同一个播放器、进度、暂停、1.5×、循环和静音。

[正式证书候选及真实覆盖安装 40269630646](https://github.com/Milenal82/sakugapp/actions/runs/40269630646) 同样 success：Release 82 项单测、Lint 0 errors / 20 warnings、assembleRelease 及固定证书/非 Debuggable/ELF 检查通过。模拟器从已发布 1.0.0/code 6 使用 install -r 覆盖到 1.1.0/code 7，**保留浅色主题、关闭标签换行和搜索历史**；未卸载、未清数据。该运行的应用源码为 841dfac，之后的验收修正仅涉及测试夹具；最终交付封装另从最新开发分支构建，完整提交见对应构件报告。

证书 SHA-256：`9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff`，与已发布 APK 相同。实际 ABI：`arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`，每个 32 位原生库都有 64 位对应版本。本地 Lint 0 errors / 6 warnings；CI 另有依赖更新提示，不将警告记作错误或通过屏蔽隐藏。

此前失败证据保留在 [初始设备报告](evidence/upgrade-first-device-run.txt)。修复了测试定位/线程、旋转退出策略快照及输入焦点；全屏返回测试还明确隔离 Android 首次沉浸式系统提示（此前返回注入收到 INJECT_EVENTS 权限异常），在确认应用窗口位于前台后只发送一次返回，不降低播放器状态断言。

旧的“不换行”设置仍可用：关闭时版权/动画师标签单行折叠，Score 独立显示。实际覆盖安装验证的是可见 UI 状态，不能以文件中的旧值替代。

## 尚未验证及范围限制

- 设备运行只覆盖 GitHub KVM Android 9/API 28 x86_64；尚未完成真实手机、更多 Android/编解码器、传感器自动旋转和 Android 13+ 预测返回手势验证。普通系统返回键和手动旋转已测。
- 新的系统应用语言状态协调在 API 28 模拟器经 AppCompat 验证；Android 13+ 系统设置页面的端到端交互尚未实测。
- 覆盖安装实测主题/换行/历史；会话加密和旧键兼容测试通过，但没有真实登录账号来验证跨版本账号、投票或评论写入。
- 动态偏移分页仍有站点实时变动导致遗漏的风险；没有声称固定快照。可选的低内存动态缩减预取量、完整性能/内存剖析仍待做。
- 本云环境下载 GitHub 构件的 Azure Blob 重定向被代理拒绝（CONNECT 403），本地无法取得该签名二进制；GitHub 内构建、校验和真实安装均成功。仓库拥有者可从成功运行的 Actions 构件下载 APK、完整源码和校验文件。
