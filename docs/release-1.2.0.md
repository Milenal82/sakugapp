# Sakugapp 1.2.0 增量开发

基线为上一轮已验证的开发提交 `b9b8f8f43f527d575922444f348ad8eb8e85b83b`；远端 main 仍为 `cbe0453`，正式 Release 仍为 v1.0.0。分支 `feature/personalization-pro-player`，包名 `com.sakugapp`、正式签名、旧 DataStore 和会话格式保持不变；versionName 1.2.0 / versionCode 8。

附件实际列出功能 1、3–11，共 10 项。用户确认功能 2 未提供，不将壁纸假定为需求。

## 分阶段记录

- 基线：`testDebugUnitTest lintDebug assembleDebug` 成功，82 项单测通过。
- 阶段 1：主题 Token、系统/深/浅模式及旧设置迁移、12dp 卡片、真实 Score 徽章、未知时长明确占位、播放器实际时长回传、集中动画时长和历史动画；`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 成功，85 项单测通过。设备运行待最终 CI。
- 阶段 2：手机保持 1–4 列，最小宽度 600dp 的平板提供 8/12 列；保留跨窗口宽度变化的原始列数设置，普通手机竖屏、平板尊重系统旋转。新增真实分页设备用例中的平板密度/Score/截图验收；运行结果待 CI。
- 阶段 3–5：开发中。

公开帖子 JSON 本轮从云环境请求收到 HTTP 403；没有绕过。没有为缩略图批量下载完整视频来猜测时长。实际视频时长来自 Media3，未获取时显示“—”，不是 0 或示例时长。
