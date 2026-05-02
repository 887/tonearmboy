#!/usr/bin/env bash
# build-release-apk.sh — build a release APK into release/ for distribution.
#
# Three actions, in order:
#   1. ./scripts/build-release-apk.sh                  # build APK into release/
#   2. ./scripts/build-release-apk.sh --gh-release     # build + upload to GitHub Releases
#   3. ./scripts/build-release-apk.sh --install        # build + adb install onto connected device
#
# Combine: --gh-release --install is fine.
#
# Output: release/tonearm-<version>-<sha7>.apk and a release/latest.apk symlink.
#
# Signing: this uses Gradle's debug keystore by default (good for personal
# sideload). For a real published release, set TONEARM_RELEASE_KEYSTORE +
# TONEARM_RELEASE_KEY_ALIAS + TONEARM_RELEASE_KEY_PASSWORD env vars and the
# script picks them up.

set -euo pipefail

ROOT="$(dirname "$(readlink -f "$0")")/.."
cd "${ROOT}"

# Toolchain — Android CLI's bundled JRE 21 lacks java.rmi, so use system JDK
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-26-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

PUSH_TO_GH=false
INSTALL_TO_DEVICE=false
for arg in "$@"; do
    case "$arg" in
        --gh-release|--github)   PUSH_TO_GH=true ;;
        --install|--adb-install) INSTALL_TO_DEVICE=true ;;
        --help|-h)
            sed -n '1,/^$/p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *) echo "unknown flag: $arg" >&2; exit 1 ;;
    esac
done

# Version + SHA tag for the artifact name
VERSION="$(awk -F'"' '/versionName/ {print $2; exit}' app/build.gradle.kts 2>/dev/null \
    || awk -F'=' '/versionName/ {print $2; exit}' app/build.gradle.kts \
    | tr -d ' "' || true)"
[ -z "${VERSION}" ] && VERSION="0.1.0-dev"
SHA7="$(git rev-parse --short=7 HEAD)"
TAG="${VERSION}-${SHA7}"

OUT_DIR="${ROOT}/release"
OUT_APK="${OUT_DIR}/tonearm-${TAG}.apk"
mkdir -p "${OUT_DIR}"

# Decide release vs debug build flavour
if [ -n "${TONEARM_RELEASE_KEYSTORE:-}" ]; then
    echo "[build-release-apk] release-signed build (keystore: ${TONEARM_RELEASE_KEYSTORE})"
    GRADLE_TASK="assembleRelease"
    BUILD_APK="app/build/outputs/apk/release/app-release.apk"
else
    echo "[build-release-apk] debug-signed build (set TONEARM_RELEASE_KEYSTORE for production signing)"
    GRADLE_TASK="assembleDebug"
    BUILD_APK="app/build/outputs/apk/debug/app-debug.apk"
fi

echo "[build-release-apk] running ./gradlew :app:${GRADLE_TASK}..."
./gradlew ":app:${GRADLE_TASK}" --console=plain >/dev/null

if [ ! -f "${BUILD_APK}" ]; then
    echo "[build-release-apk] expected APK at ${BUILD_APK} but it's missing" >&2
    exit 1
fi

cp "${BUILD_APK}" "${OUT_APK}"
ln -sf "$(basename "${OUT_APK}")" "${OUT_DIR}/latest.apk"
APK_SIZE="$(du -h "${OUT_APK}" | cut -f1)"
echo "[build-release-apk] ${OUT_APK} (${APK_SIZE})"
echo "[build-release-apk] symlink: ${OUT_DIR}/latest.apk"

# --gh-release: create or amend a tag-versioned GitHub release
if "${PUSH_TO_GH}"; then
    if ! command -v gh >/dev/null; then
        echo "[build-release-apk] gh CLI not found — install with 'pacman -S github-cli' or equivalent" >&2
        exit 1
    fi
    REL_TAG="v${TAG}"
    if gh release view "${REL_TAG}" >/dev/null 2>&1; then
        echo "[build-release-apk] release ${REL_TAG} already exists, uploading APK..."
        gh release upload "${REL_TAG}" "${OUT_APK}" --clobber
    else
        echo "[build-release-apk] creating release ${REL_TAG} on GitHub..."
        gh release create "${REL_TAG}" "${OUT_APK}" \
            --title "tonearm ${VERSION} (${SHA7})" \
            --notes "Auto-built APK from \`${SHA7}\`. Built ${BUILD_APK##*/}.

Install: \`adb install -r tonearm-${TAG}.apk\` or sideload directly."
    fi
    REL_URL="$(gh release view "${REL_TAG}" --json url -q .url)"
    echo "[build-release-apk] ${REL_URL}"
fi

# --install: push to a connected ADB device
if "${INSTALL_TO_DEVICE}"; then
    if ! command -v adb >/dev/null; then
        echo "[build-release-apk] adb not on PATH" >&2; exit 1
    fi
    if ! adb devices | grep -qE '\<device$'; then
        echo "[build-release-apk] no ADB device connected" >&2
        echo "[build-release-apk] start the AVD: scripts/start-avd.sh" >&2
        echo "[build-release-apk] or wifi-adb-pair your phone first" >&2
        exit 1
    fi
    echo "[build-release-apk] adb install -r ${OUT_APK}..."
    adb install -r "${OUT_APK}"
    echo "[build-release-apk] installed"
fi

echo "[build-release-apk] done"
