#!/usr/bin/env bash
set -euo pipefail
base_ref=65124526ed12726f06fa41249dd78f4f7e368a1a
baseline="${RUNNER_TEMP:?}/sakugapp-performance-baseline"
output="$PWD/app/build/performance-baseline"
if ! git cat-file -e "$base_ref^{commit}" 2>/dev/null; then git fetch --depth=1 origin "$base_ref"; fi
git worktree add --detach "$baseline" "$base_ref"
trap 'git worktree remove "$baseline" --force' EXIT
mkdir -p "$baseline/app/src/debug/java/com/sakugapp" "$output"
# Only the shared fixture host/measurement harness is added to the pinned application source.
cp app/src/debug/AndroidManifest.xml "$baseline/app/src/debug/AndroidManifest.xml"
cp app/src/debug/java/com/sakugapp/PerformanceHostActivity.kt "$baseline/app/src/debug/java/com/sakugapp/"
cp app/src/androidTest/java/com/sakugapp/PerformanceScenarioDeviceTest.kt "$baseline/app/src/androidTest/java/com/sakugapp/"
(cd "$baseline" && ./gradlew --no-daemon assembleDebug assembleDebugAndroidTest)
cp "$baseline/app/build/outputs/apk/debug/app-debug.apk" "$output/"
cp "$baseline/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$output/"
