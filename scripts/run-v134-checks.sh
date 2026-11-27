#!/usr/bin/env bash
set -uo pipefail
result=0
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.V134UiDeviceTest || result=1
mkdir -p app/build/reports/v134-new-tests
cp -R app/build/outputs/androidTest-results/connected/. app/build/reports/v134-new-tests/ 2>/dev/null || true
adb shell am force-stop com.sakugapp
bash scripts/run-refinement-checks.sh || result=1
mkdir -p app/build/reports/ui-evidence
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
