# 1.2.1 — 界面精简与布局调整

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

## 实际验收结果（2026-10-27，UTC+8）

验收提交 `45ef7b0` 的 [GitHub 检查](https://github.com/Milenal82/sakugapp/actions/runs/40369422178)成功：96 项 JVM 单测、Lint 无错误、Debug APK/测试 APK，API 28 x86_64 KVM **24/24 项设备测试通过**。不是只生成了测试文件；日志记录实际执行 24 项、成功完成并导出 13 张 PNG。

设备覆盖：详情五按钮排列与边界禁用、原帖 URL 路由、长按提示、正确且不重复的 Score；收藏单入口、多夹、默认夹、取消；搜索自动焦点、默认折叠历史、组合条件、展开不移动列表顶部；收藏首页无视频/搜索框、点击收藏夹进入 Post 卡片；实际系统 JSON 导出/再导入；旧 v1 schema 迁移后保留帖子、关系、封面与媒体时长；中英及既有日/繁界面；320/360 dp 手机及平板布局断言；原有公开分页/播放/逐帧、旋转、手势、加密会话、下载错误和配额回归。

截图在 [device-test-reports](https://github.com/Milenal82/sakugapp/actions/runs/40369422178/artifacts/12673120295) 的 `ui-evidence` 中，包括：

- `compact-detail-phone-dark.png`、`compact-detail-tablet-light.png`：公开响应样本构成的隔离详情测试屏，未把测试样本注入正式列表。
- `compact-search-expanded.png`、`compact-post-grid.png`：真实读取的搜索/列表屏。
- `compact-favorites-home.png`、`compact-favorites-folder.png`、`compact-json-imported.png`：真实公开帖子的本地保存与系统文件导入流程。
- `compact-settings-zh-cn.png`：原生简体中文设置；另有原有真实详情/网格、平板、收藏与播放器图共 13 张。

首轮的 22/24 设备结果并未当作通过：Room schema 的 `indices` 是可选字段，测试改为可空读取；Android 的 `executeShellCommand` 不负责 shell 引号解析，文件查找不再给已限定的测试文件名加入字面引号。应用的 JSON 输出当时已成功，修正后完整导出/导入均通过。签名升级测试适配新的搜索入口；API 28 的 XML 不导出 Material Chip 的选中状态，改为先将系统夜间模式设为深色，再检查覆盖升级后的实际背景仍为旧版保存的浅色，防止系统默认浅色造成误判；Compose 用例继续核对选中状态和重建后的持久化。测试失败也清理主题，避免污染后续 Score 色值检查。没有删除主题、历史或数据保留断言。

最终清理后的本地命令 `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 成功；**96 tests / 0 failures / 0 errors / 0 skipped；Lint 0 errors / 7 warnings**。警告涉及 API 兼容/固定方向建议、计数复数资源、受限官网浏览 JavaScript、KAPT 与两处搜索日期排版；未添加 Lint baseline 或关闭警告。四语 **378** 条资源覆盖及格式参数、16 项发布工具测试、actionlint 均通过。

本云环境访问 GitHub Actions 构件下载重定向仍返回 `Forbidden`，因此未在本机下载、肉眼复查这些 PNG 或签名 APK。GitHub 内部构建、签名校验、模拟器安装及测试可运行；提供构件页面供用户下载。不会把截图导出成功称为人工视觉验收。真机、多种 Android/解码器以及登录后的 Vote/评论写入本轮未重测。

签名构建沿用受保护 Secrets 和原证书（SHA-256 `9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff`），不生成新密钥。已完成的 45ef7b0 Release 构建通过 96 项单测、Lint 0 errors / 24 warnings、包名/版本/非 Debug/证书/许可证校验，包含 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`；后续封装以构件 `release-verification.json` 中的 `source_commit` 为准。

交付下载：本分支 [Actions](https://github.com/Milenal82/sakugapp/actions/workflows/android.yml) 成功的签名运行中，`sakugapp-signed-candidate` 包含 `Sakugapp-1.2.1-candidate.apk`、对应源码 ZIP、`SHA256SUMS.txt` 与公开验证报告。`signed-upgrade-install-report` 保存已发布 v1.0.0 → 候选版的实际同证书 `adb install -r` 检查与主题/历史截图；1.2.0 收藏数据的兼容性另由上面的真实 v1 schema 迁移用例检查，不混同这两类验证。最终运行链接随 PR 交付结果提供。
