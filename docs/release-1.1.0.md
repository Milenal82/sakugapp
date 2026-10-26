# 1.1.0 增量升级（开发分支）

基于远端 main `cbe04535f88471e070196ecc559e769d00895b5c`，保留用户更新的三语 README、已发布 1.0.0、`com.sakugapp` 和既有正式签名配置。开发分支 `feature/browsing-search-zh-cn`；versionName 1.1.0、versionCode 7。此轮不合并 main、不创建发布标签、不替换现有 Release APK。

| 九项要求 | 实现与源码 | 验证范围 |
| --- | --- | --- |
| 加载更多 | `SakugaRepository.kt` 每页由 20 调至实测支持的 60，构造参数可配置（1–100）；`MainActivity.kt` 剩余约 12 个条目时预取，保留无限滚动、加载进度与手动重试；`FeedViewModel.kt` 防重入、取消旧查询；完整重复页暂停自动请求 | 官网连续五页共 300 个不重复 ID；真实脱敏样本单测检查完整顺序、去重与末页停止；设备验收以当前 PR / Actions 的结果为准 |
| 搜索区收放 | `ScrollChrome.kt` 观察真实已消费滚动，上滑累计 64dp 收起、下滑 28dp 展开；200ms 位移动画；搜索区覆盖层与固定列表坐标避免重排跳动；输入、联想、历史面板展开时保持显示；到顶部自动展开；1dp OutlineVariant 分隔线 | 阈值/方向变化单测通过；设备交互验收见当前 PR / Actions 构件 |
| Score 红色 | `AppTheme.kt` 深色 #FF5252、浅色 #B71C1C；`PostCaption.kt` 标签密度策略；Score 单独排版不会被长标签省略 | 深浅背景对比度均 ≥4.5:1；真实值与缺失值保留；设备用例检查颜色及 4 列可见性 |
| 紧凑详情 | `MainActivity.kt` 非标签元数据使用 FlowRow、较小字段名和正常字段值；文件大小来自已核实 file_size；长来源可选择复制，HTTPS 来源可打开 | 不改 Tags、Score/Vote、评论字体与布局；缺字段不编造，时长仍由 Media3 实际时长控制显示 |
| 简体中文 | `values/strings.xml`、`values-zh-rCN`、`values-zh-rTW`、`values-ja`；AppCompat Locale API 与 Android 13 localeConfig；首次默认系统简中→简中、其他系统语言→英文；手动保留 English/日本語/繁體中文及新增简体中文 | 298 个基础字符串，所有可翻译项的简中/繁中/日语资源和格式参数校验通过；不翻译标签、姓名、作品、评论、搜索语法及许可证原文 |
| 全屏旋转 | `VideoPanel.kt` 同一 ExoPlayer；普通页面竖屏，全屏 FULL_USER 尊重旋转锁；手动旋转、恢复跟随系统；返回优先退出全屏；Dialog 处理系统栏/刘海；`AndroidManifest.xml` 对旋转配置变更不重建 Activity | 新增本地测试视频设备用例检查播放器实例、暂停/进度/倍速/循环/静音和返回；实际结果见当前 PR / Actions 构件 |
| 1–4 列 | `PreferencesStore.kt` 新增独立 columns 键，默认 2；首页紧凑按钮和设置入口；切换保持锚点，1/2/3/4 列自适应间距和 14/12/11/11sp 字体；高密度尽量保留一版权、一动画师及完整 Score | 不改变媒体 URL 或图片缓存；新增 360dp 小屏设备验收用例，实际结果见当前 PR / Actions 构件 |
| 历史整合 | 只读 `settings_v1` 的旧 history 键；入口默认折叠，可再点击收起；提交搜索才记录，最近优先、去重、最多 50；支持单删、清空 | 旧 30 条不会被清空；新增隔离 DataStore 旧键/设置/历史兼容设备测试，实际结果见当前 PR / Actions 构件 |
| 真实联想 | `TagSuggestions.kt` 使用实测 `/tag.json?name=...&limit=8`，分类只读 type；300ms debounce、IME 组合输入暂缓、过期请求取消、120 秒/32 查询缓存；与列表请求共用 650ms 起始间隔；`SearchEditing.kt` 按光标只替换当前词，保留 ~ / - 和所有其他条件；普通/组合搜索共用 | 真实响应解析、缓存/过期、403 不重试、错误不妨碍手动搜索单测通过；IME 和取消的设备用例结果后补 |

