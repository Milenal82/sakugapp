#!/usr/bin/env bash
set -uo pipefail
geometry=${1:?Choose phone or landscape}
case "$geometry" in phone|landscape) ;; *) exit 2 ;; esac
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.SixColumnDeviceTest -Pandroid.testInstrumentationRunnerArguments.geometry="$geometry"
result=$?
mkdir -p app/build/reports/ui-evidence
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
