# Posts and navigation performance investigation

Baseline application: `65124526ed12726f06fa41249dd78f4f7e368a1a`. Development branch: `dev/performance-navigation`. Debug only; package, version, signing and user-data formats are unchanged.

## Evidence and changes

| Finding | Evidence | Targeted change |
| --- | --- | --- |
| Bottom chrome changes the viewport while scrolling | `Scaffold.bottomBar` used expanding/shrinking visibility. Its measured height is part of NavHost content padding, so each animation frame changes grid constraints. | Keep a fixed bar-sized space on main pages; slide/fade only the bar. Detail/fullscreen still have no bottom bar. |
| A main-tab tap can navigate and then pop a restored child | `primary()` used `restoreState=true`, then a second `popBackStack` if restoration ended on a child. | A single main navigation action; entry-scoped SaveableStateRegistry snapshots retain main-page UI state without restoring a child stack. Main-page fields use stable names across entry IDs; disposal snapshots run before child saveable providers unregister. Entry registries remain independent during rapid interrupted transitions; late disposal cannot overwrite a newer page snapshot. Global ordinary-feed cache/scroll and result-entry state remain separate. |
| Automatic preview churn | Initial API 28 fixture scrolls created 16 additional players during one 24-swipe two-column phase. Disabling automatic preview could retain its current selection until it left the viewport. | A cancellable 450 ms stable-idle wait; manual actions remain immediate. Pause an onscreen automatic player while scrolling, release offscreen/disabled automatic selections, and prevent pending starts in background. Manual previews are kept independent. |
| Feed/favorite facts invalidate the application root | Feed, selected detail, full library and history were all collected at the global Scaffold. Duration projection also created a fresh list for unrelated facts. | Collect in the consuming routes, select distinct duration maps, preserve unchanged Feed/Post identities and remember navigation callbacks. |
| Caption work repeats for non-tag changes | Caption selection was keyed by the entire Post and AppSettings. | Key by tags and actual caption settings; avoid per-tag temporary category sets; retain stable Post IDs and use a homogeneous grid content type. |

The player, frame decoder, clipping engine and cache architecture are retained. Page cache is already bounded at 200 MiB with LRU/leases/exit cleanup. Images already use bounded Coil memory/disk caches. Existing private/SINGLE/BATCH media priority and local cache bypass are retained. Duration facts are already trimmed to 1,000 database rows; feed/query/detail windows are bounded. No adjacent-video prefetch or permanent cache was added.

## Measurement protocol

- Actual Android frame clock and Window FrameMetrics, not Compose's synthetic test clock. Native touch injection drives the production Sakugapp composables in a Debug-only fixture host.
- 300 captured response records retain IDs/tags/scores. Deterministic generated 320×180 thumbnails and an MP4 are seeded into owned offline files. Synthetic media URLs are verified to resolve locally before rendering; this is not a live-network benchmark or real video-format distribution.
- 2/4/6 columns on phone; also 8/12 on tablet. Automatic previews off/on. Twenty main-tab switches, detail return, rendered transition captures, process memory and ExoPlayer Init/Release logs.
- Bounded bidirectional swipes and direct accessibility snapshots avoid the idle waits UiAutomator normally inserts between queries. Setup uses bounded accessibility scrolling with previews disabled, outside the measured window; the measured swipes use native touch injection. This avoids interpreting intentional top pull-to-refresh as duplicate loading. Refresh revisions and repository request logs are recorded separately; navigation requests must not increase.
- Both Debug APK/test-APK pairs are built before emulator startup. Native AndroidJUnitRunner execution then runs paired baseline/current/current/baseline trials on the same emulator and harness, fresh disposable fixture data per trial, and warmed UI. The runner gate requires an actual JUnit success summary and normal instrumentation completion; a zero adb exit code alone is insufficient. Only the dedicated workflow emulator's test-app data is cleared.
- API 35 software rendering uses 1280×900 at 160 dpi, preserving the Pixel C logical tablet layout while reducing SwiftShader pixel cost. This is a controlled test viewport, not a change to application image quality. API 28 uses its normal Nexus S viewport.
- CSV frame samples, JSON summaries, native captures and atrace system traces are retained as Actions artifacts. The 8 MiB-per-CPU atrace ring retained tail data but no complete phase brackets in these runs. CSV/JSON phase windows remain complete; retained traces are not treated as phase-aligned measurements.

