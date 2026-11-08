# Debug: actions, grid spacing and artist links

Baseline: main `5170eba355ff2359b1aba9bd9f3fbf637cf57ac7`. Branch: `dev/debug-actions-grid-artist`. Official version remains 1.3.0 (16). No Release tasks, signing changes, tags or publication.

## Changes

- Detail actions: Previous, Next, Vote (0–3), local Favorite, Android Share and Download; six equal slots in one fixed-height row. Post ID opens the original. Vote feedback uses a small busy indicator, confirmation toast and retry dialog, without changing row height. Existing server-confirmation/Score propagation and folder picker remain.
- `GridSpacingPolicy` handles inter-card spacing only: both axes use the saved 0–10 dp value for Posts/search, local folder contents, website Favorites and episode posts. Folder-home cards retain their separate 8 dp layout. Outer padding, thumbnail ratio and caption padding are independent. Existing phone/tablet column restrictions remain.
- Verified Artist tags open the existing action dialog with Search, statistics, favorite and note actions. Unknown types never gain Artist actions. Related-statistic queries retain the artist AND related tag.
- Show and episode Artist lists use only currently loaded, deduplicated Posts and verified categories. Episode filters use locally parsed episode membership, then display those sampled results in Posts; they do not guess the meaning of remote `source:` matching. Return to the episode to load more and filter again.
- Saved tags support persistent-in-navigation selection, count/clear, AND search with raw names, and editing in the existing combined-search dialog. Tag storage and backup format remain unchanged.
- Six recently used query snapshots and eight detail source-order snapshots are retained in memory. Entry state stores tokens/query text, never serialized Post objects. Search grids have entry-specific scroll state. Evicted/after-process-reclaim ordinary queries reload safely; lost episode-only snapshots display an empty sampled scope and a return-to-episode instruction rather than querying outside that episode.

## Playback and cache assessment

| Path | Finding and treatment |
| --- | --- |
| Online detail | One remembered ExoPlayer per media identity; shared page cache feeds playback, decoder ranges and clip staging. Kept. |
| Offline detail | Previously checked private/BATCH files only. Added readable SINGLE receipts after them. Invalid/revoked receipts still fall back to the remote original. |
| List preview | Previously always passed the remote URL. Resolve private/BATCH/SINGLE locally on IO only when preview starts. Offscreen/inactive preview state still stops decoding. |
| Local file/content URI | Previously passed through CacheDataSource and could duplicate local bytes into cache. Bypass caching for local schemes while retaining cleanup leases. |
| Fullscreen | Same remembered player/cache; view/rotation changes do not change media identity. Existing lifecycle pause/stop/release/listener removal retained. |
| Adjacent posts | Only neighboring thumbnails are enqueued, with disposable requests. No new video preloading. Saved source-order contexts prevent a later query from changing an older detail’s previous/next order. |
| Precise clips/frames | Local input stays local; online staging and decoded frame reads reuse page cached byte ranges. Existing precise exporter/frame algorithms unchanged. |
| Images | Coil memory is capped at min(48 MiB, 15% memory class), disk at 128 MiB. Existing cache/size behavior retained. |
| Resource/cache bounds | 200 MiB per-page disk LRU, unique page directories, reader/player/decoder leases, asynchronous cleanup and startup orphan deletion retained. Cache failures fall back upstream. URI keys include path/query, so different representations do not silently share bytes; no authenticated video source was introduced. |
| Network/state | Existing OkHttp cancellation and public-read throttle retained. Detail request generations now also reject late results from non-cooperative cancelled work. |

The cache is intentionally temporary and may evict bytes under its LRU limit. It does not promise zero network traffic for every seek or a stale upstream resource that changes behind the same URL. No performance percentage or long-session crash guarantee is claimed.

## Verification

Baseline localization, `testDebugUnitTest lintDebug assembleDebug`: passed before edits.

Local verification: 192 unit tests passed, zero failures/errors; Lint passed with 0 errors and 56 warnings. `assembleDebug` and Debug instrumentation APK compilation passed. Debug-only CI also passed on API 28 / Nexus S and API 35 / Pixel C. Fresh CI Lint reports 0 errors and 75 warnings (local incremental report: 56). [Branch checks and APK artifacts](https://github.com/Milenal82/sakugapp/actions/workflows/debug-only.yml?query=branch%3Adev%2Fdebug-actions-grid-artist). Added unit tests cover classification/deduplication, AND construction, episode scope, query snapshots/Score, process-cache loss and stale detail results. Added device coverage measures the six-action row and actual 6/8/12-column grid gaps, and exercises Artist/statistics and tag multi-select navigation. Existing MockWebServer Vote tests cover 1/2/3/0 and failure without credentials or live mutations. Page-cache tests cover partial/repeated reads, actual ExoPlayer loops, fullscreen/view replacement, cleanup, URL separation and local bypass.

Localization checks cover all eight UI languages and formatting arguments; no native-speaker review is claimed. Device scenes use local fixtures and Fake repositories, not production placeholder posts. The development workflow uses only Debug tasks and uploads APK/test/Lint/device evidence.

Manual follow-up: real website authenticated Vote/reopen/Score, Android share targets, actual video downloads/storage permissions, vendor codecs/audio focus, offline SD-card revocation, background/rotation recovery, precision clipping across formats and long-session smoothness on physical phones/tablets. Emulator evidence is not a physical-device pass.
