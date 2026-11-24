#!/usr/bin/env bash
set -uo pipefail
# Only Debug instrumentation; this branch never calls the general Android workflow.
classes=(
  com.sakugapp.ContentFirstDeviceTest com.sakugapp.NavigationPerformanceDeviceTest
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
timeout --kill-after=20s 28m ./gradlew --no-daemon connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$test_classes"
result=$?
mkdir -p app/build/reports/ui-evidence app/build/reports/content-first-diagnostics
if [ "$result" -ne 0 ]; then
  timeout 10s adb logcat -d -t 400 -s TestRunner:I AndroidRuntime:E > app/build/reports/content-first-diagnostics/runner.log 2>&1 || true
fi
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
