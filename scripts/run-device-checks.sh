#!/usr/bin/env bash
set -uo pipefail
# Preserve Gradle's result while exporting evidence before the emulator action shuts down.
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sakugapp.DeviceTests,com.sakugapp.UiSmokeTest,com.sakugapp.PublicBrowseTest,com.sakugapp.BrowsingUpgradeDeviceTest,com.sakugapp.SuggestionInputDeviceTest,com.sakugapp.PlayerRotationDeviceTest,com.sakugapp.PublicPaginationTest,com.sakugapp.LocalLibraryDeviceTest,com.sakugapp.LocalLibraryUiDeviceTest,com.sakugapp.ProfessionalPlayerDeviceTest,com.sakugapp.OfflineLibraryDeviceTest,com.sakugapp.LibraryMigrationDeviceTest,com.sakugapp.CompactInterfaceDeviceTest,com.sakugapp.LibraryDocumentsDeviceTest,com.sakugapp.PersonalStoreDeviceTest,com.sakugapp.PersonalFeatureUiDeviceTest,com.sakugapp.RefreshGridDeviceTest,com.sakugapp.SearchNavigationDeviceTest,com.sakugapp.AdvancedControlsLayoutDeviceTest
result=$?
mkdir -p app/build/reports/ui-evidence
adb pull /sdcard/Download/sakugapp-ui-evidence/. app/build/reports/ui-evidence/ || true
exit "$result"
