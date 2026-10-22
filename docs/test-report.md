# 构建与测试报告

日期：2026-10-22（UTC+8）。对象：Sakugapp 0.2.0 首个真实数据实现。机器上 JDK 17.0.18+8、Gradle 8.13、AGP 8.9.2、Kotlin 2.1.20、SDK/target 35、Build Tools 35.0.0，min SDK 26。

## 基线与本地检查

修改前先运行现有 `assembleDebug`，结果通过；仓库从 `eeb5d8e` 继续开发，未重新初始化、reset 或改写历史。

最终必须运行的命令：

```sh
source /workspace/tooling/sakugapp-env.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

| 检查 | 结果 | 范围 |
| --- | --- | --- |
| `testDebugUnitTest` | 28 通过，0 失败、0 错误、0 跳过 | DataTest 15，RepositoryTest 7，FeedViewModelTest 4，LiveFixtureTest 2 |
| `lintDebug` | 通过，No issues found | 静态检查；没有用 baseline 或 disable 关闭问题 |
| `assembleDebug` | 通过 | `app/build/outputs/apk/debug/app-debug.apk` |
| `apksigner verify` | 通过，APK v2 调试签名 | 未使用发布密钥 |
| Wrapper/分发包完整性 | 官方 SHA-256 一致 | Wrapper JAR 与 Gradle 8.13 ZIP；distributionSha256Sum 保留 |
| SDK CLI 完整性 | 官方仓库元数据 SHA-1 一致，并记录 SHA-256 | 固定 11076708 ZIP；CI 同样校验 SHA-256 |
| 实际 APK ABI | arm64-v8a、armeabi-v7a、x86_64、x86 | `libandroidx.graphics.path.so`；ELF 头确认 ARM64/x86_64 为 64 位；无 32 位限定，无 FFmpeg native 扩展 |

最新本地套件计数及 APK 摘要见 `docs/evidence/local-tests.json`。报告来自实际执行，不把 NO-SOURCE 的 Java 编译任务、SKIPPED 的 Kotlin 配置检查或未存在的 Android instrumentation 测试计为已通过测试。

真实公开样本来自 `scripts/capture-public-fixture.py`，保留字段白名单与 provenance；2 项 LiveFixtureTest 和 1 项 RepositoryTest 使用这些样本。其余手写 JSON/MockWebServer 响应明确用于边界与故障注入，包括：缺失/null/非数字 Score、真实 0、未知分类、别名、无效 URL、坏 JSON/HTML、去重、空页、403/421/429/503、编码、分类请求失败、取消搜索、同页重试和详情返回不重载。

## 真实网站现场检查

- 帖子数组、第一页/第二页、标签搜索、空结果、分类摘要、原帖 HTML 成功。
- 多标签/排除查询 `jujutsu_kaisen_series animated -genga` 返回满足条件的两个帖子。
- JPEG 缩略图返回 HTTP 200，MP4 的 64 KiB Range 请求返回 HTTP 206 和有效 ftyp 头。
- 分类用真实 summary、帖子 HTML sidebar 交叉确认，未从标签名称猜测。

详细地址、ID、类型和时间见 `data-source.md` 与 `evidence/live-source-2026-10-22.json`。不提交媒体文件或账号数据。

## GitHub Actions

原有运行 [39747155731](https://github.com/Milenal82/sakugapp/actions/runs/39747155731) 在 `android-actions/setup-android@v3` 失败，日志明确显示它执行 `sdkmanager tools`，报 `Failed to find package 'tools'`，应用编译步骤尚未执行。这是 CI 工具安装问题，不是当时本地 APK 构建失败。

本轮移除此步骤，固定 Ubuntu 24.04、JDK 17、经过校验的 CLI ZIP，只安装 platform 35/build-tools 35/platform-tools。工作流自动运行全部单测、Lint、APK 构建、签名与 ABI 检查，并上传 APK、源码 ZIP 和测试报告。源码用 `git archive HEAD` 打包，额外校验不含私钥、本地配置或缓存，并确认 Wrapper JAR 存在。

首个实现提交 `980c3918d656b0fd3df811a09524f9a4349eb89a` 的实际远端运行 [39753225420](https://github.com/Milenal82/sakugapp/actions/runs/39753225420) 已成功完成：全部单测/Lint/构建、APK 签名/ABI 检查、源码 ZIP 打包和三个 artifact 上传步骤均成功。随后仅整理许可证文本与验证文档，没有改变应用逻辑；最终提交的新运行链接和下载链接在交付报告提供。源码中的本地验证结果不替代远端结果。

## 尚未执行的运行测试

本机没有安装模拟器、system image，也没有连接设备或 `/dev/kvm`。因此以下均**未验证**：安装及启动、真实列表呈现/滑动、软键盘搜索、点标签、详情返回滚动位置、视频解码与播放/暂停/进度拖动/静音/循环/全屏、旋转和生命周期恢复。构建与 JVM 测试通过不能替代这些测试。

下一阶段先在可用的 Android 设备/云模拟器上验证上述流程；不要求用户安装 Android Studio。随后补充 UI/播放器测试、触屏预览、设置与搜索历史，最后再做认证写操作、站点入口与扩展高级能力。
