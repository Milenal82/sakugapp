# 1.2.1 验证入口

本轮变更、迁移与运行验证见 [1.2.1 记录](release-1.2.1.md)。以下保留旧版结果，不能代替本轮测试。

# 1.2.0 验证入口

本轮逐阶段检查、20 项设备用例范围、截图和未验证事项见 [1.2.0 升级记录](release-1.2.0.md#运行证据与明确限制)。[验收提交 df836f4 的检查](https://github.com/Milenal82/sakugapp/actions/runs/40303737605)成功：96 项 JVM 单测、Lint 0 错误、20/20 项 API 28 KVM 测试、5 张 PNG 实际导出。[正式签名与覆盖升级](https://github.com/Milenal82/sakugapp/actions/runs/40303730796)同样成功。最终封装提交见构件元数据；下方为旧版历史。

# 1.1.0 验证（2026-10-26）

完整范围、成功运行、修复证据及未验证事项见 [1.1.0 验收记录](release-1.1.0.md#实际验收结果2026-10-26)。开发分支验收提交 03612b8 的 [CI](https://github.com/Milenal82/sakugapp/actions/runs/40270216665) 全部成功：82 项 JVM 单测、Debug 构建/Lint、15 项 API 28 KVM 设备测试。正式证书 Release 构建及从已发布 1.0.0 实际覆盖升级成功，保留主题、换行和历史；最后封装提交以候选构件报告为准。本地 Lint 0 errors / 6 warnings，CI Release 0 errors / 20 warnings；14 项发布脚本安全测试、资源覆盖与 actionlint 均通过。

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
