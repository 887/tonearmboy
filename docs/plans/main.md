# tonearm — main build plan

## Status: in progress

## Stack (locked)

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Audio:** androidx.media3 (ExoPlayer + MediaSession + MediaSessionService)
- **Data:** Room
- **Build front-end:** Google's [Android CLI](https://developer.android.com/tools/agents/android-cli) (`android` command, launched April 2026). Wraps Gradle, SDK, install, run. **No Android Studio.**
- **Build back-end:** Gradle (driven via the Android CLI). The repo includes a Gradle wrapper.
- **Unit tests:** Robolectric (JVM-only, zero device).
- **UI tests:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp) over ADB. Real phone (wifi-adb) or [Waydroid](https://waydro.id/). **Never** a full QEMU emulator.
- **Knowledge:** `android docs search` for live Android API guidance. [`android-skills-mcp`](https://github.com/skydoves/android-skills-mcp) for the official Android skills inside Claude Code.

---

## Phase 0 — prerequisites (one-time, on the host)

These run once per developer machine. Tracked here so we can verify our environment before agents go to work.

- [ ] **0.1** Install Google's Android CLI from <https://developer.android.com/tools/agents>. Verify with `android --version` and `android info`.
- [ ] **0.2** `android sdk install platforms/android-34 build-tools/34.0.0` — current stable platform + build-tools. Bump version when AGP requires.
- [ ] **0.3** Confirm Java 17 toolchain: `java -version` reports 17.x.
- [ ] **0.4** Register the `mobile` MCP server (already done in this user's `~/.claude.json`):
      `claude mcp add mobile --scope user -- npx -y @mobilenext/mobile-mcp@latest`
- [ ] **0.5** Register the official Android skills MCP for Claude Code:
      `claude mcp add android-skills --scope user -- npx -y android-skills-mcp`
- [ ] **0.6** Pair test target: either wifi-adb to the user's phone (`adb pair <ip>:<port>` then `adb connect <ip>:<port>`) **or** Waydroid running locally with ADB exposed. Verify with `adb devices`.

**Shipped:** _(not yet)_

---

## Phase A — scaffold

Goal: a buildable, sideload-able APK that boots into a blank Compose screen. Everything that follows assumes this exists.

- [ ] **A.0** Browse `android create list` and pick the closest official template. Default expectation: `empty-activity-agp-9` (or whatever the current Compose-with-AGP-9 template is named when this phase runs). Document the chosen template in the commit message.
- [ ] **A.1** `android create --name=tonearm --output=. <template>` from inside the repo root. Verify the generated layout: `app/`, `gradle/wrapper/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. The template should give us Compose, Material 3, AGP 9, JDK 17 toolchain, and a Hello-Compose `MainActivity`.
- [ ] **A.2** Add Media3 BOM and core deps to `app/build.gradle.kts`: `media3-exoplayer`, `media3-session`, `media3-ui`. Pin via the Media3 BOM. Versions resolve through the BOM, not hand-pinned.
- [ ] **A.3** `AndroidManifest.xml` adds: `READ_MEDIA_AUDIO` (Android 13+), `READ_EXTERNAL_STORAGE` (legacy, conditional with `android:maxSdkVersion`), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`. `minSdk = 26` (Android 8.0+). `compileSdk` and `targetSdk` set to current stable.
- [ ] **A.4** `.gitignore` covers `build/`, `.gradle/`, `local.properties`, `captures/`, `.cxx/`, `*.apk`, `*.aab`, `keystore.properties`, `*.keystore`, `*.jks`, plus a "no Android Studio droppings" block (`.idea/`, `*.iml`).
- [ ] **A.5** Build verification: `./gradlew assembleDebug` succeeds. APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **A.6** Install verification: `android run --apks=app/build/outputs/apk/debug/app-debug.apk` launches the placeholder activity on the connected target.

**Shipped:** _(not yet)_

---

## Phase B — core playback

Goal: ExoPlayer plays a known audio file. MediaSession is registered. Audio focus is honored.

- [ ] **B.1** `PlayerHolder` wraps an ExoPlayer instance. Singleton for now; replaced by DI in Phase H if/when scope warrants it.
- [ ] **B.2** `PlaybackService : MediaSessionService` with a stub notification (replaced for real in Phase E).
- [ ] **B.3** `MediaSession` wired to the Player, custom layout for play / pause / next / previous / seek.
- [ ] **B.4** `AudioFocusRequest` — duck on transient focus loss, pause on permanent loss, resume on focus regain.
- [ ] **B.5** Format smoke test on the real target: play one each of MP3, FLAC, OGG Vorbis, OPUS. ExoPlayer handles all natively but verify codec coverage end-to-end.

**Shipped:** _(not yet)_

---

## Phase C — library

Goal: scan the device's audio files into a queryable, searchable library cache.

- [ ] **C.1** `MediaStore.Audio` query — pull `_ID`, `TITLE`, `ARTIST`, `ALBUM`, `ALBUM_ARTIST`, `DURATION`, `TRACK`, `YEAR`, `GENRE`, `DATA`, `DATE_ADDED`. Permission flow for `READ_MEDIA_AUDIO`.
- [ ] **C.2** Room database — entities for Track, Album, Artist, Genre, Playlist, PlaylistTrack join. Migration baseline.
- [ ] **C.3** `LibraryRepository` — single source of truth, exposes Flows for grouped views. Initial scan + incremental rescan via `MediaStore` change observer.
- [ ] **C.4** Search — full-text over title / artist / album. FTS4 if Room supports cleanly, else `LIKE` fallback. Use `android docs search room fts` if uncertain on the current API.
- [ ] **C.5** Playlist support — Room-backed custom playlists, M3U / M3U8 import.

**Shipped:** _(not yet)_

---

## Phase D — UI

Goal: full Compose UI, navigable, themed.

- [ ] **D.1** Navigation: pick **Navigation 3** (the new compose-first nav from Google, late 2025) — there's an official Android skill `navigation-3-setup` that codifies the pattern; consult it before hand-rolling. Destinations: Home / Library / Search / Now Playing / Playlist Detail / Settings.
- [ ] **D.2** Library browse screens: Albums grid, Artists list, Tracks list, Genres list, Playlists list. Material 3 lists, sticky headers, fast-scroll.
- [ ] **D.3** Now Playing screen: album art, scrubber, transport controls, queue.
- [ ] **D.4** Mini-player persistent bottom sheet across all screens.
- [ ] **D.5** Theming: Material 3, dark mode default, dynamic color (Material You) on Android 12+.
- [ ] **D.6** Edge-to-edge: official Android skill `edge-to-edge-implementation` covers the current best practice. Consult.
- [ ] **D.7** Settings screen: theme, library scan controls, dangerous actions (clear cache, rescan).

**Shipped:** _(not yet)_

---

## Phase E — notification + lock-screen controls

Goal: full system integration. Player controllable from notification, lock screen, headset, Bluetooth.

- [ ] **E.1** `MediaStyle` notification with play / pause / next / prev / stop action buttons. Album art as large icon.
- [ ] **E.2** Lock-screen controls via MediaSession metadata + transport state.
- [ ] **E.3** Headset / Bluetooth media-button intents handled by MediaSession (mostly free with Media3 — verify, don't assume).
- [ ] **E.4** Foreground service lifecycle — start on play, stop and remove notification when nothing is queued, handle the user swiping the notification away.
- [ ] **E.5** Notification controls survive process death + restart.

**Shipped:** _(not yet)_

---

## Phase F — file deletion (the differentiator)

Goal: delete audio files from inside the player, with the system consent dialog and proper cache invalidation.

- [ ] **F.1** Single-track delete via `MediaStore.createDeleteRequest` (Android 11+). Our `minSdk` is 26, but `createDeleteRequest` is API 30+ — implement the pre-30 fallback (DELETE intent + manual SAF prompt).
- [ ] **F.2** Long-press track row → context menu with "Delete file…" entry → confirm dialog → system consent → deletion.
- [ ] **F.3** Multi-select mode → bulk delete via `createDeleteRequest` (it accepts a list of URIs).
- [ ] **F.4** Library cache invalidation: remove deleted tracks from Room in the same transaction as the deletion result. Update the now-playing queue if a deleted track was queued or playing.
- [ ] **F.5** Error states: permission denied, file in use, file already missing — non-scary toast / snackbar.

**Shipped:** _(not yet)_

---

## Phase G — test harness

Goal: Claude can drive the app end-to-end without an emulator. Local unit tests run on JVM. CI optional and out of scope for v1.

- [ ] **G.1** Robolectric set up for ViewModel + repository + parser tests. `./gradlew testDebugUnitTest` runs them on JVM, no device.
- [ ] **G.2** mobile-mcp install verified on the user's machine (Phase 0.4). Verify `mcp__mobile__*` tools appear in a fresh Claude Code session.
- [ ] **G.3** android-skills-mcp install verified on the user's machine (Phase 0.5). Verify the official Android skills are surfaced in Claude Code.
- [ ] **G.4** ADB sideload helper — small shell script `scripts/install.sh` that runs `./gradlew assembleDebug` then `android run --apks=app/build/outputs/apk/debug/app-debug.apk` to the first connected device.
- [ ] **G.5** First mobile-mcp flow (also written as a Maestro-compatible YAML for portability): launch app, grant audio permission, wait for library scan, browse to a track, tap play, assert "now playing" UI, assert notification visible.
- [ ] **G.6** Second flow: long-press a track, tap delete, accept system consent, assert track gone from library.

**Shipped:** _(not yet)_

---

## Phase H — extras (post-v1, prioritize after F + G land)

- [ ] **H.1** Gapless playback (Media3 supports natively; verify with cross-faded tracks).
- [ ] **H.2** ReplayGain (track + album modes, configurable preamp).
- [ ] **H.3** Sleep timer.
- [ ] **H.4** Equalizer — only via Android's system effects API. No custom DSP chain in v1.
- [ ] **H.5** Backup / export of playlists.
- [ ] **H.6** DI framework if the manual wiring hurts (Hilt or Koin).

**Shipped:** _(not yet)_

---

## Definition of done (v1)

- All sub-steps in Phases 0 through G ticked.
- App boots, plays MP3/FLAC/OGG/OPUS without crashes from a fresh install.
- Notification + lock-screen controls work end to end.
- Single + bulk file deletion works with proper consent dialogs and cache invalidation.
- Robolectric tests pass on JVM. mobile-mcp flows pass against a connected device or Waydroid instance.
- Plan marked `## Status: ✅ DONE`.

---

## Subagent rules

- Each subagent runs in a worktree.
- Each subagent prompt names the phase + sub-steps it owns.
- Subagents tick their checkboxes (`- [x]`) and add the commit ID to the phase header **in the same commit** that lands the work.
- Subagents do not modify `~/.claude/` files — those are out of repo.
- Subagents consult `android docs search <query>` before searching the open web for Android API questions, and consult the registered `android-skills` MCP / `mobile` MCP for guidance specific to those tools.
