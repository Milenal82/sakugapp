# Menu and quiz work pool supplement

Base: `33f41b0df6a8537447d372acc738de5a50d2f24c` on `dev/compact-explore-quiz`, matching the fetched remote. Existing search/profile/discovery, frame/clip, favorites and download work is retained. Package, version 1.2.2/code 15 and signing configuration are unchanged. Only the existing branch-scoped Debug-only workflow runs; no Release task, key, tag, draft or main merge.

## Navigation

The global overflow menu contains exactly six icon rows in this order: Posts, Search, My account, Folders, Settings, Licenses and terms. Its body typography is the scaled 14 sp Material bodyMedium; the popup is bounded to 280 dp and rows retain a 48 dp minimum touch area. A divider separates licenses. Posts explicitly restores ordinary browse data and its grid position; Search navigates to search_home. Account targets the existing website login, and Folders targets the local library, not website Favorites. Main destinations use the existing save/restore/singleTop navigation and trim restored child pages back to the requested main root; existing child destinations pop back to their existing entry rather than stacking copies. The unchanged AboutDialog retains its GPL and third-party notices. DropdownMenu owns native dismissal/back handling.

History remains on Search home, Blog/Forum on Profile, language on Settings, and tag favorites on the local library. No page or stored data was removed.

## Pool UI and persistence

Quiz home shows question count, a work-scope entry, the current scope/selection count and Start. The independent quiz_pool page auto-saves all/custom mode and selections, using the existing quiz_count/quiz_custom/quiz_pool DataStore keys. The limit rises non-destructively from 20 to 30. Count, mode, add, remove and clear edits are atomic and do not overwrite other fields. Duplicates keep insertion order and do not consume capacity. A full list rejects another addition with an explicit message instead of silently truncating it. Clearing requires confirmation and only removes the quiz pool key.

Saved selections render without fetching their posts. Autocomplete starts after two characters without modifying IME composing text, debounces and cancels stale requests, and reuses TagSuggestionRepository (bounded to eight cached API candidates). Only explicitly classified Copyright candidates are shown, with a count when present. Artist/General/Meta/Unknown results have no Add entry; the selected state has a check mark and disabled Add action. No translation table or manual unchecked-tag bypass is introduced. Starting a round separately revalidates saved Copyright classifications and actual eligible video posts. All mode ignores the saved pool while preserving it for later reuse.

The manager uses one LazyColumn with IME padding and a 700 dp maximum width. Search candidates and selected works have distinct sections. Empty custom scope disables Start with the requested message. Insufficient-question errors offer retry, pool management and question-count adjustment; they never silently repeat posts or broaden the selected scope. The existing quiz player/scoring is retained.

## Sampling

A shuffled rotation visits each selected work once per cycle. Each work owns at most 100 cached Post metadata records; one cached work cannot monopolize later questions. Every custom candidate must contain the queried work as a known Copyright and pass the existing single-Artist/video/used-ID checks. Shared posts count only once in the round. Queries contain one selected work, never an AND of all works. Videos are still loaded only for the current question.

All-works requests remain capped at 12. Custom requests are capped at max(12, 2 × selected work count), at most 60; the larger finite bound gives all 30 works a chance. Two successful pages with no eligible unseen post retire that work, while other works continue. Network/421/429 failures propagate as errors, preserve the failed target for retry and do not mark a work as empty. The existing throttle, cancellation checks and session mutex remain. Limits describe a bounded sample, not an exhaustive proof that a work has no eligible clips.

## Checks and limits

Fixed-fixture/Fake Repository JVM tests cover shuffled-cycle balance, cache fairness, one-work queries, cross-work ID deduplication, outside-scope rejection, thirty-work request bounds, network failure/retry and ordered capacity handling. Scoped API 28 phone/API 35 tablet instrumentation covers six menu items/order/destinations/back, normal-feed cache/scroll restoration, legal notices, pool type filtering, consecutive additions, duplicate prevention, persistence, mode retention, removal, confirmed clearing, capacity and amber/font layouts, alongside the existing backup/frame/clip/quiz cases. Matching CI artifacts contain the actual reports and original fixture screenshots.

Live website access was previously blocked at the cloud proxy (403); no proxy bypass or real-account claim is made. Autocomplete uses the existing verified API/parser and controlled tests, not a fabricated artist/work database. Physical-device edge gestures, all keyboards and native-speaker translation review still need real-device review. Eight UI language resources and placeholders are checked. No schema migration or destructive operation touches folders, history, website accounts, Sources or original tags.
