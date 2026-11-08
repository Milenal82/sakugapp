# Posts and navigation performance investigation

Baseline application: `65124526ed12726f06fa41249dd78f4f7e368a1a`. Development branch: `dev/performance-navigation`. Debug only; package, version, signing and user-data formats are unchanged.

## Evidence and changes

| Finding | Evidence | Targeted change |
| --- | --- | --- |
| Bottom chrome changes the viewport while scrolling | `Scaffold.bottomBar` used expanding/shrinking visibility. Its measured height is part of NavHost content padding, so each animation frame changes grid constraints. | Keep a fixed bar-sized space on main pages; slide/fade only the bar. Detail/fullscreen still have no bottom bar. |
| A main-tab tap can navigate and then pop a restored child | `primary()` used `restoreState=true`, then a second `popBackStack` if restoration ended on a child. | A single main navigation action; entry-scoped SaveableStateRegistry snapshots retain main-page UI state without restoring a child stack. Entry registries remain independent during rapid interrupted transitions; late disposal cannot overwrite a newer page snapshot. Global ordinary-feed cache/scroll and result-entry state remain separate. |
| Automatic preview churn | Initial API 28 fixture scrolls created 16 additional players during one 24-swipe two-column phase. Disabling automatic preview could retain its current selection until it left the viewport. | A cancellable 450 ms stable-idle wait; manual actions remain immediate. Pause an onscreen automatic player while scrolling, release offscreen/disabled automatic selections, and prevent pending starts in background. Manual previews are kept independent. |
| Feed/favorite facts invalidate the application root | Feed, selected detail, full library and history were all collected at the global Scaffold. Duration projection also created a fresh list for unrelated facts. | Collect in the consuming routes, select distinct duration maps, preserve unchanged Feed/Post identities and remember navigation callbacks. |
| Caption work repeats for non-tag changes | Caption selection was keyed by the entire Post and AppSettings. | Key by tags and actual caption settings; avoid per-tag temporary category sets; retain stable Post IDs and use a homogeneous grid content type. |

The player, frame decoder, clipping engine and cache architecture are retained. Page cache is already bounded at 200 MiB with LRU/leases/exit cleanup. Images already use bounded Coil memory/disk caches. Existing private/SINGLE/BATCH media priority and local cache bypass are retained. No adjacent-video prefetch or permanent cache was added.

## Measurement protocol

- Actual Android frame clock and Window FrameMetrics, not Compose's synthetic test clock. Native touch injection drives the production Sakugapp composables in a Debug-only fixture host.
- 300 captured response records retain IDs/tags/scores. Deterministic generated 320×180 thumbnails and an MP4 are seeded into owned offline files. Synthetic media URLs are verified to resolve locally before rendering; this is not a live-network benchmark or real video-format distribution.
- 2/4/6 columns on phone; also 8/12 on tablet. Automatic previews off/on. Twenty main-tab switches, detail return, rendered transition captures, process memory and ExoPlayer Init/Release logs.
- Bounded bidirectional swipes avoid interpreting intentional top pull-to-refresh as duplicate loading. Refresh revisions and repository request logs are recorded separately; navigation requests must not increase.
- Paired baseline/current/current/baseline trials use the same emulator and harness, fresh disposable fixture data per trial, and warmed UI. Only the dedicated workflow emulator's test-app data is cleared.
- API 35 software rendering uses 1280×900 at 160 dpi, preserving the Pixel C logical tablet layout while reducing SwiftShader pixel cost. This is a controlled test viewport, not a change to application image quality. API 28 uses its normal Nexus S viewport.
- CSV frame samples, JSON summaries, native captures and atrace system traces are retained as Actions artifacts. Trace phase markers identify the measured windows.

`over_budget_frames` means FrameMetrics TOTAL_DURATION exceeds the display refresh interval. It is not SurfaceFlinger FrameTimeline's official jank classification. Component durations and whole-Recomposer change counts are recorded; individual visible-card recomposition counts, physical-device GPU timings and measured UI-thread blocked intervals are not claimed.

The first unnormalized tablet measurements were dominated by SwiftShader swap/render waits. The first exporter also failed to copy wildcard paths; export now copies and validates each file explicitly. A baseline whole-run request assertion initially counted legitimate pull-to-refresh as a navigation failure; the final harness separates those actions. These initial runs establish diagnostic leads, not a final before/after comparison.

## Verification and results

Initial application baseline: localization, 192 unit tests, Lint and assembleDebug passed before application edits. New policy tests cover delayed/cancelled automatic starts, manual selection/stop, disabling auto, inactive/background pages and unchanged duration identities. New device checks cover one navigation action, unchanged grid bounds, saved drafts/cache/scroll, outgoing transition content and preview-player pause/background reuse. Existing player, cache, search, community, quiz, discovery and backup regressions remain enabled.

Final paired measurements and device outcomes are reported with the delivering commit and Actions run. No unmeasured improvement percentage is claimed. If an optimization regresses the controlled cases, it must be revised or removed before delivery.

Physical-device follow-up: real thumbnail formats and network variability, vendor codecs/audio focus, variable refresh rates, system reduced-motion configurations, long-session memory trends, authenticated website operations and OEM gesture behavior. Short Debug/AVD runs cannot establish universal smoothness or eliminate all crash risks.
