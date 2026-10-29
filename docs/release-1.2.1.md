# 1.2.1 — 界面精简与布局调整

本文件保留较早的 **1.2.1 / code 9** 开发候选记录，以下提交、测试和构件均属于该历史版本，不是当前 **1.2.1 / code 12** 的验证结果。当前改动见 [CHANGELOG](../CHANGELOG.md)、[开发版本说明](../release-notes/v1.2.1.md)及[测试记录](test-report.md)。此处历史版本未作为正式 Release 发布。

开发基线：`07ca42cab25d256208928b5ca3cb389b1ba66cac`（1.2.0 开发候选）。开发分支 `feature/compact-interface`，包名仍为 `com.sakugapp`；版本 1.2.1 / code 9。正式 v1.0.0 和 main 均不修改。

## 主要界面前后对照

| 页面 | 1.2.0 | 1.2.1 |
| --- | --- | --- |
| 详情操作 | 上下帖、双收藏入口、下载、原帖分散成多行 | 同一行 5 个图标，24 dp 图形、48 dp 触摸区域，长按名称；边界上下帖禁用 |
| 收藏 | 快速收藏与选择收藏夹分开，重复已收藏文字 | 一个有状态的图标；弹窗选择多个夹、快速存默认、创建夹、取消收藏 |
| 详情信息 | Score 单独一行；来源在元数据下方另起块 | Post ID、来源、Score 和既有字段在同一横向换行栏；来源可点击 |
| Tags | 原标签下方中文译名，长按编辑译名 | 仅原始标签，原有类别颜色和搜索、Artist Wiki 保留 |
| Post 搜索 | 输入、组合搜索、历史和平均色彩常驻 | 默认仅紧凑搜索入口及列数选择；展开后自动聚焦，高级工具在同一区域，历史另行展开 |
| 搜索展开 | 搜索栏高度参与列表上边距 | 固定 64 dp 入口，展开区域覆盖列表上方；列表锚点不因面板展开移动，分隔线保留 |
| 收藏首页 | 收藏夹、全部视频、搜索、排序、存储等混排 | 仅全部/默认/自定义收藏夹卡片；新建、导入、导出为一致描边按钮 |
| 收藏夹内 | 与首页混用，视频是不同的小横条样式 | 独立页头、数量、返回、更多菜单；复用 Post 网格和卡片；管理时才显示选择工具 |
| 设置/文字 | 明暗模式和浅色开关重复；多处长说明 | 只保留系统/深/浅主题选择；精简中英及繁中、日文，保留错误、风险确认和必要权限信息 |

以上“修改前”依据基线代码，可直接在 GitHub Compare 审查。设备截图由测试保存到 `device-test-reports/ui-evidence/`；不使用手绘图代替应用截图。截图中真实网页读取和捕获的公开响应测试屏会在测试报告中区分。

## 删除清单与数据兼容

- 删除 TagDictionary、TranslatedTag/译名编辑器、维护词典 JSON、词典加载、译名设置、译名 UI 字符串、自定义译名 CRUD 及导出逻辑；没有专属翻译依赖或在线翻译服务需要撤销。
- Room 从 v1 升至 v2，仍使用 `local-library-v1.db`，迁移仅执行 `DROP TABLE IF EXISTS tag_translations`。其余四张表、ID、收藏关系、封面、顺序、保存时间、媒体时长不变；不使用 destructive migration。
- 删除旧 DataStore `tagTranslations` 键；主题、语言、搜索历史和其他键不变。卸载会丢数据，更新不要卸载。
- 仍接受 `sakugapp.library` version 1 的旧 JSON，忽略废弃 translations 字段；新备份不再导出它。收藏夹与帖子仍去重合并，现有信息优先。旧版应用可能不能导入新备份，应保留升级前备份。
- 删除收藏页 ID/标签/收藏夹搜索及界面；不改 Post 的原始标签搜索、组合搜索、历史。
- 删除详情独立 Score 区块、独立选择收藏夹入口、重复收藏状态，移除重复浅色开关。已有收藏夹管理和评论行为保留。

## 验证方法