`over_budget_frames` means FrameMetrics TOTAL_DURATION exceeds the display refresh interval. It is not SurfaceFlinger FrameTimeline's official jank classification. Component durations and whole-Recomposer change counts are recorded; individual visible-card recomposition counts and physical-device GPU timings were not measured. Scheduler-derived main-thread off-CPU estimates are limited to retained trace tails, without per-phase or disk-I/O attribution.

The first unnormalized tablet measurements were dominated by SwiftShader swap/render waits. The first exporter also failed to copy wildcard paths; export now copies and validates each file explicitly. A baseline whole-run request assertion initially counted legitimate pull-to-refresh as a navigation failure; the final harness separates those actions. Later setup attempts encountered recycled accessibility objects and failed tablet fling resets. Direct snapshots and bounded accessibility page actions replace those preconditions. Test teardown exports partial JSON, screenshots and traces even when a precondition fails. These failed/incomplete scenarios are excluded from paired results. These initial runs establish diagnostic leads, not a final before/after comparison.

## Verification and results

Initial application baseline: localization, 192 unit tests, Lint and assembleDebug passed before application edits. New policy tests cover delayed/cancelled automatic starts, manual selection/stop, disabling auto, inactive/background pages and unchanged duration identities. Eight new device checks cover one navigation action, unchanged grid bounds, saved drafts/cache/scroll, outgoing transition content, rapid interrupted transitions, Android Parcel restoration, provider-disposal order and preview-player pause/background reuse. Existing player, cache, search, community, quiz, discovery and backup regressions remain enabled.

Final paired measurements and device outcomes are recorded below with their application commits and Actions runs. No unmeasured improvement percentage is claimed. If an optimization regresses the controlled cases, it must be revised or removed before delivery.

Physical-device follow-up: real thumbnail formats and network variability, vendor codecs/audio focus, variable refresh rates, system reduced-motion configurations, long-session memory trends, authenticated website operations and OEM gesture behavior. Short Debug/AVD runs cannot establish universal smoothness or eliminate all crash risks.

## Final verification (b2346b6 application sources)

