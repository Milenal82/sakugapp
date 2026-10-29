# 1.2.0 / code 11 开发记录

基线：main `6be8a621d140cb02ba24a45e6ff0560cfd6f9814`，包含用户在 v1.1.0 后的 README 更新。工作分支 `feature/history-tags-refresh`，不移动 v1.0.0 / v1.1.0 标签，不修改公开 Release；包名和正式签名沿用。这里的 1.2.0 是新的开发候选编号，versionCode 11 高于所有旧候选。

## 七项改动

| 需求 | 实现与边界 | 检查位置 |
| --- | --- | --- |
| 下载命名 | 真实 artist 分类名称以 ` + ` 连接，再拼 Source 和 `post_ID`；URL Source 只保留规范化主机名，避免查询、用户信息、签名路径泄漏。文本 Source 清理非法字符、凭据赋值及查询后缀。UTF-8 名称上限 180 字节，扩展名来自实际格式；私有批量下载以序号去重，系统单个下载使用 CreateDocument 的同名保护 | `MediaFileNames`、`VideoDownloadPlan`、`OfflineLibraryMedia`；JVM 命名边界与受控 TLS 设备测试 |
| 顶部刷新 | 原生 Material 3 下拉指示器；到顶后才能触发，保留完整原始查询和网格设置，成功替换第一页并按 ID 去重。失败保留原列表与分页，单独显示重试入口。刷新会取消分页并递增请求代次，旧响应不能覆盖新结果 | `FeedViewModel.refresh`、`PostsScreen`；包含不响应取消的旧请求、刷新期间换搜索的 JVM 用例和实际手势设备用例 |
| 四种新语言 | 法语、西班牙语、俄语、韩语，菜单显示本族语名称；含错误、弹窗、无障碍描述与复数资源。语言保存到原 DataStore，与 Android 应用语言选择器协调。官网 locale 仍只用 en/ja，不改标签、Source、评论和许可证正文 | 410 项资源检查，格式参数和语言所需复数类别；实际选择/重启用例。译文在开发过程中编写并作程序检查，未经过母语审校 |
| 六列 | 手机和平板均可选 6，保留 1/2/3/4/8/12 的原适用范围和旧选择。六列采用紧凑间距、数值 Score、最多两项折叠标签，省略次要时长；图片 Fit/Crop 沿用，容器不拉伸内容 | GridPolicy、PostCard；320 dp、字体 2×、平板横屏、选择保存与原分页列表测试 |
| 浏览历史 | 仅详情真正显示后写入，预加载与曝光不写入；同 ID 更新快照与最近时间。日期分组、缩略图、原始标签、ID 和时间；支持删除、确认清空、关闭记录与返回位置。最多 500 条或 8 MiB，优先淘汰最早浏览项；关闭不删除旧历史 | 独立 `viewed_posts` / `history_policy`，PersonalStore、BrowsingHistoryScreen；去重、持久化、关闭和容量测试 |
| 标签收藏 | 收藏首页“全部收藏”之前的独立“标签收藏”入口；标签长按菜单及搜索结果顶部书签可收藏/取消。搜索结果的列数入口由书签替换，列数仍可在设置修改。复合条件仅提取普通标签，字段/通配符不会伪装成标签。按原标签或备注本地筛选，可排序，点击始终搜索原始值 | 独立 `saved_tags`；TagTools、TagFavoritesScreen；多次保存去重、真实分类色、原值跳转测试 |
| 标签备注 | 本地纯文本，最多 500 个 Unicode 码点、8 行，规范化换行和控制字符。支持未收藏标签，取消收藏保留备注，清备注保留收藏。与原名视觉区分，不覆盖帖子标签，不向网站提交 | 独立 `tag_notes`；JVM 限制/Unicode、Room 独立关系和原生编辑用例 |

未知分类使用普通文字色，不凭名字推断 artist / copyright。再次实际看到有可靠分类的原标签时，仅补全已收藏标签原本未知的分类，不新增收藏或改写原始名称。

## 数据与升级

