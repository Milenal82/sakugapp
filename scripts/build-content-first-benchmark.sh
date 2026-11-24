#!/usr/bin/env bash
set -euo pipefail
baseline_ref=e0a9e387471b1dde73330f11ff112dfaa5cffa98
source_root="$PWD"
baseline_path="${RUNNER_TEMP:?CI temporary directory}/content-first-baseline"
apk_path="$RUNNER_TEMP/content-first-apks"
mkdir -p "$apk_path/current" "$apk_path/baseline"
# Both revisions use this disposable runner's same Debug keystore; no key is exported or copied.
./gradlew --no-daemon assembleDebug assembleDebugAndroidTest
cp app/build/outputs/apk/debug/app-debug.apk "$apk_path/current/"
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk "$apk_path/current/"
git worktree add --detach "$baseline_path" "$baseline_ref"
cleanup() {
  git -C "$baseline_path" restore app/src/androidTest/java/com/sakugapp/PostScrollDeviceTest.kt
  git worktree remove "$baseline_path"
}
trap cleanup EXIT
# Only the identical measurement harness is shared. No UI code is ported into the baseline.
cp app/src/androidTest/java/com/sakugapp/PostScrollDeviceTest.kt "$baseline_path/app/src/androidTest/java/com/sakugapp/"
(cd "$baseline_path" && "$source_root/gradlew" --no-daemon assembleDebug assembleDebugAndroidTest)
cp "$baseline_path/app/build/outputs/apk/debug/app-debug.apk" "$apk_path/baseline/"
cp "$baseline_path/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$apk_path/baseline/"
