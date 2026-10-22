# Sakugapp · Sakuga Mobile 1.0.0

Kotlin / Compose / Material 3 原生 Android 客户端，内容来自 [Sakugabooru](https://www.sakugabooru.com/post)。延续已由用户测试的 0.5.0，保留现有功能。首页、列表、搜索、详情、设置和社区操作均为原生界面；不使用虚构帖子、标签、Score、登录或操作结果。

正式身份：应用名 **Sakugapp**，applicationId **`com.sakugapp`**，versionName **`1.0.0`**，versionCode **`6`**，发布标签 **`v1.0.0`**。本次只准备 GitHub Release **草稿**，不公开发布。签名、安装与实际验证范围见 [正式版准备报告](docs/release-1.0.0.md)。

## 本轮功能

- **0.5.0**：“作”字自适应图标；可持久保存的浅色/深色背景；详情原视频下载；详情标签边框；动画师前方独立 `?` 入口打开对应 Wiki；组合搜索新增 `score:>=3`、`score:>=100` 快捷条件。详见 [0.5.0 说明](docs/release-0.5.0.md)。
- **0.4.0 修复**：隔离官网分类汇总中的 U+2028 异常记录，恢复预览图下方彩色标签；加入繁體中文；组合搜索保留原始指令、OR 建议前缀和已有筛选，内置用户提供的完整指令备忘录；逐帧入口置于播放器下方，新增指定帧编号及真实索引滑杆。详见 [本轮修复与验收](docs/release-0.4.0.md)。
- **Posts**：两列统一 16:9 长方形媒体区域，默认按原比例 Fit，可选裁切；下方只显示黄色版权标签、紫色动画师标签和真实 Score，用空格分隔，默认换行。浅色模式使用同色系深色文字以保证对比度。详情标签添加边框，点击名称搜索、动画师旁 `?` 打开 Wiki。
- **加载与搜索**：脉动骨架屏、错误/空状态、分页去重、450ms 防抖建议、AND / ~OR / 排除条件组合、完整排序/rating 筛选、指令备忘录、最近 30 条搜索历史、完整历史列表与清空。通配符、混合 OR 与重复字段显示网站限制提示，不静默改写查询。
- **触屏预览**：点播放按钮启停，最多一个活动视频，移出可视区释放；自动预览和计费网络策略可设置，预览默认静音。
- **播放器**：Media3 播放/暂停、进度、全屏、旋转适配、音量、静音、循环、0.1×–3× 共 9 档倍速；镜像、亮度、对比度、饱和度；当前画面截图分享。
- **下载**：视频详情点击 Download video / 下載影片，通过 Android 系统“另存为”选择位置。流式保存 API 返回的真实原文件，不猜测地址、不申请广泛存储权限；有进度、取消与失败状态。下载时保持详情页开启，离开会取消并尝试删除不完整文件，不承诺后台任务。
- **严格帧分析**：独立模式，明确下载原视频（≤200 MiB）到私有缓存，从第 1 个真实帧索引开始前后浏览，不按 FPS 或时间步长猜帧。要求 Android 9/API 28+、设备提供实际帧数和索引解码、视频像素不超过 3840×2160。不支持时明确报错；不以近似 seek 替代。切入释放播放解码器，关闭后重新准备播放器。
- **账户**：官方 HTTPS 登录检查 + CSRF / Cookie；密码不落盘；会话 Cookie 与服务端确认的身份用 Android Keystore/AES-GCM 保存；退出清理；官方账户管理/注册/重置入口。
- **Vote**：官方 0–3 票值，0 为解除，3 为网站收藏级；当前票值查询不带 score；帖子 Score 只按服务端响应更新。
- **评论**：真实分页阅读、时间码定位、登录后发表、本人编辑/删除并确认；成功后重读服务器，不插入虚构评论。
- **Blog / Forum**：受限独立浏览窗口、返回应用、同源会话衔接；帖子链接返回原生界面。注册/重置使用官网原流程，不模拟注册成功。
- **Language**：**English / 日本語 / 繁體中文**。繁体中文翻译原生界面；官网仅有 en/ja，中文模式的官网页面使用 en，不声称官网提供中文。站点内容、标签名称、搜索语法与许可证保持原文。
- **其他扩展替代**：GIF 图片、可见缩略图平均色、关于/许可、图片加载策略、媒体缓存清理。完整状态见 [功能对照](docs/feature-matrix.md)。

## 下载和安装

正式版本入口：[GitHub Releases](https://github.com/Milenal82/sakugapp/releases)。草稿仅有仓库授权用户可见；在所有者配置正式签名并完成验证前，草稿**没有可安装的正式 APK**。签名工作流成功后提供 `Sakugapp-v1.0.0-release.apk`、`Sakugapp-v1.0.0-source.zip`、`SHA256SUMS.txt`、发布说明和验证报告；预检只提供源码及标明 unsigned 的报告，不把未签名 APK 作为附件。

正式 APK 下载后，对照 `SHA256SUMS.txt` 校验 SHA-256；有 SDK 时可用 `apksigner verify --verbose --print-certs` 核对发布说明中的公开证书指纹。在 Android 文件管理器中打开 APK，按系统提示允许该来源安装。最低 Android 8.0 / API 26，严格帧分析需 API 28+，无需 Android Studio。

升级正式版时，下载更高 versionCode 且使用同一正式证书签名的 APK，直接覆盖安装以保留本地数据；签名不匹配时不要更换包名或随意卸载，应先核对下载来源和指纹。**Debug 切换正式版**通常必须先退出测试版账户、记录必要设置，再卸载旧 Debug 版并安装正式 APK；卸载会删除本地历史、设置及会话。不同 CI 的 Debug 密钥也可能不同。

测试版仍由 [Android checks and artifacts](https://github.com/Milenal82/sakugapp/actions/workflows/android.yml) 提供 `sakugapp-debug`、`sakugapp-source`、`check-reports` 和 `device-test-reports`。Debug APK 使用调试证书，不能充当正式版；构件下载可能需要 GitHub 登录。

## 云端构建与验证

```sh
cd /workspace/sakugapp
source /workspace/tooling/sakugapp-env.sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./scripts/package-source.sh /workspace/artifacts/sakugapp-source.zip
```

正式发布流程为 [Signed Release draft](https://github.com/Milenal82/sakugapp/actions/workflows/release.yml)，仅允许从对应版本标签构建，使用受保护的 `release-signing` 环境和固定证书 SHA-256，缺少签名即失败。所有者的密钥生成、加密备份和五项 Secrets 配置见 [签名操作手册](docs/release-signing.md)；不要将私钥或密码贴到聊天。无签名时可运行明确的预检：

```sh
python3 -m unittest discover -s scripts/tests -v
./gradlew testReleaseUnitTest lintRelease assembleRelease -PreleaseUnsignedCheck=true
```

它只验证 Release 变体的编译、测试和元数据，输出未签名 APK，不能安装或作为正式交付。正常 `./gradlew assembleRelease` 不允许回退 Debug 签名；签名工作流同时验证 APK 包名、版本、证书、debuggable 与 ELF ABI，再仅更新 Release 草稿。

兼容工具链保持：JDK 17、Gradle 8.13、AGP 8.9.2、Kotlin 2.1.20、Compose BOM 2025.04.01、compile/target SDK 35、Build Tools 35.0.0、min SDK 26。添加 Navigation Compose 2.9.0、DataStore 1.1.7、jsoup 1.18.3 和 Coil GIF 2.7.0。依赖更新建议不等同于编译错误，未盲目升级工具链。

现有云端 Android 9 软件模拟器可运行：

```sh
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.DeviceTests,com.sakugapp.UiSmokeTest
```

实际网站设备测试单独为 `com.sakugapp.PublicBrowseTest`，会读取公开帖子、播放视频并下载原视频进行帧分析；不登录、不投票、不发表真实评论。CI 增加 KVM 设备任务，运行持久化、加密、帧索引、原生导航与真实浏览测试。结果以具体运行和 [测试报告](docs/test-report.md) 为准。

API、解析和状态单测使用合法公开脱敏样本与明确标注的故障注入数据。androidTest 内的小视频是自己生成的 VFR/B 帧解码测试图案，不会出现在帖子流中；`scripts/generate-frame-fixture.py` 可用 FFmpeg 复现。

输出 APK：`app/build/outputs/apk/debug/app-debug.apk`。没有 abiFilters；实际打包 ABI 和 ELF 验证记录在测试报告。

## 安全与验证边界

网页登录和 Native 登录共用官网会话，认证/写请求保留 CSRF、Cookie 和权限检查，不在 URL 放密码，不自动重试写入，不绕过访问限制。Cookie 在 noBackupFilesDir 加密，密钥不导出；DataStore 只放普通设置和搜索历史。浏览窗口只接受指定 HTTPS 站点，禁 file/content/mixed-content/第三方 Cookie，不注入 JS bridge；官网交互所需 JS 保留，帖子列表不进入 WebView。公开内容仍属于各自权利人。

用户反馈此前 **Vote 已正常**；本轮保留该流程，没有用真实账户重新登录或执行投票/评论写入。评论写入和网站账户资料修改仍需实账号验收；测试服务不能代替该结论。此前软件模拟器的 System UI/ThreadedRenderer 失败记录保留；后续 KVM 测试结果按版本列入测试报告。

剩余：原扩展的小游戏、全部 CSS 主题、GIF 严格逐帧、模糊/色相/sepia、自动推断作者/AD 角色、完整 DText 富文本/用户 tooltip 等未全部移植。系统/编解码器对具体格式的支持仍需更多设备覆盖；Android 8 没有严格索引解码替代。动态偏移分页仍可能因站点插入/删除而漏项，已做 ID 去重但不承诺动态快照无遗漏。

[接口调查](docs/data-source.md) · [功能对照](docs/feature-matrix.md) · [测试报告](docs/test-report.md) · [第三方声明](docs/third-party-notices.md)

项目代码 GPL-3.0-only，见 [LICENSE](LICENSE)。APK 随附 GPL/Apache/MPL/MIT 许可全文和来源声明。Release 的源码 ZIP 对应同一标签和提交，包含源文件、Wrapper、测试、资源和构建/签名脚本，排除私有密钥、本地配置和缓存。源码接收者可以用自己的证书构建；官方私钥不属于对应源码。下载的站点媒体仍归各自权利人。
