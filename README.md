# Sakugapp

Kotlin + Jetpack Compose 原生 Android 项目，采用 Material 3 深色主题。
首页为两列占位帖子网格，未连接 Sakugabooru，不包含真实帖子或模拟互动数据。

## 开发环境

- Android SDK Platform 35、Build Tools 35.0.0；最低 Android 8.0 (API 26)。
- Android Gradle Plugin 8.9.2、Gradle Wrapper 8.13、Kotlin / Compose 插件 2.1.20。
- 推荐 JDK 17（CI 使用）；Gradle 8.13 也支持 JDK 21，编译目标为 JVM 17。
- Compose BOM 2025.04.01。

安装 Android SDK 后，设置 `ANDROID_HOME`，或在未跟踪的 `local.properties` 中设置 `sdk.dir=/your/sdk/path`。

```sh
./gradlew assembleDebug
./gradlew lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`（调试签名，不用于生产发布）。
使用 Android Studio 打开根目录，选择 API 26 以上设备运行 `app`；或使用 `adb install -r` 安装 APK。
GitHub Actions 会编译、运行 Android Lint 并上传 `sakugapp-debug` 构件。

## 尚未实现

Sakugabooru API、网络请求、分页、搜索、标签、详情、媒体播放、收藏及用户登录。
当前卡片不可点击，且明确标记为占位状态。尚无自动化 UI 测试。
