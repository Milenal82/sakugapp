#!/usr/bin/env bash
set -uo pipefail
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.ReleaseUiDeviceTest,com.sakugapp.MenuAndPoolDeviceTest,com.sakugapp.ExtensionUiDeviceTest,com.sakugapp.DiscoveryNavigationDeviceTest,com.sakugapp.LocalProfileDeviceTest,com.sakugapp.BackupRestoreDeviceTest,com.sakugapp.FrameControlsDeviceTest,com.sakugapp.ClipExportDeviceTest,com.sakugapp.CommunityUiDeviceTest,com.sakugapp.PlayerLockDeviceTest,com.sakugapp.DownloadFoldersDeviceTest,com.sakugapp.BatchQueueDeviceTest
result=$?
mkdir -p app/build/reports/ui-evidence
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
