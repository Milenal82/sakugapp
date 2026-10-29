#!/usr/bin/env bash
set -uo pipefail
# This maintenance run deliberately excludes the unrelated player and full device suites.
timeout --kill-after=20s 12m ./gradlew --no-daemon connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.BackupRestoreDeviceTest,com.sakugapp.MaintenanceUiDeviceTest
result=$?
mkdir -p app/build/reports/maintenance-diagnostics app/build/reports/maintenance-ui-evidence
if [ "$result" -ne 0 ]; then
    # Only test/exception output; do not collect account, session or broad device logs.
    timeout 10s adb logcat -d -t 400 -s TestRunner:I AndroidRuntime:E \
        > app/build/reports/maintenance-diagnostics/runner.log 2>&1 || true
fi
timeout 20s adb pull /sdcard/Download/sakugapp-ui-evidence/. \
    app/build/reports/maintenance-ui-evidence/ || true
exit "$result"
