# Sakugabooru 数据源调查

验证日期：**2026-10-22（UTC+8）**。本次确实请求了站点公开接口、帮助页、帖子页、缩略图和视频片段，没有使用需求中的示例 JSON 作为接口证据。HTTP 证据见 [live-source-2026-10-22.json](evidence/live-source-2026-10-22.json)；脱敏响应见 `app/src/test/resources/live/`。

## 公开接口与实际结果

| 地址（基址 `https://www.sakugabooru.com`） | 参数/结果 | 本次验证 |
| --- | --- | --- |
| `/help/api` | 站点 API 说明，标题 API 1.13.0+update.3 | HTTP 200；说明 JSON 后缀、limit/page/tags、标签类型及错误码 |
| `/post.json?limit=2&page=1&tags=rating%3Asafe` | 顶层是帖子数组，**不是** HTML 中 `Post.register_resp` 的包装对象 | HTTP 200；ID 319947、319946 |
| `/post.json?limit=2&page=2&tags=rating%3Asafe` | page 从 1 开始、按页偏移 | HTTP 200；ID 319943、319942，与第一页无重复 |
| `/post.json?limit=2&page=1&tags=jujutsu_kaisen_season_3` | 标签按空格组合；URL 参数正常编码 | HTTP 200；返回帖子的 tags 均包含所搜标签 |
| `/post.json?limit=2&page=1&tags=sakugapp_verification_nonexistent_tag_20261022` | 无匹配项 | HTTP 200，真实空数组 `[]` |
| `/tag/summary.json` | `{version, data}`；本次 version=7352 | HTTP 200，取得分类及别名 |
| `/post/show/319947` | 原生详情的原帖链接 | HTTP 200；HTML sidebar 核实 `tag-type-artist` / `tag-type-copyright` |
| `/data/preview/0618e7f33173d8122094a1a23e33da49.jpg` | 取自实际 preview_url | HTTP 200；image/jpeg，JPEG 文件头 |
| `/data/0618e7f33173d8122094a1a23e33da49.mp4` | 取自实际 file_url，Range: bytes=0-65535 | HTTP 206；video/mp4；Content-Range 0-65535/16992437；存在 MP4 ftyp 文件头 |

视频地址和范围响应可用不等于 Android 解码/播放已通过；本环境无模拟器和设备，Media3 的实际操作需后续运行验证。以上媒体文件未打包进 APK 或源码。

## 请求与分页约定

应用使用 `/post.json` v1 数组响应，`limit=20`、`page=1,2,...`、`tags=<用户原始标签表达式>`。站点 `/help/api` 声称每页上限 100；未用大请求探测上限，也没有照搬上游源码可配置的其他上限。空字符串查询对应默认帖子列表，无默认虚构内容。

搜索字符串交给 HttpUrl 编码，保留空格、多标签、负标签和元标签语法，客户端不擅自翻译或重写。当前现场验证了普通版权标签、`rating:safe` 以及 `jujutsu_kaisen_series animated -genga` 的多标签/排除组合（HTTP 200，ID 318288、318287 均符合条件）；其他全部元语法未逐一现场验证。自动化测试覆盖 `&` 和空格不会破坏查询参数。

翻页结果按 ID 去重并保留首次出现顺序。失败不增加页码，重试同一页；短页/空页作为结束。重复页仍推进页码，避免重复请求同页。站点使用偏移分页，浏览期间新帖插入、删除或内容变化可能产生重叠/漏项；客户端可去重，但**不能保证动态数据集永不遗漏**。目前采用“加载更多”按钮，避免无限自动请求；快照/游标分页需另行调查。

## 使用字段

| 字段 | 实际类型/含义 | 客户端处理 |
| --- | --- | --- |
| `id` | 正整数；帖子 ID | 必需；无效则报告数据错误 |
| `tags` | 空格分隔的字符串 | 只代表名字，不含分类；去除重复名字 |
| `score` | 样本中为整数（包括真实的 0） | nullable；缺失、null、格式不符显示“未知”，不填 0 |
| `preview_url`、`file_url` | 实际媒体 HTTPS URL | 仅接受无凭据的有效 HTTPS；不拼接或猜测替代格式 |
| `preview_width`、`preview_height` | 缩略图尺寸，样本 150×84 | 保持比例，不强行拉伸；未知尺寸回退布局容器 |
| `width`、`height` | 原文件分辨率 | 有效值才显示 |
| `file_ext` | 样本 mp4 | mp4/webm/mkv 交给 Media3；最终解码取决于设备；不承诺所有编码 |
| `source` | 原始来源文本 | 按文本显示，不执行其中的 HTML/JavaScript |
| `author` | 上传者显示名 | 详情显示；没有则省略；脱敏样本中排除 |
| `created_at` | Unix 秒时间戳 | 转为设备本地时间，保留时区偏移 |
| `rating`、`status` | 样本 `s`、`active` | 显示站点原始值，不猜测未调查的枚举含义 |

