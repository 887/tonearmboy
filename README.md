# tonearm

Modern Android music player. Built on Jetpack Compose + Media3. Targets feature parity with [Auxio](https://github.com/OxygenCobalt/Auxio), plus first-class file deletion from inside the player.

## Status

Pre-scaffold. See [`docs/plans/main.md`](docs/plans/main.md) for the phased build plan.

## Goals

- All the basics: library browse (artist / album / track / genre), search, queue, gapless playback, MediaSession, lock-screen / notification controls, headset-button intents.
- **Delete files directly from the player**, with the system consent dialog and library cache invalidation. Single and bulk.
- Modern stack: Kotlin + Compose + Media3 + Room. No legacy Android Views, no Java.
- **Built entirely from the CLI**, no Android Studio required.

## Non-goals

- Cloud library / streaming services.
- Lyrics fetching, last.fm scrobbling, advanced audio effects (maybe later, not in v1).
- Tablet-specific layouts (works on phone, scales to tablet later).

## Build

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(Pre-scaffold: the above doesn't work yet. See plan Phase A.)

## Test

See [`CLAUDE.md`](CLAUDE.md) for the full Claude-driven test loop. Short version:

- **Unit / data layer** — Robolectric, JVM-only, zero device.
- **UI / integration** — [`mobile-mcp`](https://github.com/mobile-next/mobile-mcp) over ADB, driving either a real phone (wifi-adb) or [Waydroid](https://waydro.id/). No QEMU emulator.

## License

MIT. See [`LICENSE`](LICENSE).
