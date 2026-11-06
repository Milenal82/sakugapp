# Search, local profile and discovery

Branch: `dev/search-profile-discovery`. Latest main checked: `e308e10f2464d8a338d7828c42dca23e058c3312`, an ancestor of the previous player/download work. That work is retained. Version 1.2.2/code 15, package and signing configuration are unchanged. This branch builds Debug APKs only; no release/tag/merge is requested.

Navigation starts at `search_home`. Search and Profile are independent home pages; Posts keeps both ordinary and searched feeds, separate grid positions ; the bottom Posts tab restores ordinary browsing and clearing the search restores it too. Entering Posts never calls restoreBrowse implicitly. Children keep their existing back stack, including library/history → detail and discovery → Posts → Back. Hidden lists disable previews. The bottom bar uses consumed user scroll with 64/28 dp thresholds and returns on tab selection/top; detail and fullscreen have no bottom bar.

Local profile metadata is in a separate DataStore; image data is a private JPEG with a new UUID. Photo Picker falls back to Android document selection on older devices. Input is limited to 20 MiB; Coil performs sampled decode/EXIF orientation, and saved output is at most 512 pixels. Corrupt/unsupported images preserve the previous avatar. Names are trimmed, limited to 20 Unicode code points, and reject control/bidi format characters. Website identity/cookies and favorites backups are separate and unchanged. Profiles do not survive uninstall by guarantee and are not uploaded.

## Read-only data and sampling

Discovery reuses Post, SakugaRepository, its classification summary and PublicReadThrottle. It does not request account cookies. Tag lookup uses the already supported **one bounded `tag/summary.json` response**, with local substring matching and at most 30 verified Artist/Copyright candidates, rather than unbounded pagination through tag.json. Counts unavailable in this catalog are not guessed. A failure to fetch/parse the catalog is an error, not an empty artist directory.

Posts use encoded `post.json?tags=…&limit=100&page=…`. Artists start with one page, continue one page at a time, capped at five pages/500 input records. Works initially scan up to three pages, continue in batches of three, capped at twelve pages/1200 records. IDs are deduplicated; duplicate full pages still consume the finite page budget. Eight ten-minute sample cache entries retain committed pages. Cancel/failure never commits a failed page or invents an exhausted result. Only an empty successful page confirms server exhaustion; a short page alone does not prove it. Parsing/grouping/statistics execute off the UI thread. Missing Score stays missing, zero remains valid; average uses only valid scores. Upload years use the verified API Unix **seconds**, in UTC, and are not broadcast or employment years. Co-occurrence counts each tag once per distinct sampled Post; percentages use the sample count.

Source is free text. The independent parser recognizes #1/#01/#001, Episode/ep variants, numbered OP/ED/OVA, Movie/Film and PV/Trailer/Teaser. Conflicting episode markers are unclassified. URLs are removed before matching; year-only/resolution-only/unmarked numbers are not episodes. Numeric episode markers are conservatively bounded to 1–1899. Special group numbers are retained and groups sort numerically. Missing source and unclassified source remain separate.

**Specific episode lookup is limited to the loaded work sample**, with further bounded work-tag pages available on the episode screen. It does not make unverified `source:` queries or claim comprehensive episode coverage. Locally, all four attempted site probes on 2026-11-06 (`tag.json` Artist/Copyright, ordinary work posts, and a source-filtered query) failed at the environment proxy: `Tunnel connection failed: 403 Forbidden`. This is an environment failure, not evidence about the site's source-query semantics. Existing legally acquired response fixtures/provenance remain in app/src/test/resources/live. A network integration check in an environment with permitted site access is required before enabling a server source-search candidate strategy.

## Reference review and differences

Read actual API/calculation/parser/page code, not only READMEs:

- Sakuga Enhancer `b20eb4fa697212fd6ad86307e1d90cf280440317`: web-src/23-stats-tab.js and 24-shows-tab.js.
- Sakuga Enhancer App `e0b4e1a67a3e9d97c5b7cd9a74192e55aae55f24`: src/api/sakugabooru.ts, ArtistStatsView, ShowSearchScreen, ShowDetailScreen and EpisodeResultsScreen. MIT.
- sakugabooru-episode-mad `ec7100f712fff20c7aa16951e744534f4c3cf97a`: README, LICENSE, process.py grouping and download pipeline. MIT.

No source/runtime copied. The references treat absent Scores as zero, merge OP/ED numbering, or parse arbitrary URL hashes/years. This implementation deliberately avoids these behaviors. The reference's all-tag crawl (up to 150 pages) and unverified related/source endpoints were not adopted. The app does not concatenate videos or present sampled artists as official staff credits.

## Checks and limitations

Run localization validation, JVM unit tests, Lint and assembleDebug. CI adds API 28 phone/API 35 tablet navigation, profile-image, backup and existing native frame tests. Test fixtures/fake repositories are isolated from the production data path; UI never uses them as site content. See Actions and the `evidence/discovery` branch for exact source SHA/results. New translations have resource/placeholder checks, not native-speaker approval. Real authenticated actions, physical-device gestures, process-death restoration of in-memory samples and exhaustive site-wide episode lookup are not claimed.
