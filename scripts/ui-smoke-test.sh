#!/usr/bin/env bash
# Phase D UI smoke test.
#
# Installs the current debug APK, launches the app, navigates from the
# Library tab into the Tracks list, taps a track to start playback, and
# asserts the Now Playing surface shows up with a non-empty title.
#
# Like library-smoke-test.sh this assumes a target reachable via
# `adb devices` (the headless `emulator-5554` AVD is the canonical
# choice). The `mobile` MCP is not used here — this is a portable shell
# loop on top of `adb shell uiautomator dump` / `adb shell input tap`
# so it can run from CI without Claude in the loop.
#
# Usage:
#   scripts/ui-smoke-test.sh
set -euo pipefail

APP_ID="com.eight87.tonearm"
APK="app/build/outputs/apk/debug/app-debug.apk"
DEVICE="${ADB_DEVICE:-}"
ADB=(adb)
if [[ -n "$DEVICE" ]]; then
  ADB+=( -s "$DEVICE" )
fi

if [[ ! -f "$APK" ]]; then
  echo "missing APK: $APK — build it first with ./gradlew :app:assembleDebug" >&2
  exit 1
fi

echo "[install] $APK"
"${ADB[@]}" install -r -d "$APK" >/dev/null

# Permissions are best-effort — the install on a freshly wiped emulator
# may not have any audio yet. We still grant in case the suite is run
# right after library-smoke-test.sh.
"${ADB[@]}" shell pm grant "$APP_ID" android.permission.READ_MEDIA_AUDIO 2>/dev/null || true
"${ADB[@]}" shell pm grant "$APP_ID" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

# Force a clean activity start so we land on Home.
"${ADB[@]}" shell am force-stop "$APP_ID"
"${ADB[@]}" shell am start -n "${APP_ID}/.MainActivity" >/dev/null
sleep 2

# Cheap helper: dump the screen and grep for any node whose text or
# content-desc attribute contains the supplied pattern. Compose ships
# its semantics text in `text=`; a few image-only nodes only have
# `content-desc`, so we grep against both. Note we cannot tr on space
# because attribute values legitimately contain spaces.
UIDUMP_LOCAL="${TMPDIR:-/tmp}/tonearm-uidump.xml"
grep_screen() {
  local pattern="$1"
  "${ADB[@]}" shell uiautomator dump /sdcard/_uidump.xml >/dev/null 2>&1 || true
  "${ADB[@]}" pull /sdcard/_uidump.xml "$UIDUMP_LOCAL" >/dev/null 2>&1
  grep -oE '(text|content-desc)="[^"]*"' "$UIDUMP_LOCAL" \
    | grep -E "$pattern" \
    | head -1
}

# Navigate to the Library tab. The bottom-nav is anchored at the
# bottom of the screen; we tap by relative x for portability across
# screen densities.
W=$("${ADB[@]}" shell wm size | awk -F' ' 'END{print $NF}' | cut -d 'x' -f1)
H=$("${ADB[@]}" shell wm size | awk -F' ' 'END{print $NF}' | cut -d 'x' -f2)
LIB_X=$(( W * 35 / 100 ))
NAV_Y=$(( H - 100 ))
echo "[nav] tapping Library at ${LIB_X},${NAV_Y}"
"${ADB[@]}" shell input tap "$LIB_X" "$NAV_Y"
sleep 1

# Switch to the Tracks tab.
TRACKS_X=$(( W * 45 / 100 ))
TRACKS_Y=350
echo "[nav] tapping Tracks tab"
"${ADB[@]}" shell input tap "$TRACKS_X" "$TRACKS_Y"
sleep 1

# Tap roughly at the first track row beneath the sticky header.
FIRST_TRACK_Y=$(( H * 25 / 100 ))
echo "[nav] tapping first track at 300,${FIRST_TRACK_Y}"
"${ADB[@]}" shell input tap 300 "$FIRST_TRACK_Y"
sleep 2

# Assert Now Playing has rendered with the expected top app bar title.
if grep_screen "Now Playing" >/dev/null; then
  echo "[PASS] Now Playing surface rendered"
else
  echo "[FAIL] expected 'Now Playing' top-app-bar title not found" >&2
  "${ADB[@]}" shell uiautomator dump /dev/stdout 2>/dev/null | head -200 >&2
  exit 1
fi