当前交付入口：[草稿 PR #1](https://github.com/Milenal82/sakugapp/pull/1)、[当前检查](https://github.com/Milenal82/sakugapp/pull/1/checks)、[完整文件清单](changed-files-1.1.0.md)。构建及运行结果以相应提交的 CI 报告为准；下面保留历史失败证据，不能将旧提交的失败当作新提交的通过。

## 数据、兼容与性能

[现场接口证据](evidence/browse-upgrade-2026-10-26.json) 记录日期、URL、状态、摘要及五批 ID；测试样本只保留公开必要字段，移除上传者、创建者、来源等字段。不保存 Cookie、CSRF 或 HTML。网站的部署 JS 确认使用 TagCompletion/TagCompletionBox 与 `/tag/summary.json` 本地索引；应用用文档化的 tag.json 公开查询作为有界原生替代，不复制或执行网页 JS。

保留网站 page 从 1 开始的偏移语义、排序和完整查询表达式；动态插入/删除仍可能产生偏移遗漏，ID 去重不等于固定快照。网络失败不会推进 nextPage；已读列表保留，重试请求同页。全页重复数据停止自动链式请求，允许用户手动继续下一页。只有可见附近图片会由 LazyGrid/Coil 加载；一个活动预览，离开视区释放，没有为每批 60 条同时下载全部媒体。

DataStore 文件仍是 `settings_v1`，既有语言、设置和 history 键不变；新增 columns 和内部 locale_last_applied 协调键，历史上限由 30 扩为 50；会话加密、下载/帧缓存路径、包名和签名配置不变。`LocaleCoordinator.kt` 串行协调旧设置、应用内语言选择与 Android 的应用语言选择，恢复前台时重新检查系统语言，不用旧 DataStore 值覆盖用户在系统设置中刚选的应用语言。AppCompat 是新增 Apache-2.0 依赖，其他工具链与播放器版本保持不变。

## 构建与安装

本地已执行 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`：82 项 JVM 单测通过，Debug 和测试 APK 成功生成。`scripts/check-localization.py` 校验资源覆盖和参数；Actions 开发分支同时运行 KVM 设备测试。

普通运行上传 Debug APK、完整源码 ZIP、检查报告和设备报告。新增可选 `build_signed_candidate=true` 只复用既有 `release-signing` Secrets，运行 Release 单测/Lint/构建及证书固定校验，上传候选 APK、源码、校验文件、说明和公开验证报告作为 Actions 构件；没有创建/编辑 Release 或重新生成私钥的步骤。随后独立模拟器任务比较已发布 APK 与候选的包名/证书/递增版本号，实际覆盖安装并检查旧主题设置和历史保留。签名与设备结果必须以最终运行记录为准，此文不将准备好的用例当作通过。

Debug APK 与正式版证书不同，不能直接覆盖安装；卸载会丢失本地数据。若提供的同证书签名候选通过验证，可按覆盖安装升级，不需要卸载。真实设备和更多 Android/编解码器组合仍需后续验证，账号写入本轮不做实账号操作。

已知静态提示：API 33 localeConfig 在旧系统由 AppCompat 兼容；普通页面按需求限制竖屏，Lint 提示大屏形态可能受限；官网窗口保留必要 JavaScript；搜索备忘录 ISO 日期中的连字符保留原始查询语法。未通过全局关闭 Lint 隐藏问题。

## 首轮 CI 与待复验项

开发分支 `1a7ca39` 的 [Debug 构建](https://github.com/Milenal82/sakugapp/actions/runs/40261372845) 成功；首轮 14 项设备测试有 4 项失败，记录保留在 [初始失败证据](evidence/upgrade-first-device-run.txt)。已修正一处简中用语（菜单），并改正测试在带独立点击动作的标签树上读取父节点、Grid 按钮整串匹配，以及在非主线程读取 Media3 状态的问题。保留原有真实标签、播放器状态及持久化断言，不将“用例已编译”当作通过；修复后重新验证。

[首个签名候选及真实覆盖升级](https://github.com/Milenal82/sakugapp/actions/runs/40261375158) 已成功：正式 Release 单测/Lint/构建、固定证书校验通过；独立模拟器安装已发布 v1.0.0，保存浅色设置及真实 ID 搜索记录，随后仅使用 install -r 覆盖候选，检查主题和历史保留，没有卸载或清数据。该运行早于上述用语修正，最终交付 APK 以修复后运行和提交为准。

第二轮 `954d3e0` 的签名候选与覆盖升级再次成功，公开证书 SHA-256 为 `9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff`，候选和已发布 APK 一致。设备测试进展为 12/14，通过了真实浏览/播放/逐帧、简中切换、旧数据、IME 等；剩余为旋转阶段超时和高密度卡片的详情点击。后续修正高密度预览按钮占位与点击定位，补充明确的旋转阶段诊断，并增加系统语言协调设备用例。最终结论以新提交的完整运行结果为准。

第三轮 `e78aca5` 的 15 项设备测试中 13 项通过；新增系统语言协调用例通过。完整错误堆栈确认播放器已完成手动横屏且进度/暂停/速度/循环/静音断言通过，超时发生于按返回后恢复竖屏，因此将 Dialog 自身的返回分发、原生返回键和方向恢复统一到同一退出方法。网格返回测试改用实际可见且未被搜索区覆盖的缩略图，不用固定行高假设。补全也增加光标位于 `source:` 等字段名中段时的保护，防止将完整字段条件替换成普通标签。
