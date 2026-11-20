# Sakugabot detail names and Posts scrolling

Development version: 1.3.1-dev (17). No release, tag, package, official certificate, library database or backup-format changes.

## Public API verification

Verified on 2026-11-20 (Asia/Shanghai), using six sequential public, read-only requests from GitHub Actions. This workspace's network proxy rejected the HTTPS tunnel with `403 Forbidden`; that is not a Sakugabot HTTP response. The successful Actions responses are retained on the separate [`evidence/sakugabot-api`](https://github.com/Milenal82/sakugapp/tree/evidence/sakugabot-api) branch. No login, cookie, editing endpoint or access-control bypass was used.

Endpoint: `GET https://sakugabot.pw/api/tags/{URL-encoded original tag}/`. Each tag is one path segment, including any reserved characters; the trailing slash is retained.

| Original tag | Result | Relevant fields |
| --- | --- | --- |
| `mob_psycho_100` | 200, type 3 | `detail.name_zh` = 灵能百分百; English alias = Mob Psycho 100 |
| `yutaka_nakamura` | 200, type 1 | `name_zh` = 中村丰; `name_main` = 中村豊 |
| `yoh_yoshinari` | 200, type 1 | `name_zh`/`name_main` = 吉成曜; matching Chinese Wikipedia title |
| `hiroyuki_imaishi` | 200, type 1 | `name_zh` = 今石洋之 |
| `mokmok` | 200, type 1 | Empty `detail`; original remains visible |
| `yoshinari_you` | 404 | Original remains visible |

`detail` is an array: each item has `attribute.code` and a string `value`. The table's dotted names abbreviate this structure. Sanitized fixtures keep only tag name, type and relevant naming fields; biographies, contributor metadata and unrelated attributes are excluded.

## Display and fallback

The setting is **off by default**, saved under a new DataStore key (`sakugabotDetailTranslations`) and takes effect without restarting. The removed legacy translation flag cannot opt existing users in. The setting and provider explanation are present in all eight UI languages; resource parity is checked, without claiming native-speaker review.

- **Work / Copyright and Artist:** only tags already classified by Sakugabooru as Copyright or Artist are queried. Response name and type must match. Prefer a nonempty `detail` value with code `name_zh`. A `name_main` fallback is accepted only when the same record's `wiki_zh` URL has a matching Chinese Wikipedia title and the name contains Han characters. Merely containing Han, a Japanese main name or the top-level `main_name` is insufficient. This deliberately conservative rule can leave untranslated names that cannot be corroborated.
- **Source:** never sent to the tag endpoint. Only a whole-field, unique match to an in-post Copyright record's original name or verified naming aliases is replaced with that record's Chinese name. For example, `Mob Psycho 100` can map to 灵能百分百. URLs, `Mob Psycho 100 #12`, arbitrary source text and ambiguous matches stay unchanged; no guessed substring or episode replacements.
- Only post **details** display these names. Original tags remain visible and retain search, statistics, favorites, notes and wiki actions using the original tag. A translated Source also retains the original Source. Feed cards, search expressions, comments, API models and stored favorites are unchanged.
- Missing fields, wrong types/names, malformed JSON, oversized responses, unavailable names, network errors and rate limits fall back silently to the original. Translation never blocks post loading.

## Requests and cache

An independent, unauthenticated OkHttp client uses GET only, no redirects/automatic retries, 4 s connect, 6 s read and 8 s total timeouts. Responses are limited to 512 KiB. Only visible details request names; stopping/leaving the page or disabling the setting cancels outstanding calls.

One application-ViewModel cache holds at most 256 tag/category records, with LRU eviction: names expire after 24 h, missing/malformed records after 1 h and transport/HTTP errors after 5 min. Concurrent lookups share cached results; requests are serialized with at least one second between starts. HTTP 421/429 applies a cross-tag cooldown using `Retry-After` (bounded to one hour; default one minute). Other transport/HTTP failures cool down for one minute. The cache is memory-only and clears when the app process ends; no periodic retry or feed-scroll lookup is added.

## Posts review

Existing stable Post-ID grid keys, guarded/cancellable pagination, ID deduplication, refresh generations, retained ordinary/search grids, bounded Coil memory/disk caches, constraint-sized thumbnails and delayed idle previews are preserved. Caption tag selection is already memoized and average-color work runs only on explicit request off the UI thread.

A second confirmed hot path enumerated visible grid IDs and allocated preview viewport values on scrolling even with automatic preview disabled and no manual preview. That path now avoids observing grid layout entirely. Active previews and the user-pause state still observe visibility/scrolling so offscreen stopping and later preview behavior are preserved.

Another redundant operation was media classification: each card called `Post.isVideo` more than once, allocating a set/lowercase value and sometimes parsing its URL each time. The getter now compares supported extensions without a temporary collection, and cards remember the result by media URL/extension. Score/duration updates cannot cause repeated media-URL parsing. No image quality, real content, aspect ratio, card spacing or paging behavior is reduced. This small allocation reduction does not establish a measured frame-rate improvement.

## Validation

- Baseline localization, Debug unit tests, Lint and APK build passed before edits.
- Targeted tests cover real-response priority, independently verified main-name fallback, raw fallback, exact Source mapping, URL encoding, concurrent cache hits, LRU/expiry, HTTP cooldown, cancellation, real DataStore recreation and isolation from legacy preferences.
- Existing feed tests cover duplicate load suppression, failed-page retry, refresh deduplication, obsolete responses and returning to retained search/browse state.
- `SakugabotDeviceTest` checks the actual settings switch and immediate detail updates/raw searches with a deterministic provider. A separate device check makes one public lookup using the real repository, renders the returned name or original fallback, then uses an explicitly offline DNS client to verify the original remains visible. Its JSON report distinguishes actual translated results from network/missing-name fallback; availability is not assumed. Existing `PostScrollDeviceTest` exercises a captured 300-post sample without live images. These do not prove real-device network/scroll performance.
- Local final command: `python3 scripts/check-localization.py` and `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: passed. 216 unit tests, no failures/errors; 632 resources across eight languages; Lint 0 errors and 55 pre-existing warnings. APK and instrumentation APK compiled successfully. The cancellation fixture pins the loopback IPv4 address to avoid the host’s unreachable IPv6 localhost route.
- Workflow syntax and 22 existing Python tooling tests passed. CI metadata validation now reads the actual development version instead of hardcoding the old release candidate; existing checks and signing/publication gates remain intact.
- No usable local emulator/KVM was available. CI includes native settings/detail, public API/offline and existing scrolling checks; actual run results will be reported separately. Physical-device fast scrolling, mobile-network transitions and prolonged use still require manual verification.
