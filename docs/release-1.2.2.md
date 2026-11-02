# v1.2.2

Version: `1.2.2` / code `15`, package `com.sakugapp`. No Room or backup schema change: both remain at version 4. No account, media, favorite, classification, or history data is cleared.

Settings now have five groups. Gestures, snapshot sharing, looping/muting defaults, mobile preview/image access and history recording follow fixed policies. Legacy preference values are ignored on read and removed during initialization; the history policy is enabled without deleting entries. Temporary player mute/loop controls remain independent. Other preference keys and values are retained. The old `blue` palette maps to the full `mint` palette and is persisted; `pink`, light/dark/system mode, font scale and tag spacing retain their choices.

Sakura Pink and Mint Cyan define the full Material color scheme in light and dark appearances. Text-button colors use darker variants of the suggested light accents for readable contrast. Video content and black letterboxing are unchanged. Menu changes use the existing Material 3 popup and action handlers, without changing folder hierarchy or navigation.

## Verification

The focused workflow checks the legacy preference/history policy, palette contrast, theme persistence, menus, temporary playback controls, screenshot sharing, fullscreen sanity and short Posts scrolling. It also runs localization, affected JVM tests, Lint and Debug compilation. Original emulator captures and reports are attached to [Focused maintenance validation](https://github.com/Milenal82/sakugapp/actions/workflows/maintenance.yml).

The existing signed-release workflow runs Release unit tests, Lint and compilation, verifies the pinned official certificate, and installs the published v1.2.1 APK followed by v1.2.2 without uninstalling or clearing data. Its independent UI checker imports a public-response backup using the old app's normal file picker, creates history, disables the seven old options, and compares normal exports after upgrading, including root/subfolder relationships, tag favorites and standalone notes. It also checks retained history, newly recorded visits and the migrated mint theme. Publication requires these gates to pass.

These are bounded API 28 emulator checks, not a physical-device compatibility survey or long-duration stress test. Scrolling uses captured post metadata without thumbnails to isolate layout, not network/image performance. Metered-network access is checked by the unconditional application policy; carrier behavior is not simulated. Live account/Vote/comment operations and old unrelated suites are not manually retested for this change. Original CI checks remain enabled.

Official certificate SHA-256: `9c77062fa88e1fe35c443641d918d0389354b0eb85395a86504d939ebfc53fff`.

See [release notes in Chinese, English and Japanese](../release-notes/v1.2.2.md). Corresponding source ZIPs accompany the APK; signing material and local settings are excluded by the existing source checker.
