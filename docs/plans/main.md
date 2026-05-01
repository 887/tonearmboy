# tonearm — main build plan

## Status: in progress

## Constraints (locked)

- Kotlin + Jetpack Compose + Media3 + Room.
- Build via Gradle CLI only. No Android Studio.
- Test UI via [mobile-mcp](https://github.com/mobile-next/mobile-mcp) over ADB. Real phone (wifi-adb) or Waydroid. **Never** a full QEMU emulator.
- Single-module until that hurts. Then split.

---

## Phase A — scaffold

Goal: a buildable, sideload-able APK that boots into a blank Compose screen. Everything that follows assumes this exists.

- [ ] **A.1** Gradle wrapper (`gradlew`, `gradle/wrapper/`), `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties` (Kotlin/AGP versions pinned). Toolchain: JDK 17.
- [ ] **A.2** App module: `app/build.gradle.kts` with AGP, Kotlin, Compose BOM, Media3 BOM (versions pinned, dependency cache friendly). `compileSdk` and `targetSdk` set to current stable. `minSdk` no lower than 26 (Android 8.0 — covers >97% of devices and gives modern APIs).
- [ ] **A.3** `AndroidManifest.xml` with required permissions: `READ_MEDIA_AUDIO` (Android 13+), `READ_EXTERNAL_STORAGE` (legacy), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
- [ ] **A.4** `MainActivity` (Compose entry point) that renders a Material 3 Surface with placeholder text. App theme stub.
- [ ] **A.5** `.gitignore` covering Android build outputs (`build/`, `.gradle/`, `local.properties`, `captures/`, `.cxx/`, `*.apk`, `*.aab`, `keystore.properties`, `*.keystore`, `*.jks`).
- [ ] **A.6** `./gradlew assembleDebug` succeeds and produces `app/build/outputs/apk/debug/app-debug.apk`. Verify the APK installs via `adb install -r` on a real device or Waydroid.

**Shipped:** _(not yet)_

---

## Phase B — core playback

Goal: ExoPlayer plays a known audio file. MediaSession is registered. Audio focus is honored.

- [ ] **B.1** Wire ExoPlayer dependency, instantiate a `Player` in a singleton `PlayerHolder` (DI replaces this later).
- [ ] **B.2** `PlaybackService : MediaSessionService` with notification stub (replaced for real in Phase E).
- [ ] **B.3** `MediaSession` registered against the Player, with custom layout for play / pause / next / previous / seek.
- [ ] **B.4** `AudioFocusRequest` handling — duck on transient focus loss, pause on permanent loss, resume on focus regain.
- [ ] **B.5** Format smoke test: play one each of MP3, FLAC, OGG Vorbis, OPUS from a known location. Codec coverage matches Auxio (ExoPlayer handles all of these natively — verify, don't assume).

**Shipped:** _(not yet)_

---

## Phase C — library

Goal: scan the device's audio files into a queryable, searchable library cache.

- [ ] **C.1** `MediaStore.Audio` query implementation — pull `_ID`, `TITLE`, `ARTIST`, `ALBUM`, `ALBUM_ARTIST`, `DURATION`, `TRACK`, `YEAR`, `GENRE`, `DATA` (file path), `DATE_ADDED`. Permission flow for `READ_MEDIA_AUDIO`.
- [ ] **C.2** Room database — entities for Track, Album, Artist, Genre, Playlist, PlaylistTrack join. Migrations from v1 onward.
- [ ] **C.3** `LibraryRepository` — single source of truth, exposes Flows for grouped views. Initial scan + incremental rescan on `MediaStore` change observer.
- [ ] **C.4** Search — full-text search over title / artist / album. `MATCH` via FTS4 if Room supports it cleanly, else `LIKE` fallback.
- [ ] **C.5** Playlist support — Room-backed custom playlists, M3U / M3U8 import from filesystem.

**Shipped:** _(not yet)_

---

## Phase D — UI

Goal: full Compose UI, navigable, themed.

- [ ] **D.1** Navigation graph (`androidx.navigation.compose`) with destinations: Home / Library / Search / Now Playing / Playlist Detail / Settings.
- [ ] **D.2** Library browse screens: Albums grid, Artists list, Tracks list, Genres list, Playlists list. Material 3 lists, sticky headers, fast-scroll.
- [ ] **D.3** Now Playing screen: album art, scrubber, transport controls, queue.
- [ ] **D.4** Mini-player persistent bottom sheet across all screens.
- [ ] **D.5** Theming: Material 3, dark mode default, dynamic color (Material You) on Android 12+.
- [ ] **D.6** Settings screen: theme, library scan controls, dangerous actions (clear cache, rescan).

**Shipped:** _(not yet)_

---

## Phase E — notification + lock-screen controls

Goal: full system integration. Player controllable from notification, lock screen, headset, Bluetooth.

- [ ] **E.1** `MediaStyle` notification with play / pause / next / prev / stop action buttons. Album art as large icon.
- [ ] **E.2** Lock-screen controls via MediaSession metadata + transport state.
- [ ] **E.3** Headset / Bluetooth media-button intents handled by MediaSession (mostly free with Media3, verify).
- [ ] **E.4** Foreground service lifecycle — start on play, stop and remove notification when nothing is queued, handle the user swiping the notification away.
- [ ] **E.5** Notification controls survive process death + restart.

**Shipped:** _(not yet)_

---

## Phase F — file deletion (the differentiator)

Goal: delete audio files from inside the player, with the system consent dialog and proper cache invalidation.

- [ ] **F.1** Single-track delete via `MediaStore.createDeleteRequest` (Android 11+). Pre-Q fallback if `minSdk` ever moves below 30 (currently 26 — confirm whether the API needs a guard).
- [ ] **F.2** Long-press track row → context menu with "Delete file…" entry → confirm dialog → system consent → deletion.
- [ ] **F.3** Multi-select mode → bulk delete via the same `createDeleteRequest` API (it accepts a list of URIs).
- [ ] **F.4** Library cache invalidation: remove deleted tracks from Room in the same transaction as the deletion result. Update the now-playing queue if the deleted track was queued or playing.
- [ ] **F.5** Error states: permission denied, file in use by another process, file already missing — all surface a non-scary toast / snackbar.

**Shipped:** _(not yet)_

---

## Phase G — test harness

Goal: Claude can drive the app end-to-end without an emulator. Local unit tests run on JVM. CI optional and out of scope for v1.

- [ ] **G.1** Robolectric set up for ViewModel + repository + parser tests. `./gradlew testDebugUnitTest` runs them on JVM, no device.
- [ ] **G.2** mobile-mcp registration documented in `CLAUDE.md` (already there). Verify the install command works on the user's machine.
- [ ] **G.3** ADB sideload helper — small shell script `scripts/install.sh` that runs `./gradlew assembleDebug` then `adb install -r` to the first connected device.
- [ ] **G.4** First mobile-mcp flow, written as a Maestro-compatible YAML so it's portable: launch app, grant audio permission, wait for library scan, browse to a track, tap play, assert "now playing" UI appears, assert notification visible.
- [ ] **G.5** Second flow: long-press a track, tap delete, accept system consent, assert track gone from library.

**Shipped:** _(not yet)_

---

## Phase H — extras (post-v1, prioritize after F + G land)

- [ ] **H.1** Gapless playback (Media3 supports it, verify and test with cross-faded tracks).
- [ ] **H.2** ReplayGain support (track + album modes, configurable preamp).
- [ ] **H.3** Sleep timer.
- [ ] **H.4** Equalizer — only if Android's system effects API is enough; do not ship a custom DSP chain in v1.
- [ ] **H.5** Backup / export of playlists.

**Shipped:** _(not yet)_

---

## Definition of done (v1)

- All sub-steps in Phases A through G ticked.
- App boots, plays MP3/FLAC/OGG/OPUS without crashes from a fresh install.
- Notification + lock screen controls work end to end.
- Single + bulk file deletion works with proper consent dialogs and cache invalidation.
- Robolectric tests pass on JVM. mobile-mcp flows pass against a connected device or Waydroid instance.
- Plan marked `## Status: ✅ DONE`.
