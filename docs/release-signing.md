# 正式签名与草稿发布

## 已确定的身份

本次目标 Sakugapp 1.3.0 / versionCode 16，applicationId `com.sakugapp`，标签 `v1.3.0`；上一正式版为 v1.2.2 / code 15。v1.1.0 的 versionCode 为 10，v1.0.0 为 6。不要为了绕过签名不匹配而更换现有包名。后续沿用同一正式证书并递增 versionCode。

正式签名已通过受保护的 `release-signing` 环境配置并成功使用。现有证书 SHA-256 为 `9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff`。本地工作区不持有正式私钥，不需要重新生成或提交密钥。下面的生成步骤只供全新项目配置参考，现有发布必须复用原密钥。

## 所有者安全生成与备份（仅在确实没有正式密钥时）

无需 Android Studio。可在你控制的可信终端或私有 GitHub Codespaces 中使用 JDK 17 的 keytool、Python 3 和 GitHub CLI。不要在公开 CI、聊天或公共终端生成/传递私钥。以下脚本不会由 Actions 调用。

```sh
# 在仓库中运行脚本，但目标必须是仓库外一个尚不存在的私有目录。
./scripts/create-release-key.sh /你控制的私有目录/sakugapp-signing
```

keytool 交互提示密码，输入不会回显；使用密码管理器生成并保管高强度唯一密码。生成一次 RSA 4096 / PKCS12 密钥，alias 为 `sakugapp-release`。PKCS12 默认 key password 与 store password 相同。不要给命令添加明文 `-storepass` / `-keypass` 参数，不开 `set -x`，不截图密码。

在关闭云环境前，保存 **至少两个加密备份**，例如加密离线介质和你控制的加密保管库；密码与 keystore 分开保存。保留 alias 及公开证书 SHA-256。用备份导出公开证书确认能读、指纹一致，再关闭或销毁生成环境。不要将密钥、密码、Base64、临时目录加入 Git、日志、Actions artifacts 或 Release 附件。丢失正式密钥会影响同包名后续升级。

## GitHub 受保护配置

打开仓库 Settings → Environments → 新建 **release-signing**。先设置部署规则，只允许 `main` 分支与发布标签 `v*`：现有工作流的手动入口从 main 运行，但签名源码始终检出并核对精确版本标签；不接受 PR 或其他分支。按账户方案可用性配置 required reviewers 和 main / 标签 ruleset，限制工作流修改及标签修改/删除。不要把 Secrets 放进公开 Variables。为此环境增加：

| Secret 名称 | 内容 |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | 既有正式 keystore 的单行 Base64 |
| `ANDROID_RELEASE_STORE_PASSWORD` | keystore 密码 |
| `ANDROID_RELEASE_KEY_ALIAS` | 正式密钥 alias；新生成默认 sakugapp-release |
| `ANDROID_RELEASE_KEY_PASSWORD` | 密钥密码；上述 PKCS12 通常等于 store password |
| `ANDROID_RELEASE_CERT_SHA256` | 该正式证书 SHA-256，64 位十六进制（可带冒号），用于固定签名身份；这是公开指纹，不是私钥 |

优先使用所有者自己的 GitHub CLI 登录后运行以下脚本，避免复制 Base64：

```sh
gh auth login --hostname github.com
./scripts/configure-release-secrets.sh /私有目录/sakugapp-release.p12 sakugapp-release Milenal82/sakugapp
```

脚本以隐藏提示读取密码，只将 Base64/密码通过标准输入送到 Secrets API；不在参数、输出或临时配置文件中暴露。它不会生成新密钥。当前 cloud integration 的 Secrets 权限不足时，必须用你自己的 GitHub 登录，或在 GitHub 的上述 Secrets 安全输入框中设置；**不要把内容贴到聊天中**。若通过网页配置，单行 Base64 只能保存在权限为 600 的私有临时文件，录入后安全删除，不能作为 Release 附件上传。

## 完成已经准备的草稿

配置完成后，优先使用已注册的 Actions → Android checks and artifacts，选择 main，`release_tag` 填 v1.1.0，勾选 `sign_release`：

```sh
gh workflow run android.yml --repo Milenal82/sakugapp --ref main -f release_tag=v1.1.0 -f sign_release=true
```

这个入口调用同一份 `release.yml`；main 只提供发布编排，APK 与源码 ZIP 始终来自经校验的 `v1.1.0` 提交。新工作流已被 GitHub 索引后，也可直接在 Actions → Signed Release draft 从对应标签运行：

```sh
gh workflow run release.yml --repo Milenal82/sakugapp --ref v1.1.0 -f tag=v1.1.0
```

若新发布工作流尚未被 GitHub 索引、触发返回 404，可以先使用已有入口完成不依赖 Secrets 的 Release 检查与草稿附件上传：

```sh
gh workflow run android.yml --repo Milenal82/sakugapp --ref main -f release_tag=v1.1.0
```

未勾选 `sign_release` 时，该备用 job 只检出 `v1.1.0` 的精确提交，验证其属于 main，运行 Release 单测/Lint/未签名预检，并将源码、SHA-256、说明及 unsigned 报告上传到同一提交的**既有草稿**。它不读取签名 Secrets、不上传未签名 APK；发现已有 APK 时拒绝覆盖。勾选时使用上面的受保护签名流程，避开新工作流尚未索引的问题。只有签名校验成功后，才会有正式 APK。

