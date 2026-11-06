# Player, community and batch download update

Development branch: `dev/player-lock-community-batch`. Package/version/signing settings remain unchanged. No Release, tag or automatic APK upload is part of this update.

## Changes

- The inline frame toolbar is one row: `<<< << < | frame/time | > >> >>> [lock] [fullscreen] [more]`. Narrow windows compact arrow cells and the counter; buttons remain 48 dp high. Requested custom frame accessibility descriptions were removed. Lock, fullscreen and more retain labels/tooltips. Actual decoded frames and estimated timestamps retain their existing distinction in More.
- PlayerView auto-show is disabled, including paused/stopped states. Only a confirmed tap on the video toggles its controller. External frame buttons cancel pending video taps and hide the controller, without recreating ExoPlayer. Lock blocks touch delivery to the video, disables its controller and keeps the external toolbar operational. The same lock state survives full screen/rotation and resets for another post. Locked full screen reserves the bottom toolbar so unlock is always reachable without a video overlay.
- The normal single-column detail has a 900 dp maximum width. Video width is bounded by the available window height and the real aspect ratio; no expanded/collapsed fixed heights or video weight allocation. More remains independent content below the toolbar. The original encoded picture is neither cropped nor stretched.
- Vote is a star below the existing five actions. The popup keeps the server's 0/1/2/3 meanings. Account changes invalidate vote state; only successful server responses update Score, including the browse snapshot, adjacent local sequence and stored favorite snapshot. No website endpoint, CSRF or scoring rule changed.
- Comments start collapsed and do not request their list until expanded. A bounded post-keyed ViewModel cache retains loaded comments, pagination and drafts when collapsed/reopened. CRUD, retry, login and timestamp seeks remain independent of Vote.

## Confirmed playback work

Before this change, every 250 ms playback-position update reconfigured the native view and reapplied GPU layers/color matrices and the same frame drawable. Native view updates now skip unchanged transforms, effects and bitmaps; neutral colors skip explicit effect paints. This removes confirmed redundant work, but is not a measured claim about every device's dropped frames or long-session stability.

## Batch downloads

The queue is a separate, versioned AtomicFile journal in private application storage. It contains public post/media metadata and task states, not account credentials. Favorites, hierarchy, media receipts, backup schemas and old private downloads are untouched.

Each submission retains the 20-video limit. Default concurrency is two actual network transfers; the queue panel permits 1–3. The global storage mutex now covers only reservations, unique target creation and index commits, not network transfer. Concurrent reservations enforce 200 MiB per file and 1 GiB total, counting old private video files too. Unknown sizes reserve available capacity conservatively. A valid completed receipt deduplicates media; in-flight downloads of the same media share one result. Progress updates are throttled to five per second, with per-file and per-batch states.

A data-sync foreground service owns workers; Compose disposal does not own/cancel them. Pause/cancel deletes only the current task's incomplete destination. Completed videos remain. Failed tasks can be retried individually. Interrupted tasks restart the file safely from zero on the next foreground app visit; this is **task-level recovery, not byte-range resume**. The persistent paused state prevents unintended resumption. Force-stop, OS data-sync timeouts and vendor process restrictions can stop background work; reopen the app to resume queued tasks. Notifications need permission on Android 13+, but denial does not grant additional storage/network permissions.

HTTP 421/429 and transient server failures use bounded retry counts, exponential delay and server Retry-After (seconds/date). The host-wide cooldown is also restored from pending journal entries. Existing public-read spacing remains. No alternate hosts or bypasses are used.

Original format, sanitized filenames and the separate BATCH SAF destination remain. Removed partial documents are recorded before transfer and cleaned after process interruption; final receipts are committed only after a complete, flushed response. Directory authorization/full-storage errors are surfaced. Old private videos remain readable/exportable.

Reference inspected: [weekanya/Booru](https://github.com/weekanya/Booru/tree/077ae27d1db4b6cc20715ed35a96beab65965ef0), README, GPL-3.0 LICENSE and `OfflineStore.kt`, `OfflineDownloader.kt`, `OfflineDownloadService.kt`. The implementation here is independent Kotlin code; no reference source was copied. Its four-worker and storage limits were not adopted.

## Verification

See the branch workflow **Player community and batch queue checks** and its XML reports/screenshots. Binaries are built only within the workspace/runner and are not uploaded. Local and device results are recorded after execution; compilation alone does not verify touch behavior, actual tablet layout, background survival or playback smoothness. Physical-device testing, real authenticated website votes/comments and prolonged real-network playback remain separate checks.

The queue transfer test selects an actual SAF subfolder with the system picker on every profile. A diagnostic attempt without a selected folder on API 28 correctly produced `directory_access` before any HTTP request; it is not counted as a successful transfer. The actual UI already requires directory selection before enqueueing. Existing directory tests separately cover revoked grants and full-storage failures.
