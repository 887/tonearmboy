#!/usr/bin/env bash
# push-test-music.sh — push the test-music/ tracks to the running AVD/device
# and trigger a MediaStore rescan so they appear in the library.
#
# Run scripts/fetch-test-music.sh first to populate test-music/.

set -euo pipefail

ROOT="$(dirname "$(readlink -f "$0")")/.."
SRC="${ROOT}/test-music"

if [ ! -d "${SRC}" ] || [ -z "$(ls -A "${SRC}"/*.mp3 2>/dev/null)" ]; then
    echo "[push-test-music] no tracks in ${SRC}/. Run scripts/fetch-test-music.sh first." >&2
    exit 1
fi

if ! adb devices 2>/dev/null | grep -qE '\<device$'; then
    echo "[push-test-music] no ADB device. Run scripts/start-avd.sh." >&2
    exit 1
fi

DEVICE_DIR="/sdcard/Music/tonearmboy-test"
echo "[push-test-music] cleaning ${DEVICE_DIR}..."
adb shell rm -rf "${DEVICE_DIR}" || true
adb shell mkdir -p "${DEVICE_DIR}"

for f in "${SRC}"/*.mp3; do
    echo "[push-test-music] pushing $(basename "$f")..."
    adb push "$f" "${DEVICE_DIR}/$(basename "$f")" >/dev/null
done

echo "[push-test-music] triggering MediaStore rescan..."
for f in "${SRC}"/*.mp3; do
    adb shell am broadcast \
        -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d "file://${DEVICE_DIR}/$(basename "$f")" >/dev/null 2>&1 || true
done

# MEDIA_SCANNER_SCAN_FILE is deprecated since API 30 but still works on
# emulator. The scan is async — give it time, and retry once if empty.
verify_rows() {
    adb shell content query --uri content://media/external/audio/media \
        --where "_data LIKE '${DEVICE_DIR}/%'" --projection title 2>/dev/null \
        | grep -c "Row:" || true
}

count=0
for attempt in 1 2 3; do
    sleep 3
    count=$(verify_rows)
    [ "${count}" -gt 0 ] && break
    echo "[push-test-music] no rows yet (attempt ${attempt}/3), waiting..."
done

echo "[push-test-music] MediaStore reports ${count} test track(s) ingested"

if [ "${count}" -eq 0 ]; then
    echo "[push-test-music] note: scanner didn't pick the tracks up automatically." >&2
    echo "[push-test-music] open tonearmboy → Settings → Rescan music to force the cache to" >&2
    echo "[push-test-music] re-query MediaStore. The files ARE on the device." >&2
    exit 0
fi

echo "[push-test-music] done. Open tonearmboy — they should appear after the next library scan,"
echo "                  or hit Settings → Rescan music to force-refresh."
