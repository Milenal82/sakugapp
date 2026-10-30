# Maintenance verification

Source: `5cfb1068ef4d43db5df70b53f44b82155c7b5e6e`

Run: https://github.com/Milenal82/sakugapp/actions/runs/40992907739

Focused JVM backup tests, Android Lint, Debug compilation, fresh-install Room restore and API 28 Nexus S native search/history/settings checks. Original screenshots use a physical narrow emulator profile and 1.3 font scale, in dark and light themes. Collection/history tests use legal public-response fixtures, not account data. The existing main workflow is unchanged; its same-commit automatic checks are read in report.json when available. Full reports and the installable Debug APK remain Actions artifacts. This branch contains no APK, signing material, credentials or media.
