#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?Run in GitHub Actions}"
: "${GITHUB_ENV:?GitHub Actions environment file is required}"
sdk_root="$RUNNER_TEMP/sakugapp-sdk"
curl --fail --location --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$RUNNER_TEMP/android-cli.zip"
echo "2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258  $RUNNER_TEMP/android-cli.zip" | sha256sum --check
mkdir -p "$sdk_root/cmdline-tools"
unzip -q "$RUNNER_TEMP/android-cli.zip" -d "$RUNNER_TEMP/android-cli"
mv "$RUNNER_TEMP/android-cli/cmdline-tools" "$sdk_root/cmdline-tools/latest"
python3 -c 'print("y\n" * 100)' > "$RUNNER_TEMP/license-answers.txt"
"$sdk_root/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk_root" --licenses < "$RUNNER_TEMP/license-answers.txt" > "$RUNNER_TEMP/licenses.log"
"$sdk_root/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk_root" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
echo "ANDROID_HOME=$sdk_root" >> "$GITHUB_ENV"
echo "ANDROID_SDK_ROOT=$sdk_root" >> "$GITHUB_ENV"
