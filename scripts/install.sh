#!/usr/bin/env bash
# G.4 — dev sideload helper. Builds a debug APK and pushes it to the
# first connected device (AVD or wifi-adb phone). Mirrors what
# build-release-apk.sh does for releases, minus the gh-release plumbing.
#
# Usage:
#   scripts/install.sh           # build + install
#   scripts/install.sh --launch  # build + install + launch the activity
set -euo pipefail

cd "$(dirname "$0")/.."

LAUNCH=0
for arg in "$@"; do
  case "$arg" in
    --launch) LAUNCH=1 ;;
    -h|--help)
      sed -n '2,11p' "$0"
      exit 0
      ;;
  esac
done

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

echo "[install] running ./gradlew :app:assembleDebug..."
./gradlew :app:assembleDebug -q

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "[install] expected $APK but not found" >&2
  exit 1
fi

echo "[install] adb install -r $APK"
adb install -r "$APK"

if [[ $LAUNCH -eq 1 ]]; then
  echo "[install] launching com.eight87.tonearmboy/.MainActivity"
  adb shell am start -n com.eight87.tonearmboy/.MainActivity
fi

echo "[install] done"
