#!/usr/bin/env bash
# Only run on the workflow's dedicated, disposable emulator. Never clears a user's device.
set -euo pipefail
mode="${PERF_MODE:-baseline}"
[[ "$mode" == baseline || "$mode" == compare ]]
base_ref=65124526ed12726f06fa41249dd78f4f7e368a1a
report_dir="$PWD/app/build/reports/performance"
mkdir -p "$report_dir"
run_profile() {
  local checkout="$1" label="$2" trial="$3"
  # Fresh fixture databases/cache for both revisions, same emulator and real frame clock.
  adb shell pm clear com.sakugapp >/dev/null 2>&1 || true
  local test_status=0
  (cd "$checkout" && ./gradlew --no-daemon connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.PerformanceScenarioDeviceTest \
    -Pandroid.testInstrumentationRunnerArguments.profileLabel="$label") || test_status=$?
  mkdir -p "$report_dir/trial-$trial/$label"
  adb pull "/sdcard/Download/sakugapp-performance/$label/." "$report_dir/trial-$trial/$label/" || true
  cp -r "$checkout/app/build/outputs/androidTest-results" "$report_dir/trial-$trial/$label/test-results"
  return "$test_status"
}
if [[ "$mode" == baseline ]]; then
  run_profile "$PWD" baseline 1
else
  baseline="${RUNNER_TEMP:?}/sakugapp-performance-baseline"
  git worktree add --detach "$baseline" "$base_ref"
  trap 'git worktree remove "$baseline" --force' EXIT
  mkdir -p "$baseline/app/src/debug/java/com/sakugapp"
  cp app/src/debug/AndroidManifest.xml "$baseline/app/src/debug/AndroidManifest.xml"
  cp app/src/debug/java/com/sakugapp/PerformanceHostActivity.kt "$baseline/app/src/debug/java/com/sakugapp/"
  cp app/src/androidTest/java/com/sakugapp/PerformanceScenarioDeviceTest.kt "$baseline/app/src/androidTest/java/com/sakugapp/"
  run_profile "$baseline" baseline 1
  run_profile "$PWD" current 1
  run_profile "$PWD" current 2
  run_profile "$baseline" baseline 2
fi
