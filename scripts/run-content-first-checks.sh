#!/usr/bin/env bash
set -uo pipefail
# Only Debug instrumentation; this branch never calls the general Android workflow.
animated_classes=com.sakugapp.ContentFirstDeviceTest,com.sakugapp.NavigationPerformanceDeviceTest
classes=(
  com.sakugapp.FrameControlsDeviceTest com.sakugapp.PlayerLockDeviceTest
  com.sakugapp.PlayerStabilityDeviceTest com.sakugapp.AdvancedControlsLayoutDeviceTest
  com.sakugapp.ClipExportDeviceTest com.sakugapp.CommunityUiDeviceTest
  com.sakugapp.LocalProfileDeviceTest com.sakugapp.LocalLibraryUiDeviceTest
  com.sakugapp.LibraryMigrationDeviceTest com.sakugapp.BackupRestoreDeviceTest
  com.sakugapp.DownloadFoldersDeviceTest com.sakugapp.BatchQueueDeviceTest
  com.sakugapp.DiscoveryNavigationDeviceTest com.sakugapp.MenuAndPoolDeviceTest
  com.sakugapp.ExtensionUiDeviceTest com.sakugapp.DebugLinksDeviceTest
  com.sakugapp.SearchNavigationDeviceTest com.sakugapp.SuggestionInputDeviceTest
  com.sakugapp.ThemeSettingsDeviceTest com.sakugapp.PostScrollDeviceTest
  com.sakugapp.PreviewToolsBackDeviceTest com.sakugapp.ReleaseUiDeviceTest
)
test_classes=$(IFS=,;printf '%s' "${classes[*]}")
mkdir -p app/build/reports/ui-evidence app/build/reports/content-first-diagnostics
result=0
# New chrome/rebound/navigation cases need real animations. The established regression
# suite keeps the zero-animation environment used by performance-debug.yml at baseline.
# Separate instrumentation sessions also end any previous test process/window handoff.
for phase in animated regression; do
  if [ "$phase" = animated ]; then scale=1; selected="$animated_classes"; duration=12m
  else scale=0; selected="$test_classes"; duration=18m; fi
  for animation in animator_duration_scale transition_animation_scale window_animation_scale; do
    adb shell settings put global "$animation" "$scale"
  done
  timeout --kill-after=20s "$duration" ./gradlew --no-daemon connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$selected"
  status=$?
  if [ "$phase" = animated ] && [ -d app/build/outputs/androidTest-results/connected ]; then
    # Gradle replaces connected results on the second invocation. Preserve the first XML.
    rm -rf app/build/reports/content-first-diagnostics/animated-results
    cp -R app/build/outputs/androidTest-results/connected app/build/reports/content-first-diagnostics/animated-results
  fi
  if [ "$status" -ne 0 ]; then
    result=1
    timeout 10s adb logcat -d -t 400 -s TestRunner:I AndroidRuntime:E > "app/build/reports/content-first-diagnostics/$phase-runner.log" 2>&1 || true
  fi
  adb shell am force-stop com.sakugapp
done
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
