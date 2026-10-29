#!/usr/bin/env bash
set -uo pipefail
# Exercise system edge back rather than synthesizing app-level swipe callbacks.
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
for attempt in {1..15}; do
    mode=$(adb shell settings get secure navigation_mode | tr -d '\r')
    [ "$mode" = 2 ] && break
    sleep 1
done
if [ "$mode" != 2 ]; then
    echo 'Gestural system navigation was not enabled; edge-back verification cannot proceed.' >&2
    exit 1
fi
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.SearchNavigationDeviceTest -Pandroid.testInstrumentationRunnerArguments.gestural=true
result=$?
mkdir -p app/build/reports/ui-evidence
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
