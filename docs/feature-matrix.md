# Sakuga Extended → Android 功能对照（0.3.0）

调查基准仍为用户指定的 `ftLoic/Sakuga-Extended`，固定提交 `8229ac56e022d51343cdfd12b997e5ddc2edd4b9` / 2.1.3，GPL v3。没有复制扩展 JS/CSS/图片/游戏代码。新增模块独立实现，沿用原许可证。DATA.zip 已检查：包含 0.2.0 源码和 CI 报告，报告中的 6 条为依赖更新建议；附件没有被执行，也未覆盖工作区。

本表是当前实现状态；设备完整流程和 CI 最终结果见 `test-report.md`。代码已实现不等于所有网站写操作已用真实账户验证。

| 模块 / 原扩展位置 | 当前 Android 实现 | 状态 / 验证边界 |
| --- | --- | --- |
| 卡片标签与 Score：`js/preview.js` | 两列统一 16:9 长方形容器；默认 Fit 不拉伸，可设 Crop；下方只保留黄色 copyright、紫色 artist 与真实 Score，空格分隔、默认自动换行；未知分类不混入卡片 | 已实现；真实响应解析与分类测试通过；缺失 Score 为 `?`，不填 0 |
| 视频/GIF 预览：`js/preview.js` | 触屏播放按钮、单个活动视频、移出可视范围释放；自动预览/计费网络开关；默认静音 | 已实现；不通过鼠标悬停，不猜失败格式地址；设备交互结果单列 |
| 骨架屏 | 六张无数据身份的脉动占位布局 | 已实现；网络失败不填假帖子 |
| 高级搜索：`js/search.js` | 原语法输入、排序/rating 筛选、450ms 防抖标签建议 | 已实现；URL 编码、排除标签、过滤条件测试；服务拒绝时不自动重试抓取 |
| 近期标签推荐：`js/index.js` | DataStore 最近 30 条搜索，去重、点击重搜、完整列表及清空 | 已实现；设备持久化与边界测试通过 |
| Score/标签开关、主题：`popup.html`、`js/popup.js`、`js/redesign.js` | 原生深色设置，显示版权/动画师/Score、换行/折叠、缩略图裁切 | 已实现；不照搬八套 CSS 外观 |
| 视频速度与滤镜：`js/settings.js` | 0.1×–3× 的 9 档速度、音量/静音/循环、镜像、亮度/对比度/饱和度（0 为灰度），重置 | 已实现；不把 CSS 模糊/色相等全部等同于视频滤镜支持 |
| 原扩展 24/30 FPS 估计步进：`js/frames.js` | 独立严格帧分析：下载原始视频到私有缓存，MediaMetadataRetriever.getFrameAtIndex，按真实索引前后切换，显示实际帧数 | 已实现并通过 Android 9 真正解码测试（非关键帧 + 非均匀帧间隔 + 往返索引）；从第 1 帧开始，不伪装成当前播放时间的严格帧定位 |
| 帧分析限制 | API 28+；文件 ≤200 MiB，像素 ≤3840×2160，设备/格式必须提供帧数与索引解码；切入时暂停并释放播放解码器 | 不支持时明确失败；API 26/27 不提供近似跳转替代；完整真实视频流程验证结果见报告 |
| 视频时长/进度/全屏：`js/frames.js`、`js/resetVideo.js` | Media3 原生控制器、全屏 Dialog、旋转自适应窗口、返回先关闭分析/全屏；后台暂停 | 已实现；真实视频首帧已在本地模拟器渲染；全流程测试结果单列 |
| 截图：`js/screenshot.js` | TextureView 当前已解码帧截图，私有缓存 + FileProvider 原生分享；无需广泛存储权限 | 已实现；不声称已经保存到相册，未做逐设备截图分享验证 |
| 平均色：`js/fastaveragecolor.js`、`js/search.js` | 可见真实缩略图（最多 8 张）像素均值，显示 HEX 与成功数量 | 已实现；失败图片不计入，不用占位色替代 |
| GIF / 原始图片 | Coil GIF 解码，详情显示真实原文件 | 已实现；不声称 GIF 严格逐帧支持；未移植 libgif 源码 |
| 登录/账户 | 原生 HTTPS `/user/check.json` + CSRF + 会话 Cookie；密码不保存；AES-GCM/Android Keystore 存 Cookie 与服务端身份；官方账户管理页 | 已实现；未知用户即使 `success=true` 也不会登录；加密/清理设备测试通过；没有真实账户的成功认证验证 |
| Vote / 收藏级别 | 官方 0–3 票值，0 解除、3 收藏级；当前票值用无 score 的 POST 查询；更新只接受服务端匹配帖子 ID 的 Score | 已实现；CSRF/编码/错误/不伪造 Score 测试通过；匿名实测 403；实账号写入未执行 |
| 评论与编辑：`js/commentsTooltips.js` | 原生公开评论分页、发帖、本人编辑/删除（确认）；成功后重读服务端列表 | 已实现；真实脱敏评论样本 + 请求流程测试；未用实账号写入 |
| 评论时间码：`js/frames.js` | 安全解析真实正文时间码，点选执行普通时间定位 | 已实现；明确与严格索引模式区分；不执行 DText 中的 HTML/JS |
| 来源作者/AD 自动推断：`js/frames.js` | 原始 Source、作者字段和评论保留；不从模糊姓名推断贡献、类别或确认度 | 未移植自动归因；避免误标真实作者；未提供假开关 |
| Blog / Forum | 独立受限浏览 Activity，导航返回应用；Posts / 原帖链接拦截回原生页面；会话和 locale 衔接 | 已实现；官网 HTML 读取 200；HTTPS / 域名 / 外跳规则有单测；无 WebView 首页/帖子列表 |
| Language | 只提供 English / 日本語；原生文案切换；官网 URL 用实际 `locale=en/ja`，API 同步 locale/Accept-Language | 已实现；设备切换测试通过；站点原文与许可证不机器翻译 |
| 关于/许可 | GPL、Apache、MPL、jsoup MIT 全文、原扩展来源和依赖声明 | 已实现；依赖未改为项目许可 |
| 缓存/网络设置 | 图片仅非计费网络、预览计费策略、图片与帧缓存清理、默认播放选项 | 已实现；普通设置用 DataStore；凭据不进入 DataStore/备份 |
| Chrome scripting、私有 pool DOM 修改、猜 deleted 媒体地址 | 原生状态/认证替代；不移植 DOM 注入、eval 或访问限制绕过 | 不适用 / 有意不照搬 |
| SAKUTRAIN / Panda、用户 tooltip 的完整网页样式、八主题、模糊/色相/sepia | 尚未移植；第三方游戏/库内部许可未逐项审计 | 非核心剩余功能，未标记完成，未捆绑未经检查的资源 |

技术栈新增 Navigation Compose、DataStore 和 jsoup；数据网络仍采用已测的可取消 OkHttp 与独立解析器。源接口和设备测试证据见 `data-source.md`、`test-report.md`。所有需要账号的服务端结果均以真实响应为准，未模拟登录成功、投票成功或评论发布。
