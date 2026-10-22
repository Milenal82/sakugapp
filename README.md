# Sakugapp · Sakuga Mobile

Kotlin + Jetpack Compose + Material 3 的原生 Android 客户端首版（0.2.0），真实内容来自 [Sakugabooru](https://www.sakugabooru.com/post)。列表和详情没有使用 WebView，也不使用虚构帖子填充失败结果。

## 当前可用能力

- 深色两列网格，Coil 加载真实缩略图并保持比例。
- 真实 Score；缺失值显示“未知”。分类来自公开 tag summary，版权黄色、动画师紫色，未知类别中性色。
- 标签输入、点标签搜索、多标签/排除表达式、手动加载更多、帖子 ID 去重。
- 加载进度、真实空结果、网络失败/重试和分类不可用提示。
- 原生帖子详情，显示真实字段与原帖链接；返回保留当前列表和搜索条件，滚动状态保留在列表外。
- Media3 详情播放器：原生播放/暂停与进度条，静音、循环、全屏、后台暂停和释放。
- 原生“关于与许可”，GPL 全文和第三方声明随 APK 提供。

**验证边界：**公开 API、搜索、分页、标签分类、帖子 HTML、JPEG 与 MP4 Range 请求已现场验证。28 项 JVM 单元测试通过，Android Lint 无问题，Debug APK 编译及签名校验通过。当前环境没有模拟器或设备，**安装启动、列表交互、返回滚动、Media3 播放和全屏操作尚未在 Android 运行验证**。可达的 MP4 文件头不等同于设备解码通过。

## 下载和安装

打开 [GitHub Actions](https://github.com/Milenal82/sakugapp/actions/workflows/android.yml)，选择绿色成功的 **Android checks and artifacts** 运行，在 Artifacts 下载：

- `sakugapp-debug`：解压后的 `app-debug.apk` 可直接在 API 26+ Android 设备安装。
- `sakugapp-source`：内含 `sakugapp-source.zip`，完整项目、Gradle Wrapper、测试、文档与许可证；不包含密钥、本地配置或构建缓存。
- `check-reports`：单元测试与 Lint 报告。

GitHub 下载构件可能要求登录，构件保留期由仓库设置决定。安装 APK 时按 Android 提示允许对应来源；这是调试签名版本，不是生产发布包。无需用户安装 Android Studio。已有同包名且签名不同的应用时，Android 可能拒绝覆盖安装；卸载会删除其本地数据。

本地云端构建路径：`app/build/outputs/apk/debug/app-debug.apk`。最终交付中的 APK/ZIP 链接对应当次实际构建，不将旧构件当作新版本。

## 云端构建

工具链：JDK 17、Gradle Wrapper 8.13、AGP 8.9.2、Kotlin/Compose 插件 2.1.20、Compose BOM 2025.04.01、Android SDK Platform 35 与 Build Tools 35.0.0。最低 API 26，target API 35，JVM 编译目标 17。

当前配置好的云机器上：

```sh
cd /workspace/sakugapp
source /workspace/tooling/sakugapp-env.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./scripts/package-source.sh /workspace/artifacts/sakugapp-source.zip
```

helper 激活工作区内的 JDK/SDK/缓存及已配置的系统信任链，不在仓库中保存代理凭据。其他构建机器设置 `JAVA_HOME` 为完整 JDK 17，并设置 `ANDROID_HOME` 或未跟踪的 `local.properties` 的 `sdk.dir`。CI 安装经过校验的固定命令行工具，避免安装已废弃的 SDK `tools` 包。

如需刷新公开测试样本，执行 `python3 scripts/capture-public-fixture.py` 后审阅脱敏内容、来源记录并更新相应快照断言；日常测试使用已提交样本，CI 不依赖网站即时状态。

## 代码与文档

- `data/`：不可变模型、独立 JSON/分类解析、可取消 OkHttp 请求、分类缓存和分页合并。
- `ui/FeedViewModel.kt`：Coroutines + Flow、搜索取消、错误与重试状态。
- `MainActivity.kt` / `ui/VideoPanel.kt`：原生列表/详情与 Media3 控件。
- [接口调查与现场证据](docs/data-source.md)
- [Sakuga Extended 功能对照与完整需求待办](docs/feature-matrix.md)
- [测试报告与已知问题](docs/test-report.md)
- [许可证与第三方来源](docs/third-party-notices.md)

本轮基于明确的首版范围采用可取消 OkHttp 与独立解析器，单 Activity 中维护详情状态。完整需求中优先采用的 Retrofit、Navigation Compose、DataStore 将在多页面、设置和持久化功能加入时评估；当前没有声称已使用这些组件。

## 尚未完成

触屏预览、自动预览策略、骨架屏、搜索建议/历史、设置、账户/登录、收藏/评分写入、评论、Blog/Forum/Language、中文翻译、倍速/音量滑块、严格逐帧与扩展其他功能。没有对应的无效按钮或模拟成功结果。

分页使用站点偏移接口，动态插入/删除可能导致漏项；已去重，但不能承诺对动态数据集绝不遗漏。分类会话缓存可能滞后。进程死亡后完整列表恢复、GIF 动画、所有设备/编码兼容性和横屏专用布局仍待验证或开发。

没有 `abiFilters` 或仅限 32 位限制；实际 APK 的 Compose graphics native library 包含 `arm64-v8a`、`armeabi-v7a`、`x86_64`、`x86`。Media3 主要依赖设备解码器，本轮未引入 FFmpeg 原生解码库。

项目新编写代码采用 GPL-3.0-only，见 [LICENSE](LICENSE)。Sakuga Extended 只用于功能/协议调查，未复制其实现或资源。站点内容仍属于各自权利人；依赖保留各自许可证。