Room 2→3 新增四张表，旧收藏、关系、封面、顺序和媒体时长表不改；1→2→3 链式迁移仍保留。数据库文件 `local-library-v1.db`、DataStore `settings_v1`、原设置键及下载目录都不更换。

私有下载新增原子写入的 `.media-index`，只用媒体内容键关联可读名称。查找先读新索引，也兼容旧 `ID-URL摘要.ext` 文件；不重命名已有文件。相同 URL 即使后来标签/Source 改变，也继续使用已完成文件。不同内容同名使用序号，不覆盖旧下载。

收藏 JSON 升至 `sakugapp.library` version 2，加入 `tagFavorites` 与 `tagNotes`；接受缺少这些字段的真实 version 1 结构。导入先完整校验，再在同一事务合并，现有记录优先；非法内容不部分导入。未收藏的备注同样备份。历史、账户凭据、Cookie、下载媒体和普通设置从不进入收藏 JSON。旧版应用不能保证读取 v2 备份，请保留升级前的旧备份。

## 验证与构建

```sh
python3 scripts/check-localization.py
python3 -m unittest discover -s scripts/tests -v
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest \
  testReleaseUnitTest lintRelease assembleRelease -PreleaseUnsignedCheck=true
```

本地初次完整检查已通过：Debug / Release 各 107 项 JVM 测试，0 失败/错误/跳过；Lint 0 错误，警告以对应构建报告为准；Debug APK、测试 APK 和明确标记的未签名 Release 预检成功。不把未签名 APK 当作正式签名包。

本机没有 KVM，当前 GitHub API 请求由环境代理返回 403，站点实时读取的 CONNECT 也失败。Git 拉取可用；因此 push 触发既有 Actions，并把聚合报告和原始截图写到独立 `evidence/history-tags-refresh` 分支。本分支不会存 APK、私钥或凭据；源码分支不会被自动回写或公开发布。签名候选仍在受保护环境构建，缺少既有 Secrets 会失败，不回退 Debug 签名。

设备与签名结果以 [证据报告](https://github.com/Milenal82/sakugapp/blob/evidence/history-tags-refresh/report.json) 的 `source_commit` 和 Actions 链接为准。报告出现之前，设备手势、语言切换、数据库迁移和原生页面操作均不能视为已验证。报告中的失败不能算通过。

## 实际截图

[截图及原始运行记录](https://github.com/Milenal82/sakugapp/tree/evidence/history-tags-refresh/screenshots)。新页面由真实 Android 应用和组件截图生成，历史/收藏的数据是合法保留的公开响应样本；刷新失败是受控错误，不冒充本轮实时网站结果。不使用手绘界面或生成图片。

## 已知边界

- 新译文没有母语审校；许可证正文和网站内容不经过界面翻译。
- 单个视频下载的重名由系统文档提供程序处理；私有批量目录由应用显式避免覆盖。Source URL 仅显示主机名以保护隐私。
- 手机保留已有默认竖屏行为，横屏六列在平板配置验证；不为本轮额外改变播放器方向策略。
- 原有 API 28 严格帧、格式/解码器和下载限额继续适用。真机、全部系统版本、其他字体及真实账户写操作未作扩大验证。
- 记录是本地数据，不是账户同步。清空历史不改变收藏或下载；取消标签收藏不删除备注。

## 第一轮云端回归

[运行 40836220823](https://github.com/Milenal82/sakugapp/actions/runs/40836220823) 对应 d4d8329：Debug/Release 各 107 项单测及正式证书构建通过；设备 30/31 通过，唯一失败为刷新错误后的重试点击。历史、标签收藏/备注、四语切换、v1/v2 迁移和 JSON 检查已实际运行；失败不计作全部验收通过。

同一次运行的同证书 v1.0.0→候选覆盖安装已通过，原有主题、换行及搜索历史保留。它不代替 v1.1.0 收藏库的迁移用例，两者分别验证。

修正刷新错误呈现为底部重试条，避免插入列表首项改变锚点并被搜索层遮挡。六列错误状态采用带完整无障碍说明的紧凑图标，增加实际显示宽度、非黑屏截图、返回历史位置和六列/两列切换断言。新的结果见证据报告中后续对应提交。
