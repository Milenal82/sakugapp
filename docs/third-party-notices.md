# 许可证与来源

本项目新编写的应用代码按 **GPL-3.0-only** 发布，完整许可证见根目录 `LICENSE`，APK 同时包含 `assets/licenses/GPL-3.0.txt`。修改、再分发时请保留许可证并按 GPL 提供对应源码；每次 CI 构建提供同一提交的完整源码 ZIP。Debug APK 使用调试签名，不含发布密钥。

## Sakuga Extended 调查

- 作者与项目：ftLoic / [Sakuga-Extended](https://github.com/ftLoic/Sakuga-Extended)
- 核查提交：`8229ac56e022d51343cdfd12b997e5ddc2edd4b9`；manifest 版本 2.1.3。
- 根目录 `LICENSE` 是 GNU GPL version 3；本次未发现授权应用代码使用“or later”的单独声明，因此不扩大为 GPL-3.0-or-later。
- 本轮以功能和公开协议为参考，独立编写 Kotlin/Compose 实现。没有复制或翻译其 JavaScript、CSS、图标、游戏、`libgif.js` 或 `fastaveragecolor.js` 到应用中。
- 通用 GPL 许可证文本允许原样复制，已保留全文。若后续移植扩展代码，必须保留其作者/来源声明、标注改动并继续履行 GPL 源码义务；不能将其嵌入式第三方库一并假定为 GPL。

## 应用依赖

| 组件 | 使用版本/来源 | 许可证 |
| --- | --- | --- |
| Kotlin 标准库、Compose 编译插件 | 2.1.20 / JetBrains | Apache-2.0 |
| Kotlin Coroutines | 1.10.2 / kotlinx.coroutines | Apache-2.0 |
| AndroidX Activity、Lifecycle、Compose、Material 3 | `app/build.gradle.kts`、Compose BOM 2025.04.01 / Android Open Source Project | Apache-2.0 |
| AndroidX Media3 | 1.6.1 / Android Open Source Project | Apache-2.0 |
| Coil / Coil GIF | 2.7.0 / Coil contributors | Apache-2.0 |
| AndroidX DataStore / Navigation Compose | 1.1.7 / 2.9.0 / Android Open Source Project | Apache-2.0 |
| jsoup | 1.18.3 / Jonathan Hedley | MIT，全文随 APK |
| OkHttp、Okio（传递依赖） | OkHttp 4.12.0 / Square contributors | Apache-2.0 |
| Gson | 2.12.1 / Google | Apache-2.0 |
| Gradle Wrapper | 8.13 / Gradle | Apache-2.0；生成脚本保留原声明 |
| JUnit | 4.13.2，仅测试 | EPL-1.0 |
| MockWebServer | 4.12.0，仅测试 / Square contributors | Apache-2.0 |

Apache-2.0 全文随 APK 放在 `assets/licenses/APACHE-2.0.txt`。OkHttp 的 Public Suffix List 数据另按 MPL-2.0 授权，原始 NOTICE 与 MPL 全文随 APK 保留；来源为 https://publicsuffix.org/list/public_suffix_list.dat ，本项目未修改该数据。构建依赖自身的版权与 NOTICE 保持不变；不将第三方库改为本项目许可证。组件的依赖图可通过 `./gradlew :app:dependencies` 检查。

Sakugabooru 帖子和媒体仍归其各自权利人所有，应用许可证不授予帖子内容的版权。测试样本仅保留公开 API 的必要字段；获取地址、时间、摘要和脱敏规则在 `app/src/test/resources/live/provenance.json` 中。应用不捆绑站点图片或视频文件。
