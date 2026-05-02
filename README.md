# tonearm

Modern Android music player. Built on Jetpack Compose + Media3. Targets feature parity with [Auxio](https://github.com/OxygenCobalt/Auxio), plus first-class file deletion from inside the player.

## Status

Phases 0 → E shipped. See [`docs/plans/main.md`](docs/plans/main.md) for the phased build plan.

## Goals

- All the basics: library browse (artist / album / track / genre), search, queue, gapless playback, MediaSession, lock-screen / notification controls, headset-button intents.
- **Delete files directly from the player**, with the system consent dialog and library cache invalidation. Single and bulk.
- Modern stack: Kotlin + Compose + Media3 + Room. No legacy Android Views, no Java.
- **Built entirely from the CLI**, no Android Studio required.

## Non-goals

- Cloud library / streaming services.
- Lyrics fetching, last.fm scrobbling, advanced audio effects (maybe later, not in v1).
- Tablet-specific layouts (works on phone, scales to tablet later).

## Prerequisites (one-time, Linux)

```bash
# Android CLI 0.7+
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android \
  -o ~/.local/bin/android && chmod +x ~/.local/bin/android
android --version          # self-bootstraps the runtime + bundled JDK 21

# SDK packages
android sdk install platforms/android-34 build-tools/34.0.0

# Test target — headless AVD
android emulator create --profile=medium_phone

# Mirror utility (optional but recommended for visual QA)
sudo pacman -S scrcpy      # Arch / Manjaro
# (or your distro's equivalent — Debian/Ubuntu: `sudo apt install scrcpy`)
```

The Android CLI also fetches the emulator binary and a system image on first
`android emulator create`. Expect ~600 MB of downloads on a fresh box.

## Run the AVD + scrcpy

```bash
scripts/start-avd.sh             # boot AVD if not running, then attach scrcpy
scripts/start-avd.sh --no-mirror # AVD only, no scrcpy window
scripts/start-avd.sh --kill      # stop both
```

The AVD boots headless (`-no-window -no-audio -no-snapshot`) for ~3 GB resident
RAM. `scrcpy` then mirrors the display to a host window (Wayland / X11) without
restarting the emulator. Once running, `adb devices` shows `emulator-5554`.

## Build + install

```bash
# Direct gradlew calls need both env vars (see CLAUDE.md for the why):
export JAVA_HOME=/usr/lib/jvm/java-26-openjdk
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug
android run --apks=app/build/outputs/apk/debug/app-debug.apk --device=emulator-5554
```

Or via the Android CLI directly (which handles the toolchain internally):

```bash
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

## Populate the library + smoke-test

```bash
scripts/library-smoke-test.sh    # pushes 4 tagged-MP3 fixtures, triggers MediaStore rescan
scripts/playback-smoke-test.sh   # exercises Phase E (notification, lock-screen, headset, foreground, process death)
scripts/ui-smoke-test.sh         # exercises Phase D (top tabs, settings sub-pages, sort sheet, overflow menus)
```

## Test

See [`CLAUDE.md`](CLAUDE.md) for the full Claude-driven test loop.

- **Unit / data layer** — Robolectric, JVM-only, zero device.
- **UI / integration** — [`mobile-mcp`](https://github.com/mobile-next/mobile-mcp) over ADB driving the headless AVD (or a real phone via wifi-adb).

## License

MIT. See [`LICENSE`](LICENSE).
