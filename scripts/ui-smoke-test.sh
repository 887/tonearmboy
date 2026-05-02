#!/usr/bin/env bash
# Phase D UI smoke test, post-Auxio refactor.
#
# Asserts the new chrome:
#   - app launches directly into the library Songs tab
#   - top app bar shows "tonearm" title plus Search / Sort / overflow icons
#   - five top tabs render (Songs / Albums / Artists / Genres / Playlists)
#   - no bottom navigation bar
#   - tapping the overflow icon then "Settings" opens the Settings root
#   - Settings root lists the four sub-page entries
#   - each sub-page is reachable
#
# Like library-smoke-test.sh this assumes a target reachable via
# `adb devices` (the headless `emulator-5554` AVD is the canonical
# choice). The `mobile` MCP is not used here — this is a portable shell
# loop on top of `adb shell uiautomator dump` so it can run from CI
# without Claude in the loop.
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

# Permissions are best-effort.
"${ADB[@]}" shell pm grant "$APP_ID" android.permission.READ_MEDIA_AUDIO 2>/dev/null || true
"${ADB[@]}" shell pm grant "$APP_ID" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

# Force a clean start.
"${ADB[@]}" shell am force-stop "$APP_ID"
"${ADB[@]}" shell am start -n "${APP_ID}/.MainActivity" >/dev/null
sleep 3

UIDUMP_LOCAL="${TMPDIR:-/tmp}/tonearm-uidump.xml"
dump() {
  "${ADB[@]}" shell uiautomator dump /sdcard/_uidump.xml >/dev/null 2>&1 || true
  "${ADB[@]}" pull /sdcard/_uidump.xml "$UIDUMP_LOCAL" >/dev/null 2>&1
}
have_text() {
  local pattern="$1"
  grep -oE '(text|content-desc)="[^"]*"' "$UIDUMP_LOCAL" \
    | grep -qE "$pattern"
}
require() {
  local pattern="$1" label="$2"
  if have_text "$pattern"; then
    echo "[PASS] $label"
  else
    echo "[FAIL] $label — pattern $pattern" >&2
    head -200 "$UIDUMP_LOCAL" >&2
    exit 1
  fi
}
forbid() {
  local pattern="$1" label="$2"
  if have_text "$pattern"; then
    echo "[FAIL] $label — pattern $pattern unexpectedly present" >&2
    exit 1
  fi
  echo "[PASS] $label"
}

dump

# Root chrome.
require '^text="tonearm"$'                       'top-app-bar title is "tonearm"'
require 'content-desc="Search"'                  'top-app-bar Search icon'
require 'content-desc="Sort"'                    'top-app-bar Sort icon'
require 'content-desc="More options"'            'top-app-bar overflow icon'
require '^text="Songs"$'                         'Songs tab visible at root'
require '^text="Albums"$'                        'Albums tab visible at root'
require '^text="Artists"$'                       'Artists tab visible at root'
require '^text="Genres"$'                        'Genres tab visible at root'
require '^text="Playlists?"$'                    'Playlists tab visible at root'

# There must be no bottom-nav "Home" entry — the tab strip is the only
# primary surface.
forbid '^text="Home"$'                           'no Home destination'

# Open overflow → Settings.
"${ADB[@]}" shell input tap 1006 211; sleep 1
dump
require '^text="Settings"$'                      'overflow menu Settings entry'
require '^text="Refresh music"$'                 'overflow menu Refresh entry'
require '^text="Rescan music"$'                  'overflow menu Rescan entry'

# Tap Settings entry.
"${ADB[@]}" shell input tap 918 357; sleep 2
dump
require '^text="Look and Feel"$'                 'Settings root: Look and Feel'
require '^text="Personalize"$'                   'Settings root: Personalize'
require '^text="Content"$'                       'Settings root: Content'
require '^text="Audio"$'                         'Settings root: Audio'
require '^text="Music sources"$'                 'Settings root: Music sources'
require '^text="Refresh music"$'                 'Settings root: Refresh music action'
require '^text="Rescan music"$'                  'Settings root: Rescan music action'

# Look and Feel sub-page.
"${ADB[@]}" shell input tap 540 376; sleep 2
dump
require '^text="Theme"$'                         'Look and Feel: Theme entry'
require '^text="Color scheme"$'                  'Look and Feel: Color scheme entry'
require '^text="Black theme"$'                   'Look and Feel: Black theme toggle'
require '^text="Round mode"$'                    'Look and Feel: Round mode toggle'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1

# Personalize sub-page.
"${ADB[@]}" shell input tap 540 543; sleep 2
dump
require '^text="Library tabs"$'                  'Personalize: Library tabs entry'
require '^text="Remember shuffle"$'              'Personalize: Remember shuffle toggle'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1

# Content sub-page.
"${ADB[@]}" shell input tap 540 710; sleep 2
dump
require '^text="Intelligent sorting"$'           'Content: Intelligent sorting toggle'
require '^text="Force square album covers"$'     'Content: Force square covers toggle'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1

# Audio sub-page.
"${ADB[@]}" shell input tap 540 877; sleep 2
dump
require '^text="Headset autoplay"$'              'Audio: Headset autoplay toggle'
require '^text="Rewind before skipping back"$'   'Audio: Rewind toggle'
require '^text="Remember pause"$'                'Audio: Remember pause toggle'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1

echo "[OK] all assertions passed"
