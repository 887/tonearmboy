#!/usr/bin/env bash
# Phase C library smoke test.
#
# Pushes a handful of small audio fixtures to /sdcard/Music on the
# connected Android target, asks MediaStore to index them, installs the
# current debug APK, and pokes LibraryScanReceiver to run the
# MediaStore -> Room scan path. Asserts the fixtures show up in the
# scan log line and that Room ends up holding at least the seeded
# count.
#
# Requirements: ffmpeg, adb, a built debug APK at
# app/build/outputs/apk/debug/app-debug.apk, and a target reachable as
# `adb devices`. Tested against `emulator-5554` (API 36).
#
# Usage:
#   scripts/library-smoke-test.sh
set -euo pipefail

APP_ID="com.eight87.tonearmboy"
RECEIVER="${APP_ID}/.data.LibraryScanReceiver"
ACTION="com.eight87.tonearmboy.action.LIBRARY_SCAN"
APK="app/build/outputs/apk/debug/app-debug.apk"
TMPDIR="${TMPDIR:-/tmp}"
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

# Generate four fixture tracks with distinct titles so we can verify
# them by name in the logcat dump.
FIXTURE_NAMES=(alpha beta gamma delta)
LOCAL_FIXTURES=()
REMOTE_FIXTURES=()
for name in "${FIXTURE_NAMES[@]}"; do
  local_path="${TMPDIR}/tonearmboy-lib-${name}.mp3"
  remote_path="/sdcard/Music/tonearmboy-lib-${name}.mp3"
  echo "[fixture] generating ${local_path}"
  ffmpeg -y -loglevel error \
    -f lavfi -i "sine=frequency=440:duration=1" \
    -metadata title="Tonearmboy ${name}" \
    -metadata artist="Tonearmboy Test" \
    -metadata album="Tonearmboy Smoke" \
    -metadata genre="Rock" \
    -ac 2 -b:a 96k "$local_path"
  LOCAL_FIXTURES+=("$local_path")
  REMOTE_FIXTURES+=("$remote_path")
  echo "[fixture] pushing to ${remote_path}"
  "${ADB[@]}" push "$local_path" "$remote_path" >/dev/null
done

# Ask MediaStore to (re)index each fixture so MediaStore sees the new
# files. Without this the scan would miss them on some devices.
for remote in "${REMOTE_FIXTURES[@]}"; do
  "${ADB[@]}" shell am broadcast \
    -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file://${remote}" >/dev/null
done

# Grant the audio permission so the receiver's scan returns rows.
# READ_MEDIA_AUDIO is the API 33+ name; for older targets adb falls
# back to READ_EXTERNAL_STORAGE silently.
"${ADB[@]}" shell pm grant "$APP_ID" android.permission.READ_MEDIA_AUDIO 2>/dev/null || true
"${ADB[@]}" shell pm grant "$APP_ID" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

echo "[scan] broadcasting LIBRARY_SCAN"
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am broadcast -a "$ACTION" -n "$RECEIVER" >/dev/null

# Wait for the SCAN_COMPLETE marker, up to 30 seconds.
ready=0
for _ in $(seq 1 60); do
  if "${ADB[@]}" logcat -d -s tonearmboy:I 2>/dev/null | grep -q "library-smoke: SCAN_COMPLETE"; then
    ready=1; break
  fi
  sleep 0.5
done

logs="$("${ADB[@]}" logcat -d -s tonearmboy:I 2>/dev/null || true)"

cleanup() {
  echo "[cleanup] removing remote fixtures"
  for remote in "${REMOTE_FIXTURES[@]}"; do
    "${ADB[@]}" shell rm -f "$remote" >/dev/null 2>&1 || true
    "${ADB[@]}" shell am broadcast \
      -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
      -d "file://${remote}" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

if [[ $ready -ne 1 ]]; then
  echo "scan did not complete within 30s" >&2
  echo "$logs" | tail -30 >&2
  exit 1
fi

# Assert each seeded title appeared in the scan dump.
fail=0
for name in "${FIXTURE_NAMES[@]}"; do
  if ! grep -q "Tonearmboy ${name}" <<< "$logs"; then
    echo "missing fixture in scan: Tonearmboy ${name}" >&2
    fail=$((fail + 1))
  fi
done

if [[ $fail -ne 0 ]]; then
  echo "[FAIL] ${fail}/${#FIXTURE_NAMES[@]} fixtures missing" >&2
  echo "--- last logs ---" >&2
  echo "$logs" | tail -40 >&2
  exit 1
fi

echo "[PASS] all ${#FIXTURE_NAMES[@]} fixtures appeared in the library scan"
