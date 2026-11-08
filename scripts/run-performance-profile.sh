#!/usr/bin/env bash
# Only the workflow's dedicated disposable emulator; no user devices/data.
set -euo pipefail
apk_root="${PERF_APK_ROOT:?prebuilt Debug and baseline APK directories}"
report_dir="$PWD/app/build/reports/performance"
mkdir -p "$report_dir"
install_pair() {
  adb install -r -t "$apk_root/$1/app-debug.apk" >/dev/null
  adb install -r -t "$apk_root/$1/app-debug-androidTest.apk" >/dev/null
}
run_profile() {
  local label="$1" trial="$2" test_status=0
  local output="$report_dir/trial-$trial/$label"
  mkdir -p "$output"
  install_pair "$label"
  adb shell pm clear com.sakugapp >/dev/null
  bash scripts/run-device-instrumentation.sh com.sakugapp.PerformanceScenarioDeviceTest "$output/runner-profile.log" "$label" || test_status=$?
  adb pull "/sdcard/Download/sakugapp-performance/$label/." "$output/" || true
  adb logcat -d > "$output/logcat-profile.log"
  return "$test_status"
}
# ABBA on the same AVD and real frame clock. APKs were compiled before emulator startup.
run_profile baseline 1
run_profile current 1
run_profile current 2
run_profile baseline 2
install_pair current
