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

本轮完整检查已通过：

| 检查 | 实际结果 |
| --- | --- |
| 本地 Debug / Release | 各 96 项 JVM 测试通过，0 失败/错误/跳过；Lint 各 0 错误 / 7 警告；Debug APK 与未签名 Release 预检成功 |
| [GitHub 完整检查](https://github.com/Milenal82/sakugapp/actions/runs/40445851626) | 提交 e72bf47；Debug、96 项 JVM 测试、Lint 及 API 28 x86_64 KVM 24/24 设备测试全部通过 |
| [正式证书候选与覆盖升级](https://github.com/Milenal82/sakugapp/actions/runs/40444402161) | 1.1.0 / code 10，96 项 Release 测试、Lint 0 错误 / 24 警告；v1.0.0 → 候选覆盖安装成功，主题/换行/历史保留，原生签名 UI 检查 1/1 |
| 资源与发布工具 | 378 条四语资源和格式参数、16 项 Python 测试、actionlint、源码 ZIP 完整性/敏感文件检查通过 |

候选检查早于最后的缩略图文字行高修正；完整检查已包含最终应用代码。最终标签的 APK 会由 Signed Release draft 工作流重新构建、核对源码提交并执行同证书覆盖安装，只有该工作流和 main 检查成功后才公开。精确产物结果见 Release 的 `release-verification.json` 及 [发布构建记录](https://github.com/Milenal82/sakugapp/actions/workflows/release.yml)。

修复记录：首轮新增加的完整显示断言失败。实际日志显示紧凑数值为 `3`、测量尺寸 15×26 px、可用宽度 107 px，但段落宽度估计标为溢出。最终直接断言每个字形边界、末尾字符、无省略号和单行显示，并保留最小字体与真实 Score 颜色断言；完整 24 项设备测试通过。未以删去显示检查来掩盖问题。高密度标签使用紧凑数值与明确行高，避免原先 Score 文案换行。

未关闭 Lint 检查或添加 baseline。警告包括现有 API/方向建议、复数资源、受限官网浏览 JavaScript、KAPT、日期排版及 CI 依赖更新建议；不是构建失败。

README 的两张截图已人工检查，来自 [此前同详情/设置实现的运行](https://github.com/Milenal82/sakugapp/actions/runs/40392216263)，详见 `screenshots/README.md`。隔离测试中残留旧画面的图片未作为产品截图保留。

## 限制

未重测真实账户的登录成功、Vote、评论写入；不会为了发布测试修改线上内容。API 28 x86_64 模拟器不代表全部真机与解码器。严格帧分析和批量离线下载存在 README 列出的格式、大小和运行条件限制。A–B 不是帧级循环；未知时长不推算。没有提供的“功能 2”仍无需求与实现，不列为完成。
