# Playback stability verification

Source: `5ac61b3c55c4bdea90a51156b4b517e7be8137bb`

Run: https://github.com/Milenal82/sakugapp/actions/runs/41228097312

Only the bounded feed regression, Debug Lint/build and two short API 28 device checks run on this branch. The checks cover compact captions, folder selection, landscape/portrait immersive playback, confirmed taps, player identity/position/speed/A-B, lifecycle-owner changes, media switches and disposal. This is not an hours-long soak or a claim that every device crash is fixed. Captures are original test-app renders; the video pattern is an isolated codec fixture, not website content.
