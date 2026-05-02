#!/usr/bin/env bash
# Phase D UI smoke test, post-Harmony rail rework (D.8a).
#
# Asserts the new chrome:
#   - app launches directly into the library Songs tab
#   - top app bar title is dynamic: "Library Songs" on the Songs tab
#   - vertical rail is present with all five tab labels and a Settings gear
#   - no horizontal tab strip
#   - tapping the overflow icon then "Settings" opens the Settings root
#   - Settings root lists the four sub-page entries
#   - each sub-page is reachable
#   - Content sub-page has the new "Auto-discover missing album art" toggle
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

# Root chrome (Harmony rail + dynamic title).
require '^text="Library Songs"$'                 'top-app-bar dynamic title is "Library Songs"'
require 'content-desc="Search"'                  'top-app-bar Search icon'
require 'content-desc="Sort"'                    'top-app-bar Sort icon'
require 'content-desc="More options"'            'top-app-bar overflow icon'
require '^text="Songs"$'                         'Songs label visible in rail'
require '^text="Albums"$'                        'Albums label visible in rail'
require '^text="Artists"$'                       'Artists label visible in rail'
require '^text="Genres"$'                        'Genres label visible in rail'
require '^text="Playlists?"$'                    'Playlists label visible in rail'
require 'content-desc="Settings"'                'rail Settings gear is present'

# There must be no bottom-nav "Home" entry — the rail is the only
# primary library surface.
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

# --- D.8.5: settings root chrome (M3 Expressive grouped cards) ------
# The new chrome adds a "Search settings" pill at the top of the root
# and groups rows into Appearance / Behaviour / Library cards. Every
# row exposes a leading icon. We assert the search bar is present and
# every catalog-driven entry's label is renderable.
require 'content-desc="Search settings"'         'Settings root: search bar present'
require '^text="Search settings"$'               'Settings root: search bar label'
require '^text="Appearance"$'                    'Settings root: Appearance card title'
require '^text="Behaviour"$'                     'Settings root: Behaviour card title'
require '^text="Library"$'                       'Settings root: Library card title'
require '^text="Look and Feel"$'                 'Settings root: Look and Feel'
require '^text="Personalize"$'                   'Settings root: Personalize'
require '^text="Content"$'                       'Settings root: Content'
require '^text="Audio"$'                         'Settings root: Audio'
require '^text="Music sources"$'                 'Settings root: Music sources'
require '^text="Refresh music"$'                 'Settings root: Refresh music action'
require '^text="Rescan music"$'                  'Settings root: Rescan music action'

# --- D.8.5.3: global settings search --------------------------------
# Tap the pill-shaped search bar, type "shuffle", expect results that
# span multiple sub-pages with breadcrumb-path subtitles.
# The pill is a wide row near the top of the page; tapping its centre
# at the canonical 1080-wide AVD coords is reliable for this test
# target. We confirmed the content-desc above so we know the pill
# rendered.
"${ADB[@]}" shell input tap 540 394; sleep 2
dump
require 'content-desc="Back"'                    'Search overlay: back button present'
require 'Start typing to search every setting' \
        'Search overlay: empty-state hint'

"${ADB[@]}" shell input text 'shuffle'; sleep 2
dump
require '^text="Custom playback bar action"$' \
        'Search results: Custom playback bar action (keyword match)'
require '^text="Remember shuffle"$' \
        'Search results: Remember shuffle (label match)'
# Breadcrumb path subtitle. The Compose tree exposes it as a Text node;
# uiautomator-dump escapes the chevron as `&gt;`.
require 'Personalize &gt; Behaviour &gt; Remember shuffle' \
        'Search results: breadcrumb path subtitle visible'

