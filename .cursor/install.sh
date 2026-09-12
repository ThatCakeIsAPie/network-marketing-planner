#!/usr/bin/env bash
# Idempotent Android SDK bootstrap + Gradle dependency warm-up for the
# Network Marketing Planner app. Safe to run repeatedly.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"

# Packages required to build the app (see README: Android SDK 35, JDK 17+).
PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"
PLATFORM_TOOLS="platform-tools"

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

echo "==> Using ANDROID_HOME=$ANDROID_HOME"

if [ ! -x "$SDKMANAGER" ]; then
  echo "==> Installing Android command-line tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp_zip="$(mktemp --suffix=.zip)"
  curl -fsSL -o "$tmp_zip" "$CMDLINE_TOOLS_URL"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest" "$ANDROID_HOME/cmdline-tools/cmdline-tools"
  unzip -q "$tmp_zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -f "$tmp_zip"
else
  echo "==> Android command-line tools already present"
fi

echo "==> Accepting SDK licenses"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

echo "==> Installing SDK packages: $PLATFORM_TOOLS, $PLATFORM, $BUILD_TOOLS"
"$SDKMANAGER" "$PLATFORM_TOOLS" "$PLATFORM" "$BUILD_TOOLS"

# Point the Gradle build at the SDK. local.properties is gitignored.
echo "==> Writing local.properties"
echo "sdk.dir=$ANDROID_HOME" > "$(dirname "$0")/../local.properties"

# Warm the Gradle cache so the first agent build is fast and offline-friendly.
echo "==> Warming Gradle dependency cache (assembleDebug)"
cd "$(dirname "$0")/.."
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew --no-daemon assembleDebug

echo "==> Android environment ready"