预检会检查标签与版本一致、标签提交属于 main、Release 单测/Lint、未签名构建的清单和 ELF。签名 job 仅从该提交检出，在环境授权后读取既有 Secrets；缺一项就明确失败，不回退 Debug、不生成新密钥。关闭签名 job 的 Gradle 构建/配置缓存；keystore 仅在 runner 临时目录以 600 权限存在，结束时清理。

成功后会验证包名/版本、不可调试、APK 签名、证书指纹固定、非 Debug 证书及实际 ABI，再上传：正式 APK、同一提交完整源码 ZIP、SHA256SUMS.txt、发布说明和验证 JSON。签名阶段只创建/更新 **Draft**，拒绝覆盖公开 Release；仅获所有者明确授权的版本在专属验证门槛全部通过后公开。

下载正式 APK 后验证校验文件与公开证书指纹，并用真实设备测试首次安装、Debug 切换正式版及正式版覆盖升级。完成校验和升级检查后，才可按所有者的发布授权将草稿公开。v1.1.0 已获本次任务的公开发布授权；工作流本身仍保留草稿阶段，防止失败构建被自动公开。

## 本地/其他云端构建

正常 `./gradlew assembleRelease` 需要四项环境变量：`ANDROID_RELEASE_KEYSTORE`（私有文件路径）、`ANDROID_RELEASE_STORE_PASSWORD`、`ANDROID_RELEASE_KEY_ALIAS`、`ANDROID_RELEASE_KEY_PASSWORD`。不要用 `-P...password=...` 参数或项目内 properties 文件传密码。读取密码时关闭终端回显，在任务结束后清理环境变量。

未配置 Secrets 时只能显式执行：

```sh
./gradlew testReleaseUnitTest lintRelease assembleRelease -PreleaseUnsignedCheck=true
python3 scripts/verify-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --unsigned-preflight --report /私有输出外目录/release-preflight.json
```

这是**不能安装、不能充当正式签名 APK 的预检**。它不会被上传为 Release APK；预检证书指纹是空值，不伪造证书验证结果。GPL 源码接收者可用自己的密钥运行同一构建流程，无需获得官方私钥。

## v1.2.0 的明确公开发布授权

本次所有者明确要求直接发布 v1.2.0。工作流保留草稿签名阶段；仅 v1.2.0 在正式构建、签名/元数据验证、旧版实际覆盖安装和同一提交的字号/标签间距设备检查通过后，执行公开发布。其他版本仍保持原有草稿流程。公开前另与 v1.1.0 APK 核对证书和递增 versionCode；不会覆盖旧 Release。

## v1.3.0 公开发布门槛

本轮明确授权发布 v1.3.0，继续使用原证书，不生成新密钥。`release.yml` 为 v1.3.0 增加独立发布任务，先执行正式 Release 单测、Lint、签名与来源验证，再用官方 v1.2.2 APK 实际覆盖安装。升级前后通过正常导入／导出核对收藏夹与二级关系、封面、标签备注，检查浏览历史、设置与新本地昵称重启。未知版本会明确失败，不再默认回退 v1.0.0。

公开前 `publish-v1.3.0.py` 同时要求精确源码的 main 标准 CI 和手机／平板专项验证成功，完整的四主题 × 深浅 × 三字号矩阵，正确签名与递增版本，以及升级报告。签名缺失、不匹配、测试或升级失败时保持草稿，不公开、不回退 Debug。正式附件由既有打包脚本生成：APK、对应源码 ZIP、SHA256SUMS、说明及验证 JSON。

若需要从已存在的精确标签手动恢复发布，使用：

```sh
gh workflow run release.yml --repo Milenal82/sakugapp --ref v1.3.0 -f tag=v1.3.0
```

不要移动标签或覆盖已公开的 Release。私钥继续仅在受保护 runner 的临时目录存在，清理和日志保护沿用现有流程。
## v1.3.0 验证工具修正

v1.3.0 的标签和 APK 保持在 `37f5b5515d776ecc53ad8590f14395bb7704bddb`。首轮覆盖安装成功，随后独立检查器误用了弹出菜单中未导出的测试资源 ID。检查器改用实际可见的菜单文字；返回手势测试等待输入法的原生回调交接完成，保留全部列表和状态断言。

从 main 手动运行 Release 工作流时，可选 `reuse_v130_signed`。此选项只读取原受保护构建 42329384573 的产物，重新验证证书、包名、版本、ELF、源码与全部校验和，并要求 APK SHA-256 恰为 `50da9e14b4c9fbcc3811f19419aedf5a2468d4d49a241cb399ba378a36ce0da1`；不读取密钥、不重新签名、不改变 APK 字节。仍执行完整的 v1.2.2 原生升级验证和 main CI。

验证提交必须继承原标签，且差异只允许列出的测试、发布验证脚本和文档文件。任何应用代码、资源、构建依赖、签名工具或现有 Android／维护工作流改动都会拒绝发布。升级报告同时记录 APK 源码提交与验证工具提交；二者不能混用。已公开版本仍禁止修改，旧版 Release 不受影响。
