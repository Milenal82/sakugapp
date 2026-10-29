#!/usr/bin/env bash
set -uo pipefail
# Preserve Gradle's result while exporting evidence before the emulator action shuts down.
timeout --kill-after=20s 15m ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.DeviceTests,com.sakugapp.UiSmokeTest,com.sakugapp.PublicBrowseTest,com.sakugapp.BrowsingUpgradeDeviceTest,com.sakugapp.SuggestionInputDeviceTest,com.sakugapp.PlayerRotationDeviceTest,com.sakugapp.PublicPaginationTest,com.sakugapp.LocalLibraryDeviceTest,com.sakugapp.LocalLibraryUiDeviceTest,com.sakugapp.ProfessionalPlayerDeviceTest,com.sakugapp.OfflineLibraryDeviceTest,com.sakugapp.LibraryMigrationDeviceTest,com.sakugapp.CompactInterfaceDeviceTest,com.sakugapp.LibraryDocumentsDeviceTest,com.sakugapp.PersonalStoreDeviceTest,com.sakugapp.PersonalFeatureUiDeviceTest,com.sakugapp.RefreshGridDeviceTest,com.sakugapp.SearchNavigationDeviceTest,com.sakugapp.AdvancedControlsLayoutDeviceTest
result=$?
if [ "$result" -ne 0 ]; then
    mkdir -p app/build/reports/device-diagnostics
    # Only test names and exception stacks; no broad credential/session/device dump.
    timeout 10s adb logcat -d -t 400 -s TestRunner:I AndroidRuntime:E > app/build/reports/device-diagnostics/runner.log 2>&1 || true
fi
mkdir -p app/build/reports/ui-evidence
timeout 20s adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
