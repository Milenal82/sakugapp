# Inline frame selection and A–B export

This development update does not change version 1.2.2 / code 15, signing, or existing Releases.

## Reference review

Read the existing VideoPanel, StrictFrames/FrameDecoder, FrameIndex, PlayerTools, VideoGestureHost, download storage/naming and their tests before editing. Inspected reference README, license and relevant implementation at:

| Project | Revision | Relevant implementation | License |
| --- | --- | --- | --- |
| ftLoic/Sakuga-Extended | 8229ac56e022d51343cdfd12b997e5ddc2edd4b9 | js/frames.js, settings, manifest/history | GPL-3.0 |
| Punyesh/sakugaEnhancer | b20eb4fa697212fd6ad86307e1d90cf280440317 | web-src/16-time-and-frame-bar.js, 17-trim-download-panel.js | MIT |
| Punyesh/sakugaEnhancerApp | e0b4e1a67a3e9d97c5b7cd9a74192e55aae55f24 | src/api/trim.ts, ViewerScreen, native trim override | MIT |

Enhancer pauses and seeks by an FPS-derived interval; its export UI distinguishes stream copy and re-encoding. This implementation uses those interaction ideas, independently written in Kotlin with Android Media3/platform media APIs. No reference JavaScript, React Native runtime, bundled FFmpeg binary or extension code is copied. Media3 Transformer/Muxer 1.6.1 is an official Apache-2.0 dependency, matching the existing player version.

## Frames and loop points

- The video has an independent aspect-ratio surface, followed by a permanent frame bar and an independently expanding, scrollable advanced panel. Expanding it does not change the surface's constraints or player instance. Fullscreen keeps the existing tap-to-reveal overlay.
- Six arrow targets (`<<< << <` and `> >> >>>`) move −3/−2/−1 and +1/+2/+3 frames in one row. Lock, Fullscreen and More stay at the right. Narrow windows compact the arrow widths and frame/time counter; the row remains 48 dp high. No step is hidden. See [current controls and locking](player-community-batch.md).
- Requests are serialized. Each immediately pauses; decoding stops the ExoPlayer pipeline to release its decoder, displays the decoded bitmap on the same gesture surface, and keeps playback paused. Resume prepares the existing player; no second ExoPlayer is created. Frame state is keyed by URL **and Post ID** and is discarded on external seeking/resume/media change.
- On Android 9+, MediaMetadataRetriever returns an actual frame index when supported (up to 3840×2160). A timestamp mapping is accepted only when unique, nonnegative sample PTS count equals the decoded frame count. Media3 extractor PTS are sorted into presentation order, including nonzero starts and VFR. This avoids Android 9 platform-extractor edit-list offsets that differ from the player; ordinary MP4 uses the parsed sample tables directly; other containers skip encoded payload with a random-access reader. Actual bitmap/index with unavailable PTS is marked as estimated time; unsupported decoders use reported finite FPS, then 24 FPS, explicitly marked as estimated seeking. These are not interchangeable precision claims.
- A/B is disabled while frame requests are pending. Once confirmed, A and B read that selected frame time, not the old asynchronous ExoPlayer position. Times use the existing millisecond loop model; sub-millisecond PTS round down. Clear/reset, interval slider and export all use the same pair. B must be later than A. Polling (10 ms while active), decoder latency and audio scheduling still limit ExoPlayer A–B looping; it is not absolutely frame-accurate playback.

## Clip export and directories

Confirmation shows the selected start, exclusive end and duration. One export per detail page runs at a time, survives panel collapse/fullscreen, and cancels on page disposal. Original source is staged read-only under a task UUID in app cache, reading already cached ranges first. Missing ranges are fetched normally; there is no remote clipping endpoint. Maximum source is 200 MiB. Staging checks available private space, reserves output headroom and checks cancellation; files are removed after success/failure/cancellation.

- **Re-encode:** Media3 Transformer explicitly forces both encoders, output H.264/AAC MP4. The video decoder reads a surrounding GOP window to retain B-frame references; a native timestamp gate forwards only the shared A/B interval and records its actual source PTS. Audio is re-encoded from that same interval, then the two tracks are muxed without another encoding pass. A reported video encoder, recorded source PTS, retained video sample count and duration (within a real frame interval, with a 100 ms timestamp/container tolerance) are required before saving. The first/last retained source PTS are reported. The output video duration is set to the selected interval, independently of the surrounding decode window, including VFR last-frame duration. Reported file duration also includes any AAC priming/padding. This mode is never labelled lossless. Hardware codec capability, VFR and source timestamps constrain achievable precision; unsupported/failed verification is an error rather than success.
- MP4/WebM/MKV inputs depend on the device decoder; precise output is H.264/AAC MP4. Unsupported codecs or failed boundary verification produce errors. There is no promise to support every codec in these containers.

Settings now has independent **single video**, **selected clip**, and **batch** SAF destinations. First use of an unset destination opens the system folder picker; choose a permitted subfolder, not Android 11+'s restricted Downloads root. Grants persist via takePersistableUriPermission, files are written via ContentResolver/DocumentsContract, revoked grants request re-selection. Reset only clears that kind's destination. Old public MediaStore receipts and private offline files stay readable; old originals are never moved or deleted.

Names keep original Artist / Source-or-episode / Post ID. Clip names additionally include A–B milliseconds and the precise-mode identifier; byte-safe truncation preserves Post ID/extension, and existing names gain a numeric suffix. Saving creates only a new document, removes its own incomplete file on failure/cancel and reports completion only after verified export and completed write. Private source/clip files are not included in backups.

## Validation

Focused tests cover fractional-FPS/end clamping and safe names; native VFR/B-frame decoded indices and rapid signed steps; A/B confirmed timestamps and invalid intervals; stable video dimensions/player identity when expanding; separate persistent SAF destinations and collisions; re-encoded output counts/duration and decoded first/last colors in CFR/VFR clips, unchanged source hashes and failure handling. Codec fixtures are generated by scripts/generate-clip-fixtures.sh and never used as site content. API 28/35 phone and API 28 tablet checks are in player-tools.yml; original Android CI stays enabled. Actual results are recorded after execution, not inferred from compilation.

Latest original CI counts, failures and captures: [player-tools evidence](https://github.com/Milenal82/sakugapp/blob/evidence/player-tools/report.json). The source_commit field identifies the exact tested revision; earlier failures remain in that branch history.
The current development branch removes user-selectable stream-copy export and its unused WebM writer. All exports use the verified re-encoding pipeline; failure never falls back to copying. Internal transmuxing only combines the already re-encoded video/audio tracks.
