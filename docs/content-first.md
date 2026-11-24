# Content-first UI / 内容优先 UI

Branch: [`ui/content-first-refresh`](https://github.com/Milenal82/sakugapp/tree/ui/content-first-refresh). Baseline: latest `origin/main` at task start, `e0a9e387471b1dde73330f11ff112dfaa5cffa98`. The installed name, `com.sakugapp`, version `1.3.2-dev` / code 18, signing configuration and data formats are unchanged. This is a Debug-only review branch, not a release.

## Changes

| Area | Behavior | Main files |
| --- | --- | --- |
| Posts bottom space | Slide/fade out in 170 ms; release 72 dp after the transition settles. Preserve the system navigation inset. Reserve space once before the 190 ms entrance. No per-frame interpolation of grid height, list reset or player/cache recreation. | `MainNavigation.kt` |
| Explicit Posts tap | Return to the default, empty-query feed; refresh page 1 and scroll to the top. Coalesce in-flight first-page refreshes. Retain old default posts and a retry action on failure. Obsolete search/append responses cannot replace the latest state. | `MainActivity.kt`, `FeedViewModel.kt` |
| Search-home gesture | Normal scrolling for long content; unconsumed edge drag moves the content layer by at most 24 dp, then returns in 180 ms. No pointer interception, refresh/navigation trigger or coroutine per drag delta. Disable displacement while the IME is visible; animation core uses the system duration scale. | `SearchElasticScroll.kt`, `HomeScreens.kt` |
| Frame and Explore tools | Frame strip: 42 dp visual surface, 32 dp step visuals, unchanged 48 dp touch-slot height and adaptive horizontal widths. All six operations, counter and three right-side actions remain in one row. Explore uses local compact AssistChips and preserves all 14 queries/destinations in three groups. | `FrameControlBar.kt`, `VideoPanel.kt`, `ExploreScreens.kt` |
| Profile | Six single-row destinations on narrow windows. At available width ≥600 dp, a 35/65 profile/list layout, centered within 960 dp. Avatar/nickname operations, draft dialog, scroll, login state and existing destinations are retained. No migration. | `HomeScreens.kt` |
| Icons | Grid Posts, outline profile, movie works, grouped artists, compass Explore, gear settings and chevrons. Existing folder/star/search icons retained. New geometric vectors are project originals under GPL-3.0-only, with no dependency added. | `AppNavigation.kt`, `AppMenus.kt`, `res/drawable/ic_*.xml` |

Only **explicit bottom-tab taps** request the latest feed. Programmatic search navigation still shows its result; a detail return restores the same query/list/scroll, and menu Posts retains its ordinary-browse intent. Search history is not cleared.

On a 320 dp one-row strip, six 44 dp-wide step targets, three 44 dp actions and a counter cannot fit together. This change preserves the existing adaptive horizontal widths and keeps 48 dp vertical hit areas; it does **not** claim 44×44 dp targets for every step on the narrowest phones. No operation is hidden or replaced by approximate seeking.

## Checks and evidence

Baseline localization, unit tests, Lint and Debug build passed before edits. Local final validation and the exact CI commit/results are recorded with the delivered build. The added unit tests cover explicit refresh/coalescing/default restoration, actual loopback HTTP query/page parameters, failure retention and elastic-drag bounds/reversal.

Local checks: 238 unit tests passed with no failures/skips; localization verified all 633 resources in eight UI languages; Lint reports zero errors and 58 warnings; Debug application and instrumentation APKs compiled. One earlier Lint attempt failed inside the Kotlin analysis engine; compiling the test package first and rerunning with stable inputs passed without suppressions. No local KVM device is available; emulator outcomes come from the isolated CI run.

`ContentFirstDeviceTest` checks the released band contains actual Post cards in 2/4/6 columns on phone and additionally 8/12 on tablet, in light/dark modes. It records shown/hidden viewport rectangles and screenshots. Other cases cover refresh intents/detail return, both short-content drag directions, IME behavior, profile width/draft/state changes, all step/topic callbacks and unchanged player/page-cache identity across settled chrome changes. Existing navigation, media, search, profile, library, migration, backup, community and download Debug tests remain enabled.

Test screens use captured public response metadata and clearly identified generated local posters/media. They are native emulator captures, not screenshots of live account data. Window-width checks are distinct from physical foldable/split-screen validation. Existing strict-frame and A/B tests exercise the playback algorithms; styling does not change them.

```sh
python3 scripts/check-localization.py
./gradlew --no-daemon assembleDebugAndroidTest
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Compile the instrumentation package before running Lint to keep its generated Kotlin inputs stable. No checks are disabled. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

### CI isolation

[`content-first-debug.yml`](../.github/workflows/content-first-debug.yml) runs only on this branch or manual dispatch. It builds Debug, runs API 28 phone/API 35 tablet checks, and uploads **SAKUGAPP-ContentFirst-Debug**. It never calls Release tasks, signing secrets or release/tag scripts. No PR is created because the existing general PR workflow includes Release work. App changes are not pushed or merged into main.

Bounded reports and original captures are kept separately on [`evidence/content-first-refresh`](https://github.com/Milenal82/sakugapp/tree/evidence/content-first-refresh), with the source SHA, run/artifact URLs and actual failures. They do not alter application sources or main.

### Measurement scope

The paired check builds the pinned main baseline and current Debug/test APKs on the same runner, then measures baseline/current/current/baseline on the same disposable API 35 tablet emulator. `PostScrollDeviceTest` uses the actual Android frame clock and Window FrameMetrics for 24 up/down swipes at 2/4/6/8/12 columns. Both versions use the same captured 300-post fixture and harness. Thumbnails are absent in both trials to isolate layout; this is **not** a live-image/network or whole-navigation performance benchmark. Global chrome resizing/player reuse has a separate functional check. Fixture data is reset only on the owned CI emulator.

Raw JSON retains frame counts, P50/P95/mean, layout/draw components and long-frame counts. These software-rendered Debug timings do not establish physical-device FPS, an improvement percentage, long-term memory stability or universal smoothness. No such claims are made.

## Remaining device checks

Physical phone/tablet gesture behavior, reduced-motion settings, real network/image load, vendor decoder behavior, long browsing sessions and actual OS split-screen/foldable transitions still require manual testing. The existing phone orientation policy is unchanged. No formal upgrade installation is performed because this task does not build or use the official signature.
