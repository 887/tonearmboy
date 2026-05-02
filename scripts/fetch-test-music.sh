#!/usr/bin/env bash
# fetch-test-music.sh — download CC-licensed test fixtures for tonearm.
#
# Pulls four SoundHelix sample tracks (CC-BY-SA, stable URL since ~2010),
# tags them with ID3v2 metadata, and embeds album art on half of them so
# the auto-discover-album-art path has something to fall back on.
#
# Output: test-music/ (gitignored). Optional --push flag installs onto the
# running AVD via push-test-music.sh.
#
# Prereqs: curl, ffmpeg, ImageMagick (`magick` or `convert`).

set -euo pipefail

ROOT="$(dirname "$(readlink -f "$0")")/.."
DEST="${ROOT}/test-music"
mkdir -p "${DEST}"

if ! command -v ffmpeg >/dev/null; then
    echo "[fetch-test-music] ffmpeg required" >&2; exit 1
fi
MAGICK="$(command -v magick || command -v convert || true)"
if [ -z "${MAGICK}" ]; then
    echo "[fetch-test-music] ImageMagick required (magick or convert)" >&2; exit 1
fi

# Format:  <url>|<title>|<artist>|<album>|<track>|<year>|<genre>|<embed-cover>|<rg-track-db>|<rg-album-db>
# rg-track-db / rg-album-db = ReplayGain values written as ID3v2 TXXX
# frames so the D.9b.1 parser has something to attenuate. The Velvet
# Den album carries a clearly-noticeable -8 dB album gain so the
# `dumpsys audio` smoke assertion can detect a volume change. Field
# Recordings is left unattenuated to verify the "missing tag = no
# change" path.
TRACKS=(
    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3|Cipher Light|The Synth Foxes|Velvet Den|1|2025|Synthwave|yes|-7.40 dB|-8.00 dB"
    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3|Brushwork|The Synth Foxes|Velvet Den|2|2025|Synthwave|yes|-8.20 dB|-8.00 dB"
    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3|Pawprints in Snow|Quiet Hours|Field Recordings|1|2024|Ambient|no||"
    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3|Slow Burn|Quiet Hours|Field Recordings|2|2024|Ambient|no||"
)

for entry in "${TRACKS[@]}"; do
    IFS='|' read -r url title artist album track year genre embed rg_track rg_album <<<"$entry"
    raw="${DEST}/$(basename "$url")"
    out="${DEST}/$(echo "$title" | tr ' ' '_').mp3"

    # Optional ReplayGain ID3v2 TXXX frames. ffmpeg writes any
    # `-metadata KEY=VALUE` it doesn't recognize as a TXXX frame, which
    # is exactly the encoding ReplayGain uses on MP3.
    rg_args=()
    if [ -n "${rg_track:-}" ]; then
        rg_args+=( -metadata "REPLAYGAIN_TRACK_GAIN=${rg_track}" )
        rg_args+=( -metadata "REPLAYGAIN_TRACK_PEAK=0.987654" )
    fi
    if [ -n "${rg_album:-}" ]; then
        rg_args+=( -metadata "REPLAYGAIN_ALBUM_GAIN=${rg_album}" )
        rg_args+=( -metadata "REPLAYGAIN_ALBUM_PEAK=0.987654" )
    fi

    # Skip work if already done
    if [ -f "$out" ]; then
        echo "[fetch-test-music] $title — already prepared, skipping"
        continue
    fi

    if [ ! -f "$raw" ]; then
        echo "[fetch-test-music] downloading $title..."
        curl -fsSL "$url" -o "$raw"
    fi

    # Tag + (optionally) embed cover art
    if [ "$embed" = "yes" ]; then
        cover="${DEST}/$(echo "$album" | tr ' ' '_')_cover.png"
        if [ ! -f "$cover" ]; then
            # Generate a 600x600 album cover with album title centered
            color=$(printf '#%02x%02x%02x' $((RANDOM % 96 + 32)) $((RANDOM % 96 + 32)) $((RANDOM % 96 + 32)))
            "${MAGICK}" -size 600x600 "xc:${color}" \
                -gravity center -fill white -font DejaVu-Sans-Bold -pointsize 56 \
                -annotate 0 "${album}" \
                "${cover}"
        fi
        ffmpeg -y -loglevel error -i "$raw" -i "$cover" \
            -map 0:a -map 1:v -c copy -id3v2_version 3 \
            -metadata title="$title" -metadata artist="$artist" \
            -metadata album="$album" -metadata track="$track" \
            -metadata date="$year" -metadata genre="$genre" \
            -metadata album_artist="$artist" \
            ${rg_args[@]+"${rg_args[@]}"} \
            -metadata:s:v title="Album cover" -metadata:s:v comment="Cover (front)" \
            -disposition:v attached_pic \
            "$out"
        echo "[fetch-test-music] $title — tagged with embedded cover ✓"
    else
        ffmpeg -y -loglevel error -i "$raw" -map 0:a -c copy \
            -metadata title="$title" -metadata artist="$artist" \
            -metadata album="$album" -metadata track="$track" \
            -metadata date="$year" -metadata genre="$genre" \
            -metadata album_artist="$artist" \
            ${rg_args[@]+"${rg_args[@]}"} \
            "$out"
        echo "[fetch-test-music] $title — tagged, no cover (intentional) ✓"
    fi
done

# Cleanup raw downloads (keep tagged outputs)
rm -f "${DEST}"/SoundHelix-Song-*.mp3

echo
echo "[fetch-test-music] done. Tracks in ${DEST}/:"
ls -1 "${DEST}/"*.mp3 | sed 's|^|    |'
echo
echo "Two albums: 'Velvet Den' (with cover art), 'Field Recordings' (no cover)."
echo "License: SoundHelix samples are CC-BY-SA — credit Tobias Bappert."

# Optional: push to AVD
if [ "${1:-}" = "--push" ]; then
    bash "${ROOT}/scripts/push-test-music.sh"
fi
