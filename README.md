# v1.2.2 focused verification

Source: `8aeb9bed095aecdda43c4b75785de83f28ce76ad`

Run: https://github.com/Milenal82/sakugapp/actions/runs/44704094281

Focused JVM checks, Lint/build and six API 28 device cases cover legacy settings/history preservation, complete pink/mint palettes and menus, temporary mute/loop controls, screenshot sharing, fullscreen and short scrolling. Original captures use public-response fixtures and an isolated codec pattern. scroll_profile is actual emulator FrameMetrics with thumbnails absent to isolate layout; it is not a real-device benchmark. The existing main CI remains enabled. Official v1.2.1-to-v1.2.2 signed upgrade checks run separately before publication.
