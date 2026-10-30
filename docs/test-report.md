# v1.2.0 / code 13：字号与预览标签行距

本轮只增加两项外观设置，沿用现有包名和签名。`AppearanceDeviceTest` 定向检查 75%/125% 即时生效、系统字号不变、独立 DataStore 读取磁盘设置、Activity 重建、Media3 原生时间文字不累积缩放，以及预览行距、视频尺寸、48 dp 触控区、搜索与播放面板布局。新增资源补齐全部八种 UI 语言。

设备结果以[当前提交证据](https://github.com/Milenal82/sakugapp/blob/evidence/maintenance/report.json)的 source_commit 与 AppearanceDeviceTest 用例结果为准，不以较早版本的通过结果替代。正式构建和签名报告由 [Release 工作流](https://github.com/Milenal82/sakugapp/actions/workflows/release.yml)及对应 Release 附件提供；公开发布还要求旧版实际覆盖安装和 v1.1.0 证书比较通过。未逐一验证实体设备与所有系统字号组合。

以下保留历史记录。

# 1.2.1 / code 12 收藏备份与界面整理（2026-10-30）

本轮只做收藏备份、搜索工具、浏览历史、设置分组、三语 README 和提供的图标调整。包名、数据库版本、设置键、下载记录与正式签名配置保持不变，不创建公开 Release。

本地已执行并通过：

- `python3 scripts/check-localization.py`：425 项资源、全部 8 种界面语言；含参数与复数检查，未作母语审校。
- `./gradlew --no-daemon testDebugUnitTest --tests '*BackupImportTest' --tests '*LibraryCodecTest' --tests '*PersonalDataTest' assembleDebugAndroidTest`：22 项单元测试通过，测试 APK 编译成功。
- `./gradlew --no-daemon lintDebug assembleDebug`：通过。本地 Lint 0 错误 / 15 警告；不使用 baseline 或关闭检查。
- 新维护工作流的 actionlint、Shell 语法与证据脚本编译检查通过。

数据用例覆盖可移植收藏夹键、旧 v1/v2 格式、名称/备注冲突、顺序/封面、多对多关系、独立备注、重复合并、版本/大小/关系校验。新的 `BackupRestoreDeviceTest` 在独立 Room 数据库中执行源库导出、关闭源库、空库导入、逐项核对、关闭并重新打开；另测同名收藏夹、旧格式、备注选择、损坏及超限时原数据不变。`MaintenanceUiDeviceTest` 只检查本轮三个页面及原有设置持久化，使用 API 28 Nexus S 实际窄屏、1.3 倍字体、深浅主题，保存原始截图。

本地没有 KVM，不把测试 APK 编译当作设备测试通过。设备用例由 [Focused maintenance validation](https://github.com/Milenal82/sakugapp/actions/workflows/maintenance.yml) 执行；**实际提交、运行状态、5 项设备结果、原始截图及 APK 构件链接，以[本轮证据](https://github.com/Milenal82/sakugapp/blob/evidence/maintenance/report.json)的 `source_commit` 核对**。这些用例尚未在本地运行。现有主 CI 没有删除或关闭；main 推送后原有自动检查的同提交结果也在该报告中记录，不额外手动重复播放器全套测试。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`；CI 构件 `maintenance-debug-apk`。数据格式见[备份说明](backup-format.md)。未覆盖所有实体设备、OEM 文件提供程序、横屏和每种语言的人工视觉审查；旧测试结果不能代替本轮结果。

首次 CI [40966510830](https://github.com/Milenal82/sakugapp/actions/runs/40966510830) 在源码 `de2d113` 上完成 4/4 项数据库设备测试：空库恢复/重开核对、同名夹独立恢复、旧格式与冲突、失败原子性全部通过。页面用例暴露了放大字体下浮块文本测量边界，后续调整浮块本征宽度，并检查实际字形边界和省略状态。现有回归测试中的旧按钮文案定位改为组件标识；字体放大时先滚动到收藏夹再点击，不删除原断言。

该提交的原有主 CI 构建、Debug/Release 各 120 项单测和 Lint 已通过（CI Lint 0 错误 / 32 警告）。API 35 任务在安装 Google 系统镜像时失败：`Error on ZipFile unknown archive`，未启动模拟器、未产生 JUnit 结果；这是该次下载失败的实际证据，不视为设备用例通过。后续同提交验证仍以本轮证据中的 SHA/结论为准。

以下保留历史开发记录。

# 下载、组合搜索、返回导航与播放器面板增量

版本保持 1.2.0 / code 11。[变更、检查命令与文件清单](incremental-search-download-controls.md)。本地 Debug / Release 各 112 项单测、Lint 0 错误、Debug 和未签名 Release 预检通过；411 项八语资源检查、16 项脚本测试和工作流校验通过。签名候选及 API 28 / API 35 设备结果按[证据报告](https://github.com/Milenal82/sakugapp/blob/evidence/history-tags-refresh/report.json)中的 source_commit 核对，不以旧提交结果代替本轮结果。以下为之前开发轮次记录。

# 1.2.0 / code 11 七项开发验证

本地基线与完整 JVM / Lint / Debug / Release 预检及新设备用例说明见 [开发记录](development-1.2.0.md)。[本轮证据分支](https://github.com/Milenal82/sakugapp/blob/evidence/history-tags-refresh/report.json)记录实际提交、Actions 状态、单测/设备计数、Lint 和原始截图；应用代码 14f15a2 的完整检查已通过：Debug/Release 各 107 项单测、33/33 项设备测试、正式签名及覆盖升级检查；CI Lint 0 错误 / 23 警告。本地 Lint 0 错误 / 6 警告。最终文档与截图同步构件以证据报告中的提交为准。以下保留历史版本记录。

# 正式 v1.1.0 验证（2026-10-27）

[完整检查](https://github.com/Milenal82/sakugapp/actions/runs/40445851626)通过：96 项 JVM 单测、Debug 构建、Lint 无错误及 API 28 KVM 24/24 项设备测试；包括实际字形显示、分页/搜索、严格帧、旋转、收藏迁移和系统 JSON 导入导出。[正式证书候选和覆盖升级](https://github.com/Milenal82/sakugapp/actions/runs/40444402161)通过。最终标签 APK 会独立执行正式签名构建与覆盖安装验证，公开发布以该结果为准，详见 [发布检查](release-1.1.0.md)。以下为历史候选版本记录。

# 1.2.1 验证入口

[验收检查](https://github.com/Milenal82/sakugapp/actions/runs/40369422178)通过：96 项 JVM 单测、Debug 构建、Lint 0 错误、24/24 项 API 28 KVM 设备测试及 13 张截图。迁移、系统 JSON 文件选择器和界面覆盖范围见 [1.2.1 记录](release-1.2.1.md#实际验收结果2026-10-27utc8)。以下保留旧版结果，不能代替本轮测试。

# 1.2.0 验证入口

本轮逐阶段检查、20 项设备用例范围、截图和未验证事项见 [1.2.0 升级记录](release-1.2.0.md#运行证据与明确限制)。[验收提交 df836f4 的检查](https://github.com/Milenal82/sakugapp/actions/runs/40303737605)成功：96 项 JVM 单测、Lint 0 错误、20/20 项 API 28 KVM 测试、5 张 PNG 实际导出。[正式签名与覆盖升级](https://github.com/Milenal82/sakugapp/actions/runs/40303730796)同样成功。最终封装提交见构件元数据；下方为旧版历史。

# 浏览与搜索开发候选验证（2026-10-26）

完整范围、成功运行、修复证据及未验证事项见 [1.1.0 验收记录](development-browsing-search.md#实际验收结果2026-10-26)。开发分支验收提交 03612b8 的 [CI](https://github.com/Milenal82/sakugapp/actions/runs/40270216665) 全部成功：82 项 JVM 单测、Debug 构建/Lint、15 项 API 28 KVM 设备测试。正式证书 Release 构建及从已发布 1.0.0 实际覆盖升级成功，保留主题、换行和历史；最后封装提交以候选构件报告为准。本地 Lint 0 errors / 6 warnings，CI Release 0 errors / 20 warnings；14 项发布脚本安全测试、资源覆盖与 actionlint 均通过。

以下为历史版本记录，与本轮结果分开保存。

# 0.5.0 当前验证

本轮六项改动与验证范围见 [release-0.5.0.md](release-0.5.0.md)。本地 73 项单测及完整构建已通过；设备最终实际结果见交付 CI 链接。以下保留历史版本说明，不混同结果。

# 0.4.0 验证

本轮标签/繁體中文/组合搜索/逐帧改动与测试范围见 [release-0.4.0.md](release-0.4.0.md)。本地构建、单测、Lint 与测试 APK 已成功完成；KVM 的最终实际运行结果见交付链接。下面保留 0.3.0 的历史记录，不混同版本结果。

# 0.3.0 验证报告（2026-10-22）

本轮在 0.2.0 已通过的构建基线上继续开发。DATA.zip 是旧源码/报告副本，未执行其中脚本或覆盖仓库；旧 CI 的 6 条 GradleDependency 为版本更新建议。JDK/Gradle/SDK 主工具链保持兼容。

## 本地构建与 JVM 测试

执行 `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`，最终构建结果见交付运行。当前新增测试后共有 **53 项 JVM 单测，0 失败、0 错误、0 跳过**：DataTest 15、RepositoryTest 7、FeedViewModelTest 4、LiveFixtureTest 2、CommunityTest 16、PreferencesAndFramesTest 9。

新增覆盖：unknown-user / wrong-password 即使 success=true 不得登录；身份必须服务端确认；CSRF 解析；凭据只放 POST body；投票匹配帖子 ID、真实 Score 与整数溢出；查询票值不发送 score；评论创建/编辑/删除字段、归属检查、真实脱敏样本与时间码；历史去重/容量；严格帧索引边界；浏览 URL 欺骗/非 HTTPS 拒绝；只有 en/ja。

Lint 当前 **0 errors / 1 warning（SetJavaScriptEnabled）**：官网账户/Forum/Blog 的独立浏览窗口确实需要官网 JavaScript。已检查同源白名单、禁混合内容/文件/内容 URI、禁第三方 Cookie、无 JS bridge、外部导航过滤、无忽略 SSL 错误；不关闭该警告。CI 可能另列新版本依赖建议，不混同为错误。此前 LocalContext 强转 Activity 的 Lint 错误已改为 LocalActivity，未使用 baseline/disable 隐藏。

## 已完成的设备测试

在本云机器准备 Android 9/API 28 x86_64 软件模拟器（无 KVM）。成功运行：

- `DeviceTests`：3/3 通过。Android Keystore/AES-GCM 会话恢复与清理；DataStore 设置/历史跨实例保留；12 帧 VFR 且含 B/P 非关键帧的视频真实索引读取，按 0、1、7、8、7、11、0 顺序检查每帧像素颜色。
- `UiSmokeTest`：1/1 通过。启动原生界面、设置滚动、English/日本語切换、账户页面和空凭据按钮禁用。

原始结果在 `evidence/device-tests-api28.txt`、`evidence/native-ui-api28.txt`。测试片段先前生成过程产生丢帧，已修复编码时间基/MP4 edit list；FFprobe 与设备均确认 12 帧可解码，而不是修改断言去迁就错误样本。片段是测试图案，不是伪造网站数据，不进入正常 APK 内容。

## 真实浏览/播放测试与环境问题

`PublicBrowseTest` 已实际搜索公开 ID 319947、进入详情、收到 Media3 原生 onRenderedFirstFrame（真实视频已解码到表面）。此后软件模拟器的 System UI ANR 遮挡了播放控制；再次尝试遭 `android.view.ThreadedRenderer.finalize() timed out after 10 seconds` / instrumentation system crash。该流程**未通过**，不能宣称本地完整播放/全屏/真实视频帧分析全部已验证。

云模拟器使用正常云代理；先验证网站 TLS 链，再把云环境已有的公开代理 CA 加入此测试模拟器系统信任库。没有在 APK 中增加测试 CA、关闭证书校验或忽略 SSL 错误。系统配置、代理证书与账户资料不进入源码 ZIP。

GitHub KVM 运行 [39774912127](https://github.com/Milenal82/sakugapp/actions/runs/39774912127) 已成功完成：构建任务与设备任务均为 success。设备日志明确记录 Starting/Finished **5 tests**，并且 BUILD SUCCESSFUL。3 项 DeviceTests、1 项 UiSmokeTest、1 项 PublicBrowseTest 全部执行，覆盖真实 ID 搜索、真实视频原生首帧/播放状态、倍速、全屏、下载原始视频并按真实索引前进/后退、返回保留查询。摘要在 evidence/kvm-device-results.txt，完整报告在 CI 的 device-test-reports 构件。没有把本地软件环境失败记作成功；此次通过来自独立 KVM 环境。测试只读取真实内容，不向官网写入票值/评论。

## 官网接口与受保护操作

- 帖子/分类/缩略图、普通及排除搜索、分页、原帖、MP4 Range 读取已经验证。
- 评论 ID 317910 的公开接口现场 200 / 3 条；脱敏样本保留真实正文与时间码。Blog、Forum、en/ja 页面现场 200。
- 登录检查空账户响应真实为 unknown-user + success:true，客户端正确拒绝；匿名查询当前 vote 的 POST（没有 score）现场 403。GET vote 实测 404，已改为正确 POST。
- 注册/重置在官网窗口执行；没有获取真实密码/测试账号，没有运行官网成功认证、投票更改、评论写入或资料修改。请求流程与错误测试只证明代码协议，不等同实账号通过。
- 官方聚合 JS `.jsout` 资源访问得到 403，未绕过；协议以可检查公开源码、实际 HTML 与接口观察相互核对，不宣称已审阅无法取得的部署资源。

## 交付与后续

CI 构建 APK/测试 APK、单测、Lint、签名/ABI 检查、完整源码 ZIP，并执行 KVM 设备测试、上传报告。最终提交与运行结果在交付报告列出。新的 ELF/签名/测试摘要位于 `evidence/local-tests.json` 和 `evidence/abi-check.json`。

未验证或未实现的项目明确列入 `feature-matrix.md`。尤其不能把 MockWebServer 的写入成功当作官网账号行为，也不能把帧索引 API 自动推广为所有设备/格式都支持。
