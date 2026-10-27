# v1.1.0 发布检查

本轮整理三轮既有开发成果，不添加应用功能；修复高密度网格下 Score 换行，保留单行数值显示，并增加文本布局断言。以开发分支 `feature/compact-interface` 的 `e338200a8db9588ee9ae67af609b1913344eacb3` 为基线；前三轮分别为 `feature/browsing-search-zh-cn`（b9b8f8f）、`feature/personalization-pro-player`（07ca42c）和 `feature/compact-interface`。

## 版本与签名

- 安装名称 Sakugapp，项目名 Sakuga Mobile，applicationId `com.sakugapp` 不变。
- 公开版本仅有 v1.0.0；本版 v1.1.0 / versionCode 10，高于所有已分发候选版（最高 code 9）。候选版曾显示 1.2.x，Android 升级按 versionCode 判定。
- 正式签名沿用 `release-signing` 环境 Secrets，不生成新密钥，不使用 Debug 签名。
- 证书 SHA-256：`9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff`。
- GitHub Release 构建验证精确标签、main 祖先关系、非 Debug APK、证书、ABI、单测和 Lint；先生成草稿，完成检查后公开。

## 功能复核

| 范围 | 实际行为 | 证据 |
| --- | --- | --- |
| 浏览与搜索 | 连续分页/去重、返回位置、滚动隐藏/按需搜索、组合条件、50 条历史、光标联想 | Feed、SearchSyntax、SearchEditing 及设备测试 |
| 平均色与网格 | 可见最多 8 张缩略图均色/HEX；手机 1/2/3/4、平板另有 8/12 列 | AverageColor 源码、PublicPaginationTest；不宣传服务端色彩检索或任意 1–12 整数列 |
| 详情 | 五图标同排、边界禁用、长按名称、统一多夹收藏、元数据换行、原标签颜色与评论 | CompactInterfaceDeviceTest、PublicBrowseTest |
| 播放器 | 旋转、A–B 时间戳循环、真实索引步进/帧号、9 档倍速、音量、缩放/双击/切帖、折叠高级控制 | PlayerRotationDeviceTest、ProfessionalPlayerDeviceTest、DeviceTests |
| 收藏夹 | 本地多夹、默认快速收藏、JSON、独立夹内页、封面/排序/批量管理 | LocalLibraryDeviceTest、LocalLibraryUiDeviceTest、LibraryDocumentsDeviceTest |
| 外观 | 系统/深/浅、Post 浮块、实际时长/Score、统一展开动画、四语资源 | UiSmokeTest、布局断言及真实截图 |

## 升级兼容

数据库文件 `local-library-v1.db` 不变。Room v1→v2 只清理废弃表，其余四张表结构与数据保持；不使用破坏性迁移。当前 v2 schema 与 code 9 相同。设置沿用 `settings_v1` 和原主题、语言、网格、换行及历史键，不清空 DataStore。旧收藏 JSON version 1 可继续合并导入。

设备迁移测试从仓库已提交的原始 v1 schema 建库，核对帖子、双夹关系、封面、顺序、时长及可继续读写；系统文档测试实际执行导出/删除测试夹/导入/合并。正式签名覆盖安装检查从已发布 v1.0.0 开始，不卸载、不清数据，核对浅色背景实际像素、换行设置及旧搜索历史。

## 构建与运行记录

本轮基线 `./gradlew testDebugUnitTest lintDebug assembleDebug` 已通过。最终发布提交、Debug / Release 构建、模拟器与升级结果在完成后更新于此，不以旧运行冒充最终版本结果。

本轮之前的同应用源码验收：[24 项设备测试](https://github.com/Milenal82/sakugapp/actions/runs/40392216263) 与 [正式证书覆盖安装](https://github.com/Milenal82/sakugapp/actions/runs/40392204087)均通过。截图原件取自前者，详情见 `screenshots/README.md`。

## 限制

未重测真实账户的登录成功、Vote、评论写入；不会为了发布测试修改线上内容。API 28 x86_64 模拟器不代表全部真机与解码器。严格帧分析和批量离线下载存在 README 列出的格式、大小和运行条件限制。A–B 不是帧级循环；未知时长不推算。没有提供的“功能 2”仍无需求与实现，不列为完成。