- JVM：真实响应解析、分类、分页去重、错误、搜索语法、历史、JSON 安全边界及旧 JSON 导入忽略译名等回归。
- Room 迁移：设备上按已提交 v1 schema 创建完整旧库，保存真实帖子、双收藏夹关系、封面、时长、译名，打开 v2 后核对保留的字段和可继续读写、译名表已消失。
- UI：五按钮同排且可点击、边界禁用、长按提示、来源/原帖、统一收藏入口、多收藏夹与取消收藏；按需搜索、焦点、历史、组合条件、列表位置；收藏首页无视频和输入框，夹内浏览；实际系统文档选择器导出、删除测试夹、导入及合并确认。
- 保留真实公开读取/播放/严格帧、旋转、手势、受控下载失败/配额测试。不会自动登录、投票或发评论。
- 窄屏 320 dp、日常 360 dp 与平板布局；中英原生 UI 和四语资源覆盖。

实际构建与设备结果见下方记录；不能从编译通过推断运行通过。真机/其他解码器、登录后写入和外部浏览器选择器不作未经测试的保证。A–B 仍为时间戳循环，严格帧和离线下载沿用既有边界。

## 实际验收记录（2026-10-27，UTC+8）

应用侧最新已完成验收提交 `1b60279` 的 [完整检查](https://github.com/Milenal82/sakugapp/actions/runs/40389463257)成功：96 项 JVM 单测、Lint 无错误、Debug APK/测试 APK，API 28 x86_64 KVM **24/24 项设备测试通过**。包含浅色/关闭换行、设置返回后的原生 UiAutomator 点击、自动聚焦，以及旧 v1 数据库迁移和系统 JSON 导入/导出。之前各轮失败未当作通过；最终下载以构件报告中的 source_commit 为准。

本地完整命令 `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 成功，96 tests / 0 failures / 0 errors / 0 skipped。Debug Lint 0 errors / 7 warnings，Release 0 errors / 24 warnings；未加 baseline 或关闭警告。378 条四语资源及格式参数、16 项发布工具测试、actionlint 均通过。警告包括 API 兼容、方向建议、复数资源、受限官网浏览 JavaScript、KAPT、日期排版及 CI 依赖版本建议。

签名构建使用既有受保护 Secrets 与原证书，SHA-256 `9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff`。已验证 `com.sakugapp`、1.2.1 / code 9、非 Debug 和四种 ABI：arm64-v8a、armeabi-v7a、x86、x86_64。源码 ZIP 经过完整构建输入/敏感文件检查。

正式证书升级作业先安装已发布 v1.0.0，再同证书 `adb install -r`，不卸载或清数据；已实际确认安装、系统强制深色时仍渲染保存的浅色（12/12 背景采样匹配）及换行设置保留。旧 1.2.0 收藏数据另由设备上的原始 v1 schema 迁移检查覆盖，不混同这两类验证。

签名版本的搜索/历史检查使用独立的 `upgrade-ui-check` 测试模块，由持续连接的 UiAutomator 进程操作真实签名应用。模块只在 `-PupgradeUiCheck=true` 时启用，用独立 Debug 证书自测，不注入主应用、不读取私有数据、不使用正式密钥签测试包；测试 APK 不上传。输入框、焦点和旧历史内容断言均保留，结果写入 signed-upgrade-install-report/native-ui-result.txt。完整运行结果与最终下载链接随草稿 PR 提供。

下载本分支 [Actions](https://github.com/Milenal82/sakugapp/actions/workflows/android.yml) 成功的签名运行中的 **sakugapp-signed-candidate**，包含候选 APK、同提交源码 ZIP、SHA256SUMS.txt、公开验证报告和版本说明。**device-test-reports** 包含实际运行报告与截图；**signed-upgrade-install-report** 包含覆盖安装记录。13 张截图含手机深色/平板浅色详情、搜索、列表、收藏首页/夹内、JSON 导入及简中设置；部分详情/收藏屏使用合法捕获的公开响应隔离测试，正式列表仍只读真实网络数据。

本云下载 Actions 构件重定向返回 Forbidden，未在本机人工复查 PNG 或签名 APK；GitHub 内部构建、校验、安装和测试正常提供证据。本地无 KVM 的软件模拟器被 System UI ANR 遮挡，未把该尝试记为通过。真机、其他 Android/解码器以及登录后 Vote/评论写入本轮未重测。A–B 精度和严格帧/离线下载限制沿用旧版。
