# Sakugapp 1.2.0 增量开发

基线为上一轮已验证的开发提交 `b9b8f8f43f527d575922444f348ad8eb8e85b83b`；远端 main 仍为 `cbe0453`，正式 Release 仍为 v1.0.0。分支 `feature/personalization-pro-player`，包名 `com.sakugapp`、正式签名、旧 DataStore 和会话格式保持不变；versionName 1.2.0 / versionCode 8。

附件实际列出功能 1、3–11，共 10 项。用户确认功能 2 未提供，不将壁纸假定为需求。

## 最终实际验收

[应用验收提交 df836f4](https://github.com/Milenal82/sakugapp/commit/df836f4bd51d0e845a26e82e9e70d2d60185227e) 的 [完整检查](https://github.com/Milenal82/sakugapp/actions/runs/40303737605) **success**：96 项 JVM 单测、Debug APK、测试 APK、Lint 通过；API 28 x86_64 KVM **20/20 项设备测试全部通过**。日志明确 Starting / Finished 20 tests、BUILD SUCCESSFUL；5 张 PNG 已实际导出到 device-test-reports/ui-evidence。

[Release 及实际覆盖升级](https://github.com/Milenal82/sakugapp/actions/runs/40303730796) **success**：96 项 Release 单测、Lint **0 errors / 26 warnings**、assembleRelease、固定证书/包名/版本/非 debuggable/实际 ELF ABI 检查通过。已发布 1.0.0/code 6 直接 install -r 到 1.2.0/code 8，保留浅色、关闭换行与搜索历史；未卸载、未清数据。证书 SHA-256 为 `9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff`；ABI 为 arm64-v8a、armeabi-v7a、x86、x86_64。所有 32 位原生库都有 64 位对应版本。本地 Lint 0 errors / 9 warnings，CI 另含依赖升级等建议，未全局隐藏警告。

[机器可读验收摘要](evidence/personalization-final-validation.json)。五阶段和测试修正之后，最终交付可能另有纯文档提交；APK/source ZIP 的准确提交由同一签名构件中的 release-verification.json 标明，构建会校验 APK 内嵌提交与源码一致。

原生截图：phone-post-detail.png、phone-post-grid.png、tablet-12-columns.png、phone-local-favorites.png、professional-controls.png。收藏、译名、JSON 合并/失败回滚、下载字节与配额、A–B/旋转/真实帧、双击不 seek、原生进度条只 seek、双指缩放、单指平移与切帖均已实际运行。首轮 18/20 的两个失败源于测试触点落在受保护边缘/进度条；改为分别测试保护区和画面内部，保留原有系统返回与 scrubber 保护，未删减功能断言。

本环境下载 Actions 构件返回 Forbidden，未打印带签名的重定向 URL；GitHub 内的校验和实际安装成功。仓库用户可登录 GitHub 下载签名候选、对应源码 ZIP、校验、报告与截图。真机、其他系统/格式、传感器旋转及实账号仍按下文列为未验证。

## 分阶段记录

- 基线：`testDebugUnitTest lintDebug assembleDebug` 成功，82 项单测通过。
- 阶段 1：主题 Token、系统/深/浅模式及旧设置迁移、12dp 卡片、真实 Score 徽章、未知时长明确占位、播放器实际时长回传、集中动画时长和历史动画；`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 成功，85 项单测通过。设备运行待最终 CI。
- 阶段 2：手机保持 1–4 列，最小宽度 600dp 的平板提供 8/12 列；保留跨窗口宽度变化的原始列数设置，普通手机竖屏、平板尊重系统旋转。新增真实分页设备用例中的平板密度/Score/截图验收；运行结果待 CI。
- 阶段 3：新增独立 Room 收藏库（多对多、封面、排序、批量移动/删除、按 ID/时间排序、本地搜索、JSON 安全合并）；导出/导入原始记录并保留已有数据，异常回滚。提供用户主动确认的顺序批量私有下载（20 个/批，200 MiB/文件，总额 1 GiB）；删除收藏关系不删除文件，显式清理与系统下载分离。标签翻译支持本地词典、用户覆盖、长按复制/搜索/编辑/删除；8/12 列隐藏翻译行。新增真实响应解析和 Room 设备用例，运行结果待 CI。
- 阶段 3 本地验证：91 项 JVM 单测、Lint、Debug APK 与设备测试 APK 编译全部通过；Room 设备行为将在最终 KVM 实测。
- 阶段 4：播放器高级控制分组折叠、A–B 时间戳循环、已有 0.25× 等倍速保留、真实帧索引及可核实时间映射、1–5× 双指缩放/平移/重置、双击播放暂停与按当前结果顺序切帖；必要邻帖元数据复用，预取最多两张缩略图，不预取邻帖视频。新增本地视频设备测试检查 A–B 实际跳回、旋转状态、严格帧、双击不 seek、缩放/平移不切帖与单指切帖。96 项 JVM 单测、Lint、Debug / 设备 APK 编译成功；设备实际结果待 CI。
- 阶段 5：扩充 20 项设备用例与原生截图导出，保留旧功能回归；新增受控 TLS 实际文件传输/损坏流/配额验证、新旧主题默认及列数持久化验证。发布脚本和签名流程沿用，候选仅上传 Actions，不创建标签或修改 Release。最终实际结果以本分支成功 CI 和草稿 PR 报告为准，不能把设备 APK 编译当作运行通过。

## 附件功能对照

| 编号 | 实现范围 | 实现位置 / 验证入口 |
| --- | --- | --- |
| 1 | 手机 1–4、平板 8/12；保留设置，响应式降密度 | AppTheme.GridPolicy / MainActivity / PublicPaginationTest |
| 2 | 未提供；用户确认不开发 | 不推断为壁纸、不提供无效入口 |
| 3 | 本地多对多收藏、封面、排序、批量移动/删除、搜索、JSON 备份、离线元数据与批量下载 | LibraryDatabase / LocalLibraryStore / LibraryCodec / LibraryScreen / OfflineLibraryMedia；Room / UI / TLS 设备用例 |
| 4 | 图下独立译文、本地维护词典和用户覆盖；长按操作；高密度隐藏译文 | TagDictionary / TranslatedTag / tag-translations.json；LibraryCodecTest / LocalLibraryUiDeviceTest |
| 5 | A–B、实际解码帧、标称 FPS、九档速度、缩放平移 | VideoPanel / PlayerTools / StrictFrames / VideoGestureHost；PlayerToolsTest / ProfessionalPlayerDeviceTest |
| 6 | 12dp 圆角卡片、轻表面/阴影、按压反馈；高密度减少间距 | MainActivity.PostCard / AppTheme |
| 7 | 默认原生播放/进度/时间、全屏和更多；高级操作分三组折叠 | VideoPanel；PublicBrowseTest / PlayerRotationDeviceTest |
| 8 | 系统/深/浅，原设置迁移；统一 Token 与 AA 对比度检查 | AppTheme / PreferencesStore / PersonalizationTest / BrowsingUpgradeDeviceTest |
| 9 | 搜索 340ms、控制 320ms、历史 300ms、收藏弹窗 280ms、卡片反馈 150ms | Motion / PacedAlertDialog；Compose 原生时长比例支持快速反向和系统关闭动画 |
| 10 | 左下真实时长/未知状态、右下真实红色 Score；图下分类标签及可选译文 | MainActivity.PostCard / PostCaption；真实分页与实际视频测试 |
| 11 | 按当前搜索/收藏顺序左右切帖，双击播放/暂停；缩放优先与边缘/进度条保护 | FeedViewModel / MainActivity / VideoGestureHost；顺序单测及手势设备用例 |

对照表“实现”表示有实际代码；运行验收必须查看相同提交的 Actions，设备用例未运行或失败时不记作已通过。

## 运行证据与明确限制

- 开发基线 82 项、阶段 1 为 85 项、阶段 2 为 86 项、阶段 3 为 91 项；阶段 4–5 当前 96 项 JVM 单测。各阶段执行了单测、Lint、Debug / 测试 APK 编译并分别提交。首轮阶段 3 CI 的手机/平板功能断言通过，最后截图保存失败；已修正为目标应用可写目录，等待最终运行证据。
- 最终已执行设备范围：API 28 x86_64 KVM；手机 360dp、平板 1920×1200 / 160dpi 的 8/12 列、真实 5 批帖子、详情/切帖、现有登录会话加密/设置/语言/严格帧/真实视频下载回归；Room 多对多/封面/合并/失败回滚；真实 UI 收藏/译名操作；本地 VFR 视频 A–B/旋转/双击/缩放/平移；受控 TLS 下载到实际文件并验证字节、损坏清理和限额拒绝。20/20 已通过，详见上方成功运行。
- UI PNG 位于 device-test-reports 构件的 ui-evidence（手机网格、12 列平板、本地收藏和专业控制）。专业工具截图使用明确的测试视频；正常帖子界面使用实际官网内容，不把测试图案当网站数据。
- 批量下载用受控 TLS 视频验证存储行为；没有自动抓取整夹真实大视频来增加网站负担。系统下载按钮仍有真实官网视频测试。没有真实账号来验证投票/评论写入或跨版本账号升级，沿用已有实现。
- 真机、其他 Android/编解码器、系统传感器自动旋转和 Android 13+ 预测返回仍需验证；本次截图/交互来自模拟器，不等同实机验收。未做完整 GPU/内存峰值剖析，不声称所有设备无卡顿或所有格式逐帧支持。
- 可选的平板列表＋详情双栏、用户备注和在线机器翻译未实现；功能 2 未提供，壁纸不在本轮范围。没有这些功能的无效按钮。


## 播放精度与手势边界

A–B 基于真实播放时间戳，每 30ms 检查并调用 Media3 seek，另有解码/缓冲延迟，不称为逐帧精确循环。A 必须早于 B；每个新视频清空区间，同视频旋转保留状态。

严格帧模式使用 MediaMetadataRetriever.getFrameAtIndex（API 28+），保留真实解码帧号。只有 MediaExtractor 的唯一、零起点 PTS 数量与实际解码帧数吻合时，才允许按播放时间选取索引；不使用 FPS×时间或固定 33ms。其他情况明确提示从第 1 帧开始，不能宣称已定位当前播放帧。主播放器只报告实际时间和流所报告的标称 FPS，不伪造精确帧号。分析缓存继续使用原路径，新增 400 MiB 上限；私有完整离线文件可直接解码。

视频区域的双指操作优先缩放，1–5×；放大后单指平移。双击始终播放/暂停，重置使用独立按钮。原生进度条区域及屏幕左右 24dp 边缘保留原行为；切帖阈值 64dp 且横向位移显著大于纵向。切帖按当前列表/收藏排序，并释放旧播放器；切换不同帖子会退出旧全屏页。手势可在设置关闭。

## 收藏与翻译的数据边界

- Room 数据库 `local-library-v1.db` 是新增独立存储；旧 `settings_v1`、账户密文与系统下载不迁移、不清空。新数据库无 destructive fallback；Schema 在 app/schemas。
- 收藏上限 10000 帖子、100 收藏夹、50000 关系；快照总额 8 MiB、自定义词条 2000、媒体时长缓存 1000 URL。JSON 上限 20 MiB。超限明确失败，不默默丢弃旧记录。同 ID 保留既有快照，同名文件夹合并；用户导入的本地快照不宣称为官网最新数据。
- 离线标识仅根据实际存在且下载完成的私有媒体文件显示。旧 SAF 导出到系统目录的文件不被擅自扫描或声称已缓存；原来的下载按钮保留。
- 词典包含用户任务确认的两个示例及四个基础动画术语；不推导未核实的人名别名或作品译名。外部 Wikipedia 核实请求被云代理 CONNECT 403 拒绝，未宣称外部核验成功。未启用在线机器翻译，也没有 API 密钥；未知词条保留原文，可由用户本地编辑。


初次带 ID 查询的公开请求收到 HTTP 403；随后正常请求 `/post.json?limit=1` 返回 200。未改变认证、伪装身份或绕过访问限制，详情见 docs/evidence/personalization-data-check.json。该实际响应的 frames/frames_string 等字段为空，未取得 duration 字段。没有为缩略图批量下载完整视频来猜测时长。实际视频时长来自 Media3，未获取时显示“—”，不是 0 或示例时长。
