#!/usr/bin/env bash
# D.17.1 — regenerate the adaptive launcher icon raster fallbacks.
#
# Inputs:
#   app/src/main/res/drawable-nodpi/ic_launcher_source.png  (1024x1536 RGBA,
#     transparent canvas surrounding the fox-vinyl artwork)
#
# Outputs:
#   mipmap-{xxxhdpi:432,xxhdpi:324,xhdpi:216,hdpi:162,mdpi:108}/
#       ic_launcher_foreground.png
#       ic_launcher_monochrome.png
#
# Pipeline:
#   1. Detect the opaque-content bounding box (alpha-extract + threshold)
#      so the trim ignores the translucent dark canvas around the artwork.
#   2. Crop to that box, pad to a square (centered) so the adaptive icon
#      foreground is square as Android requires.
#   3. Resize down to each density target.
#   4. Emit a monochrome alpha-extracted variant for Android 13+ themed
#      icons.
#
# The script is idempotent — rerunning overwrites the generated rasters.

set -euo pipefail

cd "$(dirname "$0")/.."

SOURCE="app/src/main/res/drawable-nodpi/ic_launcher_source.png"

if [[ ! -f "$SOURCE" ]]; then
  echo "fatal: $SOURCE missing" >&2
  exit 1
fi

if ! command -v magick >/dev/null 2>&1; then
  echo "fatal: ImageMagick 7 (magick) not on PATH" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 1. Determine the opaque-content bounding box.
BOX="$(magick "$SOURCE" -alpha extract -threshold 1% -format "%@" info:)"
echo "alpha-content bbox = $BOX"

# 2. Crop to that box.
magick "$SOURCE" -crop "$BOX" +repage "$WORK/cropped.png"

# 3. Pad to a square. Side = max(W, H), centered, transparent fill.
W="$(magick "$WORK/cropped.png" -format "%w" info:)"
H="$(magick "$WORK/cropped.png" -format "%h" info:)"
SIDE=$(( W > H ? W : H ))
magick "$WORK/cropped.png" \
  -background "rgba(0,0,0,0)" -gravity center \
  -extent "${SIDE}x${SIDE}" \
  +repage "$WORK/square.png"

echo "squared = ${SIDE}x${SIDE}"

# 4. Emit foreground rasters per density and the monochrome silhouette.
declare -A DENSITIES=(
  [xxxhdpi]=432
  [xxhdpi]=324
  [xhdpi]=216
  [hdpi]=162
  [mdpi]=108
)

for DPI in "${!DENSITIES[@]}"; do
  PX="${DENSITIES[$DPI]}"
  OUT_DIR="app/src/main/res/mipmap-${DPI}"
  mkdir -p "$OUT_DIR"

  magick "$WORK/square.png" \
    -resize "${PX}x${PX}" \
    -strip \
    "$OUT_DIR/ic_launcher_foreground.png"

  magick "$OUT_DIR/ic_launcher_foreground.png" \
    -alpha extract \
    -strip \
    "$OUT_DIR/ic_launcher_monochrome.png"

  echo "wrote ${OUT_DIR}/ic_launcher_foreground.png + monochrome.png at ${PX}px"
done

echo "done."
