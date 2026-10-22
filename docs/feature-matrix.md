# Sakuga Extended → Android 功能对照

调查日期：2026-10-22。参考项目已由用户确认：[ftLoic/Sakuga-Extended](https://github.com/ftLoic/Sakuga-Extended)。核查固定提交 [`8229ac56e022d51343cdfd12b997e5ddc2edd4b9`](https://github.com/ftLoic/Sakuga-Extended/tree/8229ac56e022d51343cdfd12b997e5ddc2edd4b9)，manifest 2.1.3，最后提交 2024-06-07。以下源码路径均相对此提交。

已读 README（只有项目简介）、GPL-3.0 LICENSE、manifest 的匹配规则/权限、popup.html、popup.js、settings.js、主要列表/搜索/播放器源码、相关 CSS，以及 changelog 和最近 10 次提交。2.1.3 主要修复评论名字识别与弹窗样式；2.1.2 修复来源识别及含 `&` 的搜索；2.1.0 新增 AD 来源识别和搜索选项；2.0.5 切换 manifest v3 与直接媒体链接控制。原扩展功能多于 README 中的一句话。

“已实现”指代码、构建和相关单元测试通过；**不代表 Android 设备交互测试已完成**。本环境没有 emulator/system image、设备或 `/dev/kvm`。下面的“现场”仅指真实 HTTP/API 检查；没有使用 WebView 实现列表或详情。

## 原扩展功能

| 原功能 | 原源码位置 | Android 原生方案 | 本轮状态 | 验证/未实现原因 |
| --- | --- | --- | --- | --- |
| 列表 Score 与标签摘要 | `js/preview.js` 的 loadPosts，`css/preview.css` | nullable Score + Compose 卡片 | 已实现 | 真实样本 33 和 0；缺失保持未知的单测通过；设备 UI 未验证 |
| artist/copyright/其他分类与别名 | `js/preview.js`，`/tag/summary.json` | 独立分类解析、黄色版权/紫色动画师，未知中性色 | 已实现 | 真实摘要、帖子 HTML 与解析单测交叉验证 |
| 显示/隐藏 Score、标签配置 | `js/popup.js`、`popup.html` | 后续 DataStore 设置 | 待办 | 本轮固定显示，不放置无效开关 |
| 标签缩写、隐藏已搜索标签、presumed 标记 | `js/preview.js` 的 abbr/loadPosts | 保留原标签名；后续可选显示规则 | 未移植 | 避免缩写改变标签搜索语义；presumed 原样显示，不推断作者 |
| 鼠标悬停 MP4/WebM/GIF 预览，快速片段扫描 | `js/preview.js` 的 onmouseenter、timer | 触屏播放按钮 + 单个可见项播放器 | 待办 | 本轮仅详情播放；不将鼠标悬停逻辑移植为触屏行为 |
| 从 MP4 失败猜 WebM / deleted-preview 恢复 | `js/preview.js` 的 _onerror、deleted-preview 分支 | 只使用真实 API 地址并显示错误 | 有意不照搬 | 不猜 URL、不通过隐藏/删除内容绕过站点限制 |
| 原生视频播放/暂停、时间与进度 | `js/frames.js`、`js/resetVideo.js` | Media3 PlayerView 原生控制器 | 已实现 | 编译/Lint通过；真实 MP4 206 可取；设备播放未验证 |
| 循环、静音、全屏 | `js/frames.js`、HTML video、`js/resetVideo.js` | 同一个 ExoPlayer、循环/静音开关、全屏 Dialog | 已实现 | 编译通过；后台暂停/释放生命周期代码；设备操作未验证 |
| 24/30fps 假设的前后帧与快捷键 | `js/frames.js` 的 timeFromFrame、seek、readShortcuts | 后续调查 Media3 帧能力 | 待办 | 不将普通时间跳转标为严格逐帧；触屏需替代键盘 |
| GIF 播放/暂停、按帧 slider | `js/frames.js` 的 gifManager，`js/libgif.js` | 后续独立 GIF 解码方案 | 待办 | libgif 仅识别其用途，未逐行检查算法/许可证；不纳入 APK |
| 倍速、24/30fps 选择 | `js/settings.js` | 后续 Media3 playbackParameters | 待办 | 本轮播放速度固定 1×，不提供无效控件 |
| 亮度/对比度/模糊/色相等滤镜、水平翻转 | `js/settings.js` | 后续评估视频变换/效果能力 | 待办 | 不直接照搬 CSS 到 Media3 |
| 截图到剪贴板、自定义截图快捷键 | `js/screenshot.js`、`js/popup.js` | 后续 PixelCopy/MediaStore 与触屏按钮 | 待办 | 本轮没有图片写入/剪贴板操作 |
| 评论时间戳点击、当前作者片段、来源 AD/SB/ED 等识别 | `js/frames.js` 的 buildCommentModifiers/loadTimestamps/updateArtist/loadADs | 后续结构化解析与时间轴 | 待办 | 本轮不读评论；不把启发式来源分析当成已核实贡献 |
| 评论用户 tooltip、原地编辑 | `js/commentsTooltips.js` | 原生评论/用户卡片；编辑必须真实登录 | 待办 | 登录与评论不在本轮范围 |
| 评论图片交互与提示 | `js/pictureEvents.js`、`js/commentsNotice.js` | 原生安全图片查看与文本提示 | 待办 | 不移植 eval/动态脚本执行 |
| 高级搜索（排序、rating、limit、Solo KA） | `js/search.js` | 标签表达式输入；后续原生筛选器 | 部分实现 | 标签和元标签原样提交；搜索 & 编码单测；并未做全部元语法现场验证 |
| 近期标签推荐、快速清空 | `js/index.js`、`js/search.js` 中 recent_tags | 后续 DataStore 搜索历史与建议 | 待办 | 当前可编辑并提交搜索；尚无建议/历史 UI |
| 多主题与局部重设计 | `js/redesign.js`、`css/designs/*`、`css/post_redesign.css` | Material 3 深色主题 | 部分实现 | 本轮只有深色；未复刻各 CSS 主题 |
| 内容平均色背景 | `js/fastaveragecolor.js`、`js/search.js` | 后续可选缩略图调色 | 待办 | 库内部算法和许可证未检查；未复用 |
| 私有 pool 提示隐藏、网站旧播放器切换 | `js/framesScript.js`、`js/resetVideo.js`、popup default_player | 原生只读公开帖子；不修改网页 DOM | 不适用/待办 | 不绕过 pool 权限；原生应用无 VideoJS/旧网页播放器切换 |
| 原始媒体页控制与按 md5 找原帖 | `js/framesData.js` | 详情直接使用 API ID 的原帖链接 | 部分替代 | 原帖链接已核实；直接媒体 deep link 未实现 |
| SAKUTRAIN、Panda 游戏与彩蛋 | `html/sakutrain/*`、`html/panda/*`、`js/frames.js` | 独立娱乐功能后续评估 | 未检查内部实现 | 非本轮浏览核心，未复制资源或代码 |
| Chrome storage/scripting、扩展注入配置 | `manifest.json`、`js/popup-script.js` | Android ViewModel 与应用内状态 | 原生替代 | 无扩展注入，无执行站点 JavaScript |

## Sakuga Mobile 完整需求与本轮边界

| 产品功能 | Android 实现位置/方案 | 状态与证据 |
| --- | --- | --- |
| 两列、真实缩略图、标签、Score | `MainActivity.kt`、Coil；优先显示分类标签，超量折叠到详情 | 已实现；真实 HTTP 图片/样本验证，设备滚动和布局未验证 |
| MVVM、标签输入/点标签搜索、分页去重 | `ui/FeedViewModel.kt`、`data/FeedState`、`SakugaRepository` | 已实现；请求取消、错误重试不跳页、去重测试通过 |
| 加载/空/错误/重试 | 列表底部状态与进度、分类失败提示、媒体错误 | 已实现；无虚构帖子；本轮用进度条，骨架屏待办 |
| 详情 ID/Source/上传者/时间/分辨率/格式/原帖 | `PostDetail` 使用真实 API 字段快照 | 已实现；缺失字段省略；设备呈现未验证 |
| 详情返回保留列表搜索/滚动 | ViewModel 不销毁列表，grid state 在详情分支外保存 | 已实现；单测确认不重载/不清空结果，滚动恢复需设备验证；进程死亡后完整恢复待办 |
| 详情视频基础控件 | `ui/VideoPanel.kt`、Media3 | 已实现；真实媒体可达，设备播放/拖动/静音/循环/全屏未验证 |
| 触屏预览、自动预览与移动网络策略 | 单播放器、可见性和 DataStore | 待办；没有无效预览按钮 |
| 倍速、音量滑块、横屏专用布局、逐帧 | Media3 能力调查后单独实现 | 待办；无“近似时间跳转”等冒充逐帧 |
| 搜索历史、输入建议、搜索返回栈 | DataStore + 后续 Navigation Compose | 待办；目前系统返回关闭详情，首页按系统退出；搜索替换当前条件 |
| Posts/My Account/Blog/Forum/Language | 原生路由、必要时 Custom Tabs/认证流程；先调查语言机制 | 除 Posts 外待办；不放置无效菜单，不宣称登录/Language 完成 |
| 账户、评分提交、收藏、评论 | 真实认证与安全存储后实现 | 待办；只读 Score，不模拟提交结果 |
| 设置、中文翻译、缓存管理 | 原生设置 + DataStore | 待办；关于与许可已提供 |
| Retrofit、Navigation Compose | 首版用可取消 OkHttp + 独立 JSON 解析层、单 Activity 详情分支 | 技术取舍；完整需求为优先采用，后续多页面时可迁移；本轮不为引库而重写已测链路 |
| 发布 APK/源码 ZIP/CI | `.github/workflows/android.yml`、`scripts/package-source.sh` | 本地编译/测试/Lint；实际 CI 结果见 `docs/test-report.md` |
| 许可证和完整项目 | `LICENSE`、`docs/third-party-notices.md`、APK assets | GPL-3.0-only；依赖保持原许可，未复用扩展实现 |

原扩展的游戏、第三方解码库内部细节及所有历史提交并未逐一审查；这些项目没有被标记为功能完成或安全通过。下一阶段按真实设备验证、播放器/预览、设置/搜索体验、账户和站点其他入口的顺序推进。
