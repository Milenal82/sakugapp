# Preview names, download names and player tools — 1.3.2-dev

This development update does not publish or move a Release/tag. Application ID, signing, folder schema, backup format and old downloads remain unchanged.

## Display and download rules

The existing Chinese-name switch remains off by default and persists under its existing DataStore key. The parser still requires an exact tag/category match and `detail[].attribute.code = name_zh`, or `name_main` corroborated by the same record's Chinese Wikipedia title. A Japanese or English `main_name` alone is not a Chinese translation.

| Area | Switch off | Switch on |
| --- | --- | --- |
| Posts preview Copyright | Original tag | One verified Chinese name, otherwise original |
| Posts preview Artist | Original tag | Original tag |
| Detail tags | Original tag | Existing Chinese-plus-original display |
| Detail Source | Original field | Existing exact, unambiguous verified Copyright-alias mapping; original retained |
| New download tag fields | Original tags | Verified cached Chinese names, otherwise original tags |

Preview labels preserve the original tag category/color and submit the original tag when searched. Detail presentation code and its original-tag actions remain in place. Other grids keep their existing label presentation.

The shared bounded cache has the existing 256-entry limit, expiry, 1 Hz request pacing, timeouts and silent failure/cooldown rules described in [Sakugabot details](sakugabot-details.md). Posts only looks up visible Copyright tags after 450 ms of idle. Scrolling, leaving or backgrounding cancels pending work. Downloads only inspect valid cached entries, never fetch missing translations or wait for an unrelated HTTP request. Thus a fresh process or missing translation can legitimately produce raw filenames.

New filenames are `#artist1#artist2#source#copyright1#copyright2#PostID.ext`. Tags keep their original order within each verified category and duplicates are removed. Source is the actual source field, not a tag or guessed translation; text like `Series #12` becomes `Series_12`. URLs reduce to their host to avoid exposing credentials, query values or signed paths. Missing/empty fields are omitted. The required example produces `#中村丰#21#gachiakuta#295427.mp4`.

Illegal path/control characters are removed, `#` inside a field becomes a separator-safe space, and the filename is limited to 180 UTF-8 bytes without splitting a Unicode code point. Long segments are shortened; ID and real MP4/WebM/MKV extension remain. Collisions add a sequence without truncating the ID. Single, batch and private legacy download paths share naming; clips additionally retain their A–B range/mode suffix. Existing URL-based receipts/legacy files resolve under their old identity, with no renaming or media changes.

## Frame tools and return behavior

The existing six arrows, frame/time counter and lock/fullscreen/more actions retain one row below the video. The frame area now has a light rounded surface, small localized title and button selection/ripple feedback. Actual decoding/estimated fallback, seek serialization, A–B positions, errors and fullscreen behavior are unchanged; this UI work does not improve frame precision.

Only a committed system back on a Posts result entry clears the result token and query, restores the cached ordinary feed/pagination/scroll position, and clears the search editor. A request-generation change rejects late search/refresh responses. An interrupted ordinary-page load resumes safely. A legacy filtered browse snapshot is reloaded with an empty query rather than presented as the homepage. Back from detail still returns to its source results; editor/IME and combined-search back handlers retain their existing precedence. Other routes keep their normal back behavior, including a subsequent back to the originating statistics/episode/tag page.

## Scrolling review

Reviewed Punyesh/sakugaEnhancerApp at `e0b4e1a67a3e9d97c5b7cd9a74192e55aae55f24`: MIT, Copyright 2026 Punyesh; actual `src/components/PostCard.tsx` and `src/screens/SearchScreen.tsx`, not only the README. Its memoized card, stable ID keys, single-flight page guard and ID deduplication support the same principles already used here. React Native window/batch sizes, cover-cropped images and per-card video hooks were not copied.

Confirmed redundant work in Sakugapp: offline receipt and legacy-file identity checks called `VideoDownloadPlan.from`, which traversed tags and sanitized Source just to obtain the media URL/extension. Those checks now use a separate media descriptor; one complete naming plan is built only when a new download is required. A deterministic test performs 1,000 descriptor reads and confirms zero tag accesses, then verifies real naming does access the tags.

Preview labels reuse the same map when unchanged, with a fast path for identical inputs. New name requests run only for a settled visible viewport; cards do not make HTTP requests or translate Artist labels. Existing stable Post-ID keys/content types, bounded Coil memory/disk cache, constraint-sized thumbnails, generation-guarded paging/refresh, deduplication and delayed single-preview player remain intact. Average-color work is still explicitly requested off the main thread. No image quality or content was reduced. These changes avoid measured redundant operations; no frame-rate percentage or universal smoothness claim is made.

## Checks and limits

- Baseline Debug unit tests, Lint and APK build passed before edits.
- Targeted unit tests cover preview/category fallback, unchanged cache-map identity, cache-only downloads/expiry/nonblocking reads, exact filename example, tag order, URL privacy, Unicode/collisions/format, default-feed restoration and obsolete responses. Existing detail translation/settings tests remain.
- Native tests cover preview-only names versus unchanged dual-name details, frame tile bounds/selection, detail back versus result back, restored default scroll/cache/editor, and the existing actual API-35 edge/predictive-back path. Existing real frame-step/player-lock tests are retained. CI runs them on a narrow API-28 phone and an API-35 tablet.
- Local final checks passed: `python3 scripts/check-localization.py` (633 resources, all eight UI languages), `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` (230 unit tests, no failures; Lint 0 errors/55 existing warnings; both APKs compiled). The 22 Python tooling tests, workflow syntax and whitespace checks also passed. An old positional frame-bar call in a device test was updated for the added optional state argument; no assertion was removed.
- CI results are recorded separately for the exact pushed commit. A local KVM emulator is unavailable; native checks require the CI emulator. Fixture rendering is not proof of live-network scrolling performance. Physical-device fast scrolling, hardware decoding and all keyboard/gesture implementations still require manual verification.
- The first CI run passed the new narrow-phone regression suite but caught an old offline-download assertion expecting an underscore before the ID. It now asserts the required hash separator while retaining byte, collision, legacy-reuse and quota checks. The API-35 dialog-back test also asserted removal immediately after a platform gesture; it now awaits native window idle and actual dialog removal without adding gestures or relaxing state/scroll assertions.