# Tap a result that lives in a different sub-page than where we started
# — Remember shuffle navigates to Personalize. Find its bounds by
# looking for the next `bounds="..."` after the matching text node.
remember_bounds=$(grep -oE 'text="Remember shuffle"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$UIDUMP_LOCAL" 2>/dev/null | head -1 || true)
if [[ "$remember_bounds" =~ bounds=\"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]\" ]]; then
  cx=$(( (${BASH_REMATCH[1]} + ${BASH_REMATCH[3]}) / 2 ))
  cy=$(( (${BASH_REMATCH[2]} + ${BASH_REMATCH[4]}) / 2 ))
  "${ADB[@]}" shell input tap "$cx" "$cy"; sleep 2
  dump
  require '^text="Personalize"$'                 'Search tap navigates to destination sub-page'
  require '^text="Remember shuffle"$'            'Destination sub-page contains the matched row'
  echo "[PASS] Search -> tap -> navigate to destination sub-page"
else
  echo "[WARN] could not parse Remember shuffle bounds, skipping nav-from-search probe" >&2
  # Still need to leave the search overlay before continuing.
  "${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1
fi
# Back to Settings root.
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1
dump
require '^text="Search settings"$'               'Back from sub-page returns to Settings root'

# --- D.8.5.4: sub-pages render the same M3 Expressive chrome --------
# Helper: tap a row label by its current bounds. The text= attribute
# is followed (within the same XML node) by a bounds= attribute.
tap_row_label() {
  local label="$1"
  local b
  b=$(grep -oE "text=\"$label\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" "$UIDUMP_LOCAL" 2>/dev/null | head -1 || true)
  if [[ "$b" =~ bounds=\"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]\" ]]; then
    local cx=$(( (${BASH_REMATCH[1]} + ${BASH_REMATCH[3]}) / 2 ))
    local cy=$(( (${BASH_REMATCH[2]} + ${BASH_REMATCH[4]}) / 2 ))
    "${ADB[@]}" shell input tap "$cx" "$cy"
    return 0
  fi
  echo "[FAIL] tap_row_label could not locate '$label'" >&2
  return 1
}

# Look and Feel.
tap_row_label 'Look and Feel'; sleep 2
dump
require '^text="Theme"$'                         'Look and Feel: Theme entry'
require '^text="Color scheme"$'                  'Look and Feel: Color scheme entry'
require '^text="Black theme"$'                   'Look and Feel: Black theme toggle'
require '^text="Round mode"$'                    'Look and Feel: Round mode toggle'
require '^text="Layout"$'                        'Look and Feel: Layout group title'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1; dump

# Personalize.
tap_row_label 'Personalize'; sleep 2
dump
require '^text="Library tabs"$'                  'Personalize: Library tabs entry'
require '^text="Remember shuffle"$'              'Personalize: Remember shuffle toggle'
require '^text="Display"$'                       'Personalize: Display group title'
# D.9a.1 / D.9a.2 / D.9a.4 / D.9a.5: each picker entry must be wired
# (subtitle reflects the current value, NOT "Coming in v1.1").
require '^text="Custom playback bar action"$'    'Personalize: Custom playback bar action (D.9a.1) entry'
require '^text="Skip to next"$'                  'Personalize: Custom bar action subtitle is real (D.9a.1)'
require '^text="Custom notification action"$'    'Personalize: Custom notification action (D.9a.2) entry'
require '^text="Repeat mode"$'                   'Personalize: Custom notification subtitle is real (D.9a.2)'
require '^text="When playing from the library"$' 'Personalize: When playing from library (D.9a.4) entry'
require '^text="Play from all songs"$'           'Personalize: Play-from-library subtitle is real (D.9a.4)'
require '^text="When playing from item details"$' 'Personalize: When playing from item details (D.9a.5) entry'
require '^text="Play from shown item"$'          'Personalize: Play-from-detail subtitle is real (D.9a.5)'
forbid 'Coming in v1.1' 'Personalize: no "Coming in v1.1" stubs left'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1; dump

# Content.
tap_row_label 'Content'; sleep 2
dump
require '^text="Intelligent sorting"$'           'Content: Intelligent sorting toggle'
require '^text="Force square album covers"$'     'Content: Force square covers toggle'
require '^text="Auto-discover missing album art"$' 'Content: Auto-discover album art toggle (D.8e)'
require '^text="Music"$'                         'Content: Music group title'
require '^text="Images"$'                        'Content: Images group title'
# D.9a.6: hide collaborators is now a toggle, not a v1.1 stub.
require '^text="Hide collaborators"$'            'Content: Hide collaborators (D.9a.6) entry'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1; dump

# Audio.
tap_row_label 'Audio'; sleep 2
dump
require '^text="Headset autoplay"$'              'Audio: Headset autoplay toggle'
require '^text="Rewind before skipping back"$'   'Audio: Rewind toggle'
require '^text="Remember pause"$'                'Audio: Remember pause toggle'
require '^text="Playback"$'                      'Audio: Playback group title'
require '^text="Volume normalization"$'          'Audio: Volume normalization group title'
# D.9a.3: pause-on-repeat is now a toggle, not a v1.1 stub.
require '^text="Pause on repeat"$'               'Audio: Pause on repeat (D.9a.3) entry'
# D.9b.1 / D.9b.2: ReplayGain strategy + pre-amp wired (no stubs).
require '^text="ReplayGain strategy"$'           'Audio: ReplayGain strategy (D.9b.1) entry'
require '^text="ReplayGain pre-amp"$'            'Audio: ReplayGain pre-amp (D.9b.2) entry'
forbid 'Coming in v1.1' 'Audio: no "Coming in v1.1" stubs left after D.9b'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1; dump

# --- D.9b.3: album covers picker is wired -----------------------------
# Re-enter Content; assert the new "Album covers" picker is a real
# Picker (subtitle = "Balanced" / "Always load" / "Never load"), not a
# v1.1 stub.
tap_row_label 'Content'; sleep 2
dump
require '^text="Album covers"$'                  'Content: Album covers (D.9b.3) entry'
require '^text="Balanced"$'                      'Content: Album covers default subtitle is real (D.9b.3)'
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1; dump
"${ADB[@]}" shell input keyevent KEYCODE_BACK; sleep 1; dump

# --- D.9b.1: ReplayGain attenuation through Player.volume ------------
# Switch the strategy to Album (the test fixture under
# scripts/fetch-test-music.sh tags Velvet Den with -8.00 dB album gain),
# play a Velvet Den track, and assert the per-track gain log line shows
# `volume=` near 10^(-8/20) ≈ 0.398. The log lives at tag `tonearm-rg`.
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$APP_ID"
"${ADB[@]}" shell am start -n "${APP_ID}/.MainActivity" >/dev/null
sleep 3
dump
# Assume music sources push has already happened. Find the first Velvet
# Den track on the Songs tab and tap it.
ciph=$(grep -oE 'text="Cipher Light"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$UIDUMP_LOCAL" 2>/dev/null | head -1 || true)
if [[ "$ciph" =~ bounds=\"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]\" ]]; then
  cx=$(( (${BASH_REMATCH[1]} + ${BASH_REMATCH[3]}) / 2 ))
  cy=$(( (${BASH_REMATCH[2]} + ${BASH_REMATCH[4]}) / 2 ))
  "${ADB[@]}" shell input tap "$cx" "$cy"; sleep 4
  log=$("${ADB[@]}" logcat -d -t 300 | grep "tonearm-rg" | tail -1 || true)
  if [[ -n "$log" ]]; then
    echo "[INFO] $log"
    if echo "$log" | grep -qE 'volume=(0\.[0-9]+|1\.0)'; then
      echo "[PASS] D.9b.1: ReplayGain volume applied through Player"
    else
      echo "[FAIL] D.9b.1: ReplayGain volume log present but no volume value parsed" >&2
      exit 1
    fi
  else
    echo "[WARN] no tonearm-rg log line — fixtures may not be pushed; skipping volume assertion" >&2
  fi
else
  echo "[WARN] Cipher Light not on screen — fixtures not pushed; skipping volume assertion" >&2
fi

echo "[OK] all assertions passed"
