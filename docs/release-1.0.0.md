# 1.0.0 正式发布准备

应用名 Sakugapp；保持 `com.sakugapp`；versionName `1.0.0`、versionCode `6`；标签 `v1.0.0`。准备时远端没有 Releases 或版本标签，故采用首版号。基于已由用户测试的 0.5.0 / `01e1ce5b0c271701f89fea7ccfb4d4f53c9e243c`，不重新初始化、不更换包名、不引入 R8 行为变化。

## 验证范围

| 检查 | 实际结果 / 边界 |
| --- | --- |
| 原 main 构建基线 | `testDebugUnitTest lintDebug assembleDebug` 成功；该提交最近一次既有 CI `39822196361` 成功 |
| Release 变体单元测试 | `testReleaseUnitTest`：73 项，0 失败、0 错误、0 跳过 |
| Release Lint | 0 错误，保留 1 项 `SetJavaScriptEnabled` 警告：官方账户/Blog/Forum 受限浏览窗口需要 JavaScript；首页和帖子列表为原生界面。未通过全局关闭 Lint 掩盖警告 |
| 无 Secrets 的普通 Release 构建 | `assembleRelease` 明确失败于 `checkReleaseSigning`，列出缺失变量名；无 Debug 签名回退 |
| 显式未签名预检 | `testReleaseUnitTest lintRelease assembleRelease -PreleaseUnsignedCheck=true` 成功；仅编译验证，不是可安装正式版 |
| 发布工具安全测试 | `python3 -m unittest discover -s scripts/tests -v`：14 项通过；使用合成字节，不生成正式私钥 |
| APK 清单 | 预检实际 APK：`com.sakugapp` / `1.0.0` / `6`，min SDK 26 / target SDK 35，`debuggable=false` |
| APK 架构 | 实际 ELF 为 armeabi-v7a / arm64-v8a / x86 / x86_64，两个 native library 均有匹配的 64 位版本；没有 32 位限定 |
| APK 许可 | 核对 APK 中 GPL、Apache、MPL、MIT 许可全文及来源声明存在 |
| 源码交付 | 从精确标签提交 `git archive`，包含完整源码/资源/Wrapper/测试/构建脚本/许可；验证脚本拒绝密钥文件、密码配置、缓存及 PEM 私钥内容 |
| 正式签名 / 证书指纹 | **尚未取得正式密钥，未验证**；不把 Debug 指纹或空值当作正式指纹 |
| 正式 APK 安装、运行与升级 | **尚未验证**；用户对旧测试版的验收不等同于正式签名 APK 验收 |

工具链保持 JDK 17.0.18、Gradle 8.13、AGP 8.9.2、Kotlin 2.1.20、compile SDK / Build Tools 35。生成的 `release-verification.json` 记录实际构件哈希、源码提交、测试数量、Lint 数量、签名状态、证书及 ABI；只对该次构件有效，不声称跨环境 APK 哈希相同。

## 签名阻塞与剩余操作

工作区只发现 Debug keystore，无可复用的正式密钥或签名环境变量。远端 Secrets 名称列表读取和 `release-signing` 环境创建均返回 **HTTP 403 / Resource not accessible by integration**，不能据此断言远端所有 Secrets 都不存在，也未尝试覆盖已有密钥。

所有者按 [正式签名手册](release-signing.md) 先复用既有正式密钥；确实没有时只生成一次、做好加密备份。在 GitHub 保护 `release-signing` 环境，只允许 `v*` 标签，配置五项 Secrets，然后从 **v1.0.0 标签**重跑 [Signed Release draft](https://github.com/Milenal82/sakugapp/actions/workflows/release.yml)。预检 job 独立完成不依赖 Secrets 的检查；缺签名的 job 明确失败。Secrets/环境保护配置完成前，不应将草稿公开。

签名成功后，工作流核对固定证书指纹、有效签名、非 Debug 证书、不可调试、包名、版本和 ELF；将正式 APK、源码 ZIP、SHA-256、发布说明及验证 JSON 上传到 **Draft**，不会公开发布。若签名仍不可用，准备草稿只包含源码 ZIP、SHA-256、说明和标明 unsigned 的预检报告，**不附未签名 APK**。

## 许可与已知问题

GPL-3.0-only 保持不变；对应源码与附件基于同一标签提交，包含构建与安装方法，源码接收者可使用自己的签名证书。官方私钥、密码及其 Base64 不属于公开源码。APK 随附许可证和 [第三方声明](third-party-notices.md)，没有复制扩展的 JS/CSS/图标或捆绑网站视频。完整功能与限制见 [发布说明](../release-notes/v1.0.0.md)、[功能对照](feature-matrix.md) 和 [README](../README.md)。

尚需正式签名后的安装/升级及更多设备/编解码器测试；本轮不使用真实账号执行 Vote、评论或账户资料写入。帧分析设备/大小限制、详情页前台下载、动态分页及未全部移植的扩展功能保持明确说明。
