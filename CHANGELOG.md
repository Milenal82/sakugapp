# Changelog

## Unreleased — Search, Profile and discovery

- Three main tabs, a styled SAKUGAPP header, independent search home and preserved browse/result states.
- Private local avatar/nickname, with existing account/favorites/site/settings entry points.
- Read-only sampled artist statistics and conservatively parsed work/episode browsing.
- Debug-only delivery; no formal version, tag, signing or Release changes.


## Unreleased — player controls, community and batch queue

- Compact one-row frame controls, player touch lock and explicit controller visibility.
- Window/aspect-based single-column detail sizing; skip unchanged native video effects.
- Star-menu Vote and collapsed, first-expand-only comments.
- Persistent background batch queue, true bounded concurrency, individual retry and conservative rate-limit backoff.
- Existing version, signatures, favorites and download destinations retained. No Release/APK publication.
- Scope and limitations: [verification notes](docs/player-community-batch.md).


## Unreleased — inline frame controls and clip export

- Add permanent ±1/2/3 frame controls, confirmed frame timestamps for A/B, and right-aligned fullscreen/More actions. Remove the duplicate analysis dialog.
- Keep video geometry and the existing player unchanged when expanding advanced controls.
- Export the shared A/B interval as encoded stream copy or explicitly re-encoded MP4; verify output before writing. Add an independent persisted SAF clip folder; unset destinations open the system picker.
- No version, tag, signing or Release change. Precision/format limits: [player tools](docs/player-tools.md).

## Earlier development — page cache and download folders

- Page-scoped Media3 disk cache (200 MiB LRU), shared by playback and strict frame reads; preserve it across fullscreen/view recreation and clean it on page exit or after a crashed process.
- Independent persistent SAF destinations for single and batch videos, with public Movies defaults, progress/cancellation and unchanged file naming. Keep old private playback and add copy-only legacy export.
- No version or signing change; no public Release for this development update.

## 1.2.2 / code 15 — 2026-11-02

- 精简设置为外观、浏览、播放、数据与关于五组；移除七项开关，迁移旧值并保留用户数据。
- 统一导航、Posts、标签和收藏夹操作菜单，保留一级与二级分类操作。
- 樱花粉、薄荷青完整深浅配色，覆盖页面、菜单、弹窗和播放器面板；旧蓝色选择迁移为薄荷青。
- 视频默认静音与循环，保留临时播放操作；浏览历史始终记录，图片和预览不限制 Wi-Fi。

## 1.2.1 — 2026-11-01

- 一级收藏夹拖拽排序，夹内二级分类、多分类关系与非破坏性 Room 3→4 迁移。
- 备份 schemaVersion 4，兼容旧格式；新增蓝色（青色）和粉色强调色。
- 减少 Posts 重组、标签/文字布局分配与重复快照复制；保留资源上限及缓存。
- 纳入上一轮全屏与播放器资源修复。


## 1.2.0 / code 13 — release

- Add immediate, persistent 75%–125% app text size and 0–12 dp extra spacing between preview tag rows. Keep media dimensions, icon sizes and touch targets unchanged.
- Retain the preceding development updates and the existing official signing identity. The earlier 1.2.1 / code 12 was an unpublished development build.
- Release signing, APK verification, same-certificate upgrade and the exact source commit’s scoped appearance checks must pass before v1.2.0 is published.

## 1.2.1 / code 12 — development

This is a development update, not a new public release. The package name and existing signing certificate are unchanged.

- Add portable `sakugapp.library` schema 3 backups with explicit folder keys, covers, ordering, multiple post memberships, tag favorites, and standalone notes. Keep legacy v1/v2 imports; preview counts and conflict choices before an atomic merge, then report added/skipped/failed items. Existing notes are retained by default.
- Use consistent wrapping search-tool tiles while retaining queries, suggestions, history, and tool state.
- Group browsing history by relative date, with a compact count/recording header and separators. Group existing settings into rounded sections without changing preference keys.
- Rewrite the README in Chinese, English, and Japanese with shared navigation and current build/backup guidance.
- Replace the launcher artwork with the supplied pastel icon, including legacy density assets and an adaptive launcher resource.

Backup details: [format and compatibility](docs/backup-format.md). The current update's verification results are recorded in [the test report](docs/test-report.md); prior runs are not evidence for new changes.

## 1.2.0 / code 11 — development

- Add safe Artist/Source/Post ID download names, pull-to-refresh with request-generation protection, French/Spanish/Russian/Korean UI resources, six-column grids, local browsing history, tag favorites, and independent tag notes.
- Follow up with explicit episode naming, combined-search history drafts, proper search back navigation and list restoration, and grouped advanced player controls.
- Preserve existing folders, settings, downloads, and legacy JSON imports. See [development details](docs/development-1.2.0.md) and [follow-up verification](docs/incremental-search-download-controls.md).

## 1.1.0 — released

- Consolidate continuous browsing and search tools, native details, advanced player controls, local multi-folder favorites, and theme/layout improvements.
- See the [release notes](release-notes/v1.1.0.md) and [release checks](docs/release-1.1.0.md).

## 1.0.0 — released

- First signed release. See the [release notes](release-notes/v1.0.0.md).
