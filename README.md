# Playback stability verification

Source: `7304de48c06c72cedf2583b4252a0c0dfcd17349`

Run: https://github.com/Milenal82/sakugapp/actions/runs/41228933633

Only the bounded feed regression, Debug Lint/build and two short API 28 device checks run on this branch. The checks cover compact captions, folder selection, landscape/portrait immersive playback, confirmed taps, player identity/position/speed/A-B, lifecycle-owner changes, media switches and disposal. This is not an hours-long soak or a claim that every device crash is fixed. Captures are original test-app renders; the video pattern is an isolated codec fixture, not website content.
