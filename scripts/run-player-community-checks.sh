#!/usr/bin/env bash
# Keep the emulator alive until original screenshots are copied out of its storage.
set -uo pipefail
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.CommunityUiDeviceTest,com.sakugapp.PlayerLockDeviceTest,com.sakugapp.DetailLayoutDeviceTest,com.sakugapp.FrameControlsDeviceTest,com.sakugapp.BatchQueueDeviceTest,com.sakugapp.DownloadFoldersDeviceTest,com.sakugapp.PlayerRotationDeviceTest
check_status=$?
mkdir -p app/build/reports/ui-evidence
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/
capture_status=$?
if [ "$check_status" -ne 0 ]; then exit "$check_status"; fi
if [ "$capture_status" -ne 0 ]; then exit "$capture_status"; fi
if ! compgen -G 'app/build/reports/ui-evidence/*.png' >/dev/null; then
    echo 'No current-run UI captures were exported.' >&2
    exit 1
fi
