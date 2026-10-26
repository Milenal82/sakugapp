# 0.4.0 接口补充

2026-10-22 重新读取官方标签汇总（版本 7358）并捕获 Unicode U+2028 异常名称记录。解析器现隔离该记录，保留其他真实分类；详见 `evidence/tag-summary-regression.json`。实际 AND、OR 及范围/排序组合请求与返回值在 `evidence/search-live-2026-10-22.json`。搜索表达式作为一个 tags 参数原样编码，不在客户端发明组合语义。官网 locale 仍仅 en/ja，新增 zh-TW 只翻译原生应用。用户反馈 Vote 正常，本轮未执行官网受保护写入。

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


## 0.3.0 社区接口与语言补充（2026-10-22）

- 现场读取 `/user/login`，解析真实 `meta[name=csrf-token]`；仅向 `/user/check.json` POST 空用户名/密码做无账户检查，响应 `{response:"unknown-user", exists:false, success:true}`。所以成功条件必须额外要求 `response=="success"` 和有效 id/name，不能仅判断 success。该请求不会注册账户。
- 上游 `user_controller.rb#check` 和公开源码中的 User.check 使用 username/password 的 POST，成功建立会话。App 不保存 password/pass_hash；仅将服务器 Cookie 与身份 AES-GCM 加密到 noBackupFilesDir，密钥在 Android Keystore。
- 写操作前取同源页面 CSRF token，带 Cookie/X-CSRF-Token/Referer；不把凭据放在 URL，不跟随认证请求外部重定向。会话过期/权限不足如实报错。
- `/post/vote.json` 的 GET 实测 404。正确的当前票值查询是 POST `{id}`（不带 score，不改变投票）；匿名现场结果 HTTP 403 / success:false / access denied。提交投票为 POST `{id,score}`，score 在 0..3。接口设计同时核对原站 JS/上游 controller；没有实账号，未在官网执行投票更改。
- `/comment.json?post_id=317910&page=1` 实测 200，返回 3 条真实评论；字段 id/post_id/creator/creator_id/body/created_at。无 post_id 的查询可能返回空，不用它假定全站无评论。测试样本删除 creator/creator_id，保留公开作画说明和时间码，摘要/地址见 evidence/community-comments-provenance.json。
- 评论 create/update/destroy 使用明确 POST，站点权限与 CSRF 保留；本地测试服务校验字段、失败和归属，未在官网创建/编辑/删除评论。正文只按文本显示，时间码单独解析，不执行 HTML/JavaScript。
- `/tag.json?name=mokmok&limit=8` 实测 200，可用于输入建议；`/post.json?tags=id:319947&limit=1` 实测可定位原帖。
- `/forum`、`https://blog.sakugabooru.com/` 与 `post?locale=en/ja` 均实测 200。Blog 保留作者原文，不宣称用 booru 的 locale 参数翻译 Blog 内容。
- 官方 Language 的两个链接为 `locale=en`、`locale=ja`，上游 set_locale 会保存 locale Cookie；没有中文或无效 `#` 菜单选项。Native API 和官方网页采用同一设置，账号页浏览 Cookie 双向同步后通过服务端账户菜单/公开用户对象重新确认身份，不信任 user_info Cookie 自报的登录状态。

证据：evidence/community-api-2026-10-22.json、evidence/blog-2026-10-22.json。所有实账号写操作仍待用户在安装应用后自行验证，不能用 MockWebServer 代替该结论。

## 2026-10-26：1.1.0 分页与联想重新验证

`/help/api` 明确写单次帖子请求上限 100；实际串行请求 `/post.json?limit=60&page=1..5&tags=rating:safe` 均 HTTP 200，每页 60 条，共 300 个不重复 ID。应用默认由 20 改为 60，保留原偏移页/标签/排序语义，开发构造参数可配置 1–100；没有请求上限以上的数据。末页按原始响应条数判断，失败不推进页码，完整重复页暂停自动加载。站点实时变化仍有偏移遗漏风险，不承诺静态快照。

官网 `/post` 在 Accept: text/html 下返回 HTML，部署的 application `.jsout` 与 classic JS 本次均可正常读取（与此前历史 403 的时点不同）。只读检查确认 `TagCompletion` / `TagCompletionBox` 使用 `/tag/summary.json`（含 version）缓存并在本地匹配当前词，且维护最近标签。应用不执行或复制这些网页脚本，继续用已验证、文档化的 `/tag.json?name=gund&limit=8` 做有界候选查询：顶层数组，字段 id/name/count/type/ambiguous；name 是实际匹配条件（实测也匹配词中包含 gund 的标签），不冒称前缀精确搜索。type=1 是 artist，3 是 copyright，0/4 为其他，未知 type 不猜测。count 缺失为 null，不伪造 0。

原生输入 300ms debounce，IME composition 完成前不请求；候选至多 8 个，同一前缀 120 秒缓存、至多 32 个缓存查询；与帖子/分类读取共用 650ms 请求起始间隔，支持取消且 403/429 不自动重试、不找替代绕过地址。候选只替换光标所在词，保留 -/~ 与后面的其他条件；失败时仍可提交原始查询。

URL、日期、响应摘要和 5 页 ID 见 [验证证据](evidence/browse-upgrade-2026-10-26.json)。新增真实样本 `posts-five-pages.json` 只留 id/tags/score/file_ext/file_size；`tag-suggestions.json` 保留公开标签元数据。移除上传者、创建者、来源等字段；不提交原始 HTML、Cookie、CSRF 或脚本。SHA-256 与来源均记录在同一证据文件。
