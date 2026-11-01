#!/usr/bin/env bash
set -euo pipefail
baseline="$RUNNER_TEMP/sakugapp-scroll-baseline"
git worktree add --detach "$baseline" 7304de48c06c72cedf2583b4252a0c0dfcd17349
cp app/src/androidTest/java/com/sakugapp/PostScrollDeviceTest.kt "$baseline/app/src/androidTest/java/com/sakugapp/"
(cd "$baseline" && timeout --kill-after=20s 8m ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.PostScrollDeviceTest -Pandroid.testInstrumentationRunnerArguments.profileLabel=baseline)
mkdir -p app/build/reports/post-scroll-profile
adb pull /sdcard/Download/sakugapp-ui-evidence/scroll-baseline.json app/build/reports/post-scroll-profile/
timeout --kill-after=20s 8m ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.PostScrollDeviceTest -Pandroid.testInstrumentationRunnerArguments.profileLabel=current
adb pull /sdcard/Download/sakugapp-ui-evidence/scroll-current.json app/build/reports/post-scroll-profile/
git worktree remove "$baseline" --force
