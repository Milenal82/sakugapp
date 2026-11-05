# Page video cache and download folders

Development update based on v1.2.2. Package, version, signing identity, Room schema and favorite backup format are unchanged. No Release is published by this update.

## Temporary video data

Each video-bearing navigation page owns one lazy Media3 `SimpleCache` in `cacheDir/page-video/<process>/<page>`, with a 200 MiB disk LRU limit. Scroll-driven preview destruction and player/view replacement retain that page's cache. Fullscreen uses the same player/session. Configuration recreation retains the navigation ViewModel/session. Actual navigation disposal closes the session; registered readers are cancelled and reader/player/decoder leases delay cache release and asynchronous directory deletion until safe. Next startup removes previous-process directories and the obsolete separate frame cache.

Playback and exact frame analysis use the same URL keys and HTTP byte ranges. `MediaMetadataRetriever` and `MediaExtractor` read through `MediaDataSource`, rather than downloading a second full video. Opening frame analysis can request additional ranges needed to index/decode; it does not promise zero new traffic. MP4/WebM decoding support remains device-dependent. LRU eviction may require fetching an evicted range again; videos larger than the limit cannot be held entirely. Cache failures fall back to upstream reads where possible; HTTP failures are not bypassed. No cache setting, background prefetch, or permanent offline cache is added.

## Download destinations

Data settings hold independent persisted SAF tree grants for single/batch video downloads. Defaults are public `Movies/Sakugapp/` and `Movies/Sakugapp/Batch/`: Android 10+ uses MediaStore pending entries; Android 8–9 uses MediaStore with the narrowly scoped legacy write permission. Custom folders use SAF on all supported versions. No all-files access permission is requested. SD cards work only where the system document provider grants access.

Completed URI receipts are stored separately in `download_destinations_v1`, not in favorites JSON or the old private media index. Changing/resetting a folder does not move or delete old files or revoke grants needed to play old downloads. An existing valid receipt avoids downloading that video again. Failed/cancelled transfers delete only their own incomplete target; successful earlier items survive. The existing Artist/Source/episode/Post ID naming function and collision suffixes are shared by both modes. Batch limits remain 20 items, 200 MiB per file and 1 GiB of recorded batch downloads.

Old `files/favorites-media` files/index remain readable. Export copies all recognized old private video files to the selected batch destination, retaining original names and originals; receipts avoid repeating an export. Old hashed files have no invented artist/source metadata. The existing confirmed private-media deletion operation remains separate and does not delete exported public files.

## Verification scope

Local checks run localization, existing JVM tests, Debug/Release Lint and compilation. Local Release uses `-PreleaseUnsignedCheck=true`; it is not a signed/installable release. Device checks are in [page-media.yml](../.github/workflows/page-media.yml), with API 28/35 emulator results on the [evidence branch](https://github.com/Milenal82/sakugapp/blob/evidence/page-media/report.json).

Controlled TLS fixtures test range hits, three actual ExoPlayer loops, repeated seeks, strict frame decoding, fullscreen/view replacement, page exit/reentry and 15 sequential page sessions. Native SAF tests select separate directories, verify durable grants after Activity recreation, compare saved bytes/names, detect duplicates, cancel a slow transfer, inject ENOSPC, revoke a grant, export without network/deleting originals, and query public defaults in MediaStore. The prior private-download suite remains included. Heap sampling is a bounded Java-heap check, not proof of leak freedom. Physical SD cards, provider-specific failures, process-kill/reboot permission persistence and hours-long stress are not claimed as verified by these tests.
