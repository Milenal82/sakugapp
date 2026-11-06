#!/usr/bin/env bash
set -uo pipefail
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.ExtensionUiDeviceTest,com.sakugapp.DiscoveryNavigationDeviceTest,com.sakugapp.ClipExportDeviceTest,com.sakugapp.LocalProfileDeviceTest,com.sakugapp.BackupRestoreDeviceTest,com.sakugapp.FrameControlsDeviceTest
result=$?
mkdir -p app/build/reports/ui-evidence
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
