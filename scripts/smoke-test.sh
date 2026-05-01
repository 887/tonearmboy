#!/usr/bin/env bash
# Phase B.5 codec smoke test.
#
# Generates four short test fixtures (MP3, FLAC, OGG Vorbis, OPUS) with
# ffmpeg, pushes them to the connected Android target, then drives
# playback through the running PlaybackService via SmokeTestReceiver and
# asserts ExoPlayer reaches STATE_READY for each fixture.
#
# Fixtures are kept local (in /tmp by default) and are NOT committed.
#
# Requirements: ffmpeg, adb, an installed tonearm debug APK on the
# connected device. Tested against `emulator-5554` (API 36).
#
# Usage:
#   scripts/smoke-test.sh                # all four codecs
#   scripts/smoke-test.sh mp3 flac       # subset
#
set -euo pipefail

TAG="tonearm-smoke"
APP_ID="com.eight87.tonearm"
RECEIVER="${APP_ID}/.playback.SmokeTestReceiver"
ACTION="com.eight87.tonearm.action.SMOKE_PLAY"
TMPDIR="${TMPDIR:-/tmp}"
DEVICE="${ADB_DEVICE:-}"
ADB=(adb)
if [[ -n "$DEVICE" ]]; then
  ADB+=( -s "$DEVICE" )
fi

CODECS=("$@")
if [[ ${#CODECS[@]} -eq 0 ]]; then
  CODECS=(mp3 flac ogg opus)
fi

generate() {
  local codec="$1" out="$2"
  case "$codec" in
    mp3)  ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -ac 2 -b:a 96k "$out" ;;
    flac) ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -ac 2 "$out" ;;
    ogg)  ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -ac 2 -c:a libvorbis "$out" ;;
    opus) ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -ac 2 -c:a libopus  "$out" ;;
    *) echo "unknown codec: $codec" >&2; return 2 ;;
  esac
}

pass=0; fail=0; failed_codecs=()

for codec in "${CODECS[@]}"; do
  fixture="${TMPDIR}/tonearm-smoke.${codec}"
  # Land the fixture in the app's *internal* data dir so the player can
  # read it without any storage permission. Scoped storage on API 30+
  # blocks raw file:// reads of /sdcard for app processes; pushing via
  # /data/local/tmp + `run-as` is the canonical workaround for a debug
  # build's smoke test.
  remote_tmp="/data/local/tmp/tonearm-smoke.${codec}"
  remote="/data/data/${APP_ID}/files/tonearm-smoke.${codec}"
  echo "[${codec}] generating ${fixture}"
  generate "$codec" "$fixture"
  echo "[${codec}] pushing to ${remote}"
  "${ADB[@]}" push "$fixture" "$remote_tmp" >/dev/null
  "${ADB[@]}" shell "cat ${remote_tmp} | run-as ${APP_ID} sh -c 'cat > ${remote}'"
  "${ADB[@]}" shell rm -f "$remote_tmp" >/dev/null 2>&1 || true

  # Clear logcat so we only see this iteration's output.
  "${ADB[@]}" logcat -c
  echo "[${codec}] broadcasting SMOKE_PLAY"
  "${ADB[@]}" shell am broadcast -a "$ACTION" -n "$RECEIVER" --es path "$remote" >/dev/null

  # Poll logcat for a STATE_READY line for up to 15 seconds.
  ready=0
  for _ in $(seq 1 30); do
    if "${ADB[@]}" logcat -d -s tonearm:I 2>/dev/null | grep -q "STATE_READY for"; then
      ready=1; break
    fi
    sleep 0.5
  done

  if [[ $ready -eq 1 ]]; then
    echo "[${codec}] PASS"
    pass=$((pass+1))
  else
    echo "[${codec}] FAIL — no STATE_READY within 15s"
    "${ADB[@]}" logcat -d -s tonearm:* | tail -20 || true
    fail=$((fail+1)); failed_codecs+=("$codec")
  fi

  # Stop playback before next iteration.
  "${ADB[@]}" shell am force-stop "$APP_ID" || true
done

echo
echo "Summary: ${pass} pass, ${fail} fail"
if [[ $fail -gt 0 ]]; then
  echo "Failed codecs: ${failed_codecs[*]}"
  exit 1
fi
