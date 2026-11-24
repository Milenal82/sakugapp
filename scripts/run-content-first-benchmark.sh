#!/usr/bin/env bash
set -euo pipefail
# Never reset or install over a real device. This workflow owns the throw-away emulator.
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]; then
  echo 'The paired fixture benchmark requires the dedicated CI emulator.' >&2
  exit 1
fi
apk_path="${RUNNER_TEMP:?}/content-first-apks"
output="$PWD/app/build/reports/content-first-performance-reports"
mkdir -p "$output"
trial=0
for label in baseline current current baseline; do
  trial=$((trial+1))
  adb install -r -t "$apk_path/$label/app-debug.apk" >/dev/null
  adb install -r -t "$apk_path/$label/app-debug-androidTest.apk" >/dev/null
  for columns in 2 4 6 8 12; do
    # Reset only generated benchmark fixtures on the verified disposable AVD.
    adb shell pm clear com.sakugapp >/dev/null
    report="$output/trial-$trial-$label-$columns"
    mkdir -p "$report"
    adb shell am instrument -w -r -e class com.sakugapp.PostScrollDeviceTest -e profileLabel "$label" -e columns "$columns" com.sakugapp.test/androidx.test.runner.AndroidJUnitRunner > "$report/runner.log"
    if ! grep -q '^OK (' "$report/runner.log"; then cat "$report/runner.log"; exit 1; fi
    adb pull "/sdcard/Download/sakugapp-ui-evidence/content-first-scroll-$label-$columns.json" "$report/" >/dev/null
  done
done
