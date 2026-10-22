# 0.5.0（2026-10-23）

基于 0.4.0 / b022d51 保留修改，完成用户本轮要求：

| 要求 | 实现位置与行为 |
| --- | --- |
| “作”字图标 | `res/drawable/ic_launcher_foreground.xml` 独立绘制笔画，`res/mipmap-anydpi/ic_launcher.xml` 自适应前景/背景与 Android 13 单色图标；不依赖字体或外部图标许可 |
| 浅色背景 | 设置 → Light background / 淺色背景，DataStore 持久化；`ui/AppTheme.kt` 同步原生页面与官网窗口工具栏、系统栏图标；媒体区域保持深色；官网内容沿用网站原样 |
| 详情下载 | `ui/VideoDownloadButton.kt` 使用系统 CreateDocument 选择位置，`data/VideoDownloader.kt` 流式写入返回的原视频；进度、成功、失败及取消反馈；无需广泛存储权限 |
| 详情 Tag 边框 | `ui/DetailTags.kt` 根据真实类别显示轮廓；版权黄色、动画师紫色，其余依主题；标签名称仍执行搜索，长名称可换行 |
| Score 快捷项 | `ui/SearchDialog.kt` 两个快捷项 `score:>=3`、`score:>=100`；仅替换现有 score/-score 条件，保留 OR、排除、rating、排序及其他条件，不产生重复 score 字段 |
| 动画师 Wiki | 紫色动画师标签前独立 `?`；`UrlPolicy.artistWiki` 用 query 参数编码真实名称，打开官方受限浏览窗口，例如 `https://www.sakugabooru.com/wiki/show?title=mokmok`；返回详情保留列表状态 |

浅色模式将黄色/紫色文字调为同色系深色版本，对浅底对比度达到 4.5:1；深色模式保留原颜色。语言继续支持 English / 日本語 / 繁體中文。

下载采用官网 API 返回的实际 file_url，不推测替代格式；拒绝非 HTTPS、错误 HTTP、HTML 冒充视频、空/截断响应，不自动重试或绕过限制。只有写入并关闭目标流后才提示成功。用户取消、失败或离开详情时取消请求并尝试删除未完成文件；需要保持此详情页开启，不作为可靠后台下载服务。下载的媒体权利属于各自权利人。

## 验证

首轮 CI 39814590964 构建成功，9 项设备测试仅主题测试失败：测试的 sibling 定位误匹配 13 个开关，未进入实际切换。已为每个设置开关补充独立无障碍说明，并按精确说明定位；保留截图亮度与重建状态断言。其余 8 项（含真实下载及 Wiki 返回）已通过。初始失败证据在 `evidence/initial-theme-selector.txt`，最终提交会重新执行完整 CI。

- 在原基线上执行 Debug 构建；修改后执行 `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`。资源移动触发的本地增量缓存问题通过 clean 排除，未删除源码或隐藏检查。
- JVM 共 73 项测试：新增 TLS 流式下载完整性、错误状态、HTML 响应、截断、目标写入失败、取消清理；Wiki 参数编码；Score 条件替换；深浅主题及文字对比度。测试 TLS 证书在运行时生成，仅用于本地 MockWebServer，不进入 APK，不关闭生产 TLS 校验。
- KVM 设备任务增加为 9 项，测试浅色实际截图亮度、Activity 重建后的设置、两个 Score 快捷项；真实浏览流程扩展到动画师 Wiki 返回和系统另存为下载。实际运行结论以对应提交的 CI 日志及交付报告为准，不将“测试已编译”写成“设备已验证”。
- 官网只读检查：mokmok Wiki HTTP 200、标题 `mokmok | sakugabooru`；真实 MP4 Range 请求 HTTP 206、Content-Type video/mp4、返回 MP4 ftyp 头，证据在 `evidence/wiki-and-download-2026-10-23.json`。
- 工具链保持 JDK 17 / Gradle 8.13 / AGP 8.9.2 / Android SDK 35；版本 0.5.0 / versionCode 5；不限制为 32 位，实际 ABI 检查记录于 `evidence/abi-check.json`。

登录/Vote/评论协议保留；未使用真实账户写入新票值或评论。严格帧索引的设备/格式限制及之前未完成的扩展功能仍有效，见功能对照表。Debug 签名跨构建可能不同，覆盖安装失败时请先核对签名，卸载会清除本地数据。