- [Paired measurements + device regressions, 42496524866](https://github.com/Milenal82/sakugapp/actions/runs/42496524866): all jobs passed. Both devices completed baseline/current/current/baseline trials and 8 new + 42 existing device tests.
- [Automatic Debug validation, 42497405174](https://github.com/Milenal82/sakugapp/actions/runs/42497405174): all jobs passed at `896e42b`. Application/test sources are identical to measured `b2346b6`; subsequent changes concern workflow execution and documentation only.
- 201 unit tests passed; CI Lint: 0 errors, 74 warnings (local Lint: 0 errors, 55 warnings). Localization: 630 resources, 8 languages. Debug build/AVD installation passed. No Lint checks were disabled to obtain a pass.
- [Debug APK artifact](https://github.com/Milenal82/sakugapp/actions/runs/42497405174/artifacts/13624615361). Raw measurement artifacts: [phone](https://github.com/Milenal82/sakugapp/actions/runs/42496524866/artifacts/13623967997), [tablet](https://github.com/Milenal82/sakugapp/actions/runs/42496524866/artifacts/13625401344). [Numeric summary](performance-results.json) retains frame counts, requests, memory and transition captures.
- Unit/Lint/Debug and both device regression suites run on pushes. Workflow dispatch additionally builds the pinned baseline and runs paired profiling. All tasks are Debug-only. Normalized tablet validation preserves the original logical 1280×900 dp window; the paired run also passed regressions at the stock Pixel C pixel size after restoring its density.

### API 28 phone

| Columns / preview | P50 ms before → after | P95 ms before → after | Over budget before → after | New players before → after |
| --- | --- | --- | --- | --- | --- |
| 2 / off | 22.88 → 21.72 | 33.54 → 33.10 | 61.90% → 56.19% | 0 → 0 |
| 2 / on | 23.63 → 10.85 | 84.59 → 23.34 | 78.30% → 7.03% | 23.5 → 2 |
| 4 / off | 22.75 → 18.68 | 25.07 → 23.90 | 68.33% → 50.85% | 0 → 0 |
| 4 / on | 23.46 → 12.61 | 71.88 → 23.82 | 77.33% → 18.67% | 26 → 2 |
| 6 / off | 23.11 → 22.87 | 35.47 → 32.54 | 88.66% → 76.68% | 0 → 0 |
| 6 / on | 25.31 → 23.03 | 71.17 → 34.73 | 83.86% → 76.89% | 26 → 3 |

### API 35 software-rendered tablet

| Columns / preview | P50 ms before → after | P95 ms before → after | Over budget before → after | New players before → after |
| --- | --- | --- | --- | --- | --- |
| 2 / off | 91.40 → 95.46 | 146.16 → 144.71 | 100.00% → 100.00% | 0 → 0 |
| 2 / on | 107.82 → 84.36 | 303.07 → 144.45 | 99.60% → 100.00% | 22.5 → 2 |
| 4 / off | 118.23 → 112.25 | 160.16 → 158.39 | 100.00% → 100.00% | 0 → 0 |
| 4 / on | 127.62 → 110.14 | 282.54 → 157.42 | 99.77% → 99.90% | 24.5 → 2 |
| 6 / off | 118.51 → 111.49 | 163.67 → 154.85 | 100.00% → 100.00% | 0 → 0 |
| 6 / on | 117.35 → 108.36 | 244.81 → 162.70 | 99.71% → 99.81% | 19 → 4 |

The automatic-preview cases show the clearest consistent gain. On phone, two-column creation counts fell from a median 23.5 to 2 during 24 swipes; mean total observed creations were 78 → 8 including detail. Mean end PSS was about 202 → 168 MiB, with no observed unreleased IDs in any completed trial. These short, unforced-GC samples are not a leak guarantee. Every 20-tab-switch scenario added zero repository requests; these are Fake Repository counts, not live-site HTTP measurements. No FrameMetrics callback samples were lost.

At two-column phone idle-preview-off, P95 was almost unchanged (33.54 → 33.10 ms), so no general gain is claimed. Navigation P95 was 21.39 → 18.13 ms on phone and 222.79 → 229.48 ms on the software tablet. Navigation is not advertised as universally faster. All four phone transitions had rendered intermediate pixels in both current trials at captures around 146–180 ms. Baseline fades were also observed. The single-operation child-stack navigation test, 80 ms outgoing/incoming-content assertions and rapid reversal tests passed. Slow tablet screenshot captures ran roughly 196–379 ms and did not establish intermediate rendered frames; physical-device animation appearance remains a follow-up.

The phone grid's native visible bottom moved 692 → 800 px during baseline chrome hiding, but stayed at 692 px after the fix. The independent semantics-bounds test also passed in both directions. Reserving the bar space is deliberate; detail/fullscreen reserve none.

### High-density and trace limitations

8/12-column grids were exercised and their functional spacing/layout regressions passed. Some tablet high-density gestures reached the top and intentionally invoked pull-to-refresh: 8-off baseline requests [4,4], current [0,7]; 8-on current [2,0]; 12-off baseline [3,2], current [10,6]. These phases are excluded from pure scrolling improvement claims. Twelve-column on-preview itself issued no requests, but prior refreshes alter warming, and its P50 was 133.78 → 137.16 ms despite a lower P95. It is exploratory, not evidence of uniformly faster dense browsing. The raw data is retained rather than marked clean. Tablet over-budget ratios remained near 100% under SwiftShader.

The trace ring overwrote complete phase brackets. Retained trace tails show long main-thread/RenderThread wall spans and graphics waits, but their unequal windows must not be compared as per-phase improvements. Scheduler intersection reports observed non-runnable/runnable off-CPU time within outermost doFrame spans; sleeping includes normal graphics/synchronization waits and does not prove application disk blocking. Individual-card recompositions, a matched per-phase blocked-time attribution and physical GPU timings remain unmeasured.

For example, the first current phone trace tail contains 880 outermost doFrame spans: P95 wall 13.97 ms, observed non-runnable off-CPU 8.68 ms and runnable off-CPU 3.25 ms. The corresponding tablet tail has 78 spans: wall 166.02 ms, non-runnable 114.49 ms and runnable 37.59 ms; RenderThread DrawFrames P95 is about 270.89 ms. These independent quantiles do not add together, and tails are not matched windows. They demonstrate remaining graphics/scheduling pressure, not a proven application disk-I/O cause. The interval parser is checked against a 100 ms synthetic outer frame with a nested resync span and 30 ms sleep; it counts one frame and estimates 70 ms running. [Raw summaries and trace analysis](https://github.com/Milenal82/sakugapp/blob/5d7b08fcd45bc072dde9fd328e9bf6f8ee2bcf36/performance-report.json) retain the exact inputs and definitions.

Package `com.sakugapp`, version `1.3.0` / code `16`, preferences, data schemas, signing configuration and published tags/releases are unchanged. No Release task or signing secret was used. Only the development branch is pushed.

## Iteration 1 (01774fb)

[Run 42493326362](https://github.com/Milenal82/sakugapp/actions/runs/42493326362). API 28, 480×800 Nexus S, the same AVD in ABBA order. Values below are medians of the two per-trial percentiles/ratios, not percentiles pooled across trials. All four performance scenarios completed, with no navigation requests and no unreleased player IDs at the end. These are Debug fixture results, not physical-device frame rates.

| Columns / automatic preview | P50 ms before → after | P95 ms before → after | Over refresh budget before → after | New players in phase before → after |
| --- | --- | --- | --- | --- |
| 2 / off | 18.05 → 20.06 | 36.78 → 38.40 | 51.42% → 58.24% | 0 → 0 |
| 2 / on | 22.33 → 9.88 | 81.72 → 18.09 | 73.14% → 5.21% | 22.5 → 2 |
| 4 / off | 17.93 → 13.02 | 30.45 → 22.59 | 48.50% → 30.53% | 0 → 0 |
| 4 / on | 22.12 → 11.52 | 84.26 → 21.30 | 68.89% → 5.51% | 26 → 2 |
| 6 / off | 21.84 → 21.69 | 38.37 → 25.64 | 81.98% → 72.46% | 0 → 0 |
| 6 / on | 22.80 → 21.73 | 70.76 → 26.11 | 84.64% → 70.33% | 26 → 3 |

Two-column preview-off does not establish a gain: baseline P95 ranged 34.92–38.65 ms and current 38.28–38.52 ms. Layout P95 fell from 0.37 to 0.27 ms while swap P50 rose from 9.48 to 11.11 ms. Twenty-switch P95 was 36.38 → 38.41 ms; this is not evidence of faster navigation. On-preview decoder churn is the clearest measured improvement. Whole-Recomposer change counts in the two-column on-preview phase were 136 → 41.5; these are not individual-card counts. Mean final PSS was about 207 → 168 MiB, with 76 → 8 total observed player creations (including detail). Short samples with no forced GC cannot prove absence of leaks.

The 42 existing phone regressions passed. Six of seven new checks passed; the search-draft retention check failed. The follow-up saves page state before child providers unregister, uses stable field names across entry IDs, and adds an actual composition-disposal regression. Input, ordinary-grid cache/scroll, one-operation navigation, visible transition content, rapid reversal, Parcel restoration and player background reuse remain checked. Native screenshots taken at 65–119 ms showed the old page/press feedback and were inconclusive about rendered transitions; later sampling is required. Existing NavHost fade transitions were present in the baseline and are not described as universally missing.

## Files

- `.github/workflows/performance-debug.yml`
- `CHANGELOG.md`
- `README.md`
- `app/src/androidTest/java/com/sakugapp/NavigationPerformanceDeviceTest.kt`
- `app/src/androidTest/java/com/sakugapp/PerformanceScenarioDeviceTest.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/com/sakugapp/PerformanceHostActivity.kt`
- `app/src/main/java/com/sakugapp/MainActivity.kt`
- `app/src/main/java/com/sakugapp/ui/AppTheme.kt`
- `app/src/main/java/com/sakugapp/ui/FeedPresentation.kt`
- `app/src/main/java/com/sakugapp/ui/HomeScreens.kt`
- `app/src/main/java/com/sakugapp/ui/MainNavigation.kt`
- `app/src/main/java/com/sakugapp/ui/MainPageState.kt`
- `app/src/main/java/com/sakugapp/ui/PostCaption.kt`
- `app/src/main/java/com/sakugapp/ui/PreviewPolicy.kt`
- `app/src/main/java/com/sakugapp/ui/VideoPanel.kt`
- `app/src/test/java/com/sakugapp/MainPageStateTest.kt`
- `app/src/test/java/com/sakugapp/PerformancePolicyTest.kt`
- `docs/performance-navigation.md`
- `docs/performance-results.json`
- `scripts/build-performance-baseline.sh`
- `scripts/run-device-instrumentation.sh`
- `scripts/run-performance-profile.sh`

Diagnostic-only auxiliary branch: `ci/player-tools-audit`, `.github/workflows/read-player.yml` reads Debug reports and writes summaries/screenshots to `evidence/player-diagnostics`. It is not an application dependency.