实际 API 还返回 md5、用户 ID、frames、sample_url 等字段，本轮未使用。额外字段可忽略；不从文件名计算另一种未证实的地址。帖子详情使用列表返回的同一真实帖子快照，本轮不另行宣称实时刷新详情。

## 标签分类来源

`/tag/summary.json` 的 `data` 格式为：`类型数字\`规范名\`别名\` 空格 ...`。原扩展 `js/preview.js` 使用此接口；上游 Moebooru 的 `app/models/tag/api_methods.rb` 解释了编码格式。本轮真实样本与帖子 HTML 交叉确认：

- `1` → artist，例 `mokmok`、`artist_unknown`；客户端紫色 `#CE93D8`。
- `3` → copyright，例 `jujutsu_kaisen_season_3`；客户端黄色 `#FFD54F`。
- `0` → general；`4` 按站点 API 文档为 character；均使用中性色。
- 其他未知类型或未查到的名字保持 UNKNOWN、中性色，不根据人名/作品名猜测。

原扩展 CSS 的实际 `authors` 和 `series` 配色不等于本轮产品指定颜色；Android 按用户要求采用黄色版权、紫色动画师。分类摘要在 repository 会话内缓存；失败时保留真实帖子并明确提示分类未获取成功，后续请求可重试。分类成功后不会每页重新下载；新出现/发生变化的类别可能需要重启应用刷新。

## 访问约束与安全

本次读取公开数据不需要账号、Cookie 或 API key。未实现登录及写操作；不请求或硬编码密码。网站文档列出 403 拒绝访问、421 节流以及其他 4xx/5xx；客户端同时识别常见 HTTP 429。遇到这些状态只显示错误并由用户稍后重试，不换镜像、猜格式、模拟身份或循环重试以绕过限制。

API 请求每个 ViewModel 最多一个列表任务；搜索取消旧任务，过期结果不会混入新搜索。30 秒 call timeout，关闭连接失败自动重试，响应上限 8 MiB；分类缓存、Coil 图片缓存减少请求。当前没有测量网站的精确速率额度，也未声称其不存在限制。没有自动播放列表中的所有视频。

最初访问被云环境出口策略在 CONNECT 阶段以 403 拒绝；添加站点域名的配置后实际请求恢复，最终验证记录为成功。这与网站自身返回的 403 不同。GitHub Actions 日志还需 `results-receiver.actions.githubusercontent.com`；网络配置和凭据未写入仓库。

## 真实样本与复验

执行 `python3 scripts/capture-public-fixture.py` 可在正常网络策略下重新取得公开样本。只保留必要帖子字段，删除上传者/用户 ID/source 等字段，移除 URL 查询串与 fragment，只保留样本相关的分类记录。原始响应 SHA-256、地址、时间与脱敏说明在 `live/provenance.json`。没有保存 Cookie、认证头或媒体内容。

`LiveFixtureTest` 检查已捕获样本的 ID、Score、尺寸和真实分类；`RepositoryTest` 用同一真实样本通过实际 repository 解析链路。其他手写数据仅用于边界和故障注入测试，明确不称为真实响应。重新捕获会改变样本，需一起审阅并更新快照断言。

站点帮助页和原扩展是直接证据；[Moebooru 源码](https://github.com/moebooru/moebooru/tree/845a739328fdefdccb4ce1970e129762c69d5c96) 仅作为协议补充，不代表已经确认 Sakugabooru 部署的是相同提交。

## Language 后续调查线索

默认帖子 HTML 的 Language 子菜单实际链接为 `/post?locale=en` 和 `/post?locale=ja`，父项才是 `#`。本轮仅检查了链接结构，没有登录/持久化语言切换验证，也没有在应用中放置假 Language 菜单。后续实现需核实 locale 的 Cookie/账户持久化与原生文案策略。
