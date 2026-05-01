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
- **UI tests:** [mobile-mcp](https://github.com/mobile-next/mobile-mcp) over ADB. Current target: headless AVD `medium_phone` (Android 16 / API 36, RSS ~3.2 GB) — see Phase 0.6. Real phone via wifi-adb is the long-term home once notification + lock-screen behaviour starts mattering; Waydroid was declined (would need root).
- **Knowledge:** `android docs search` for live Android API guidance. [`android-skills-mcp`](https://github.com/skydoves/android-skills-mcp) for the official Android skills inside Claude Code.

---

## Phase 0 — prerequisites (one-time, on the host)

These run once per developer machine. Tracked here so we can verify our environment before agents go to work.

- [x] **0.1** Install Google's Android CLI: `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/android -o ~/.local/bin/android && chmod +x ~/.local/bin/android`. The launcher self-bootstraps a 78 MB runtime on first invocation, including a bundled JDK 21 at `~/.android/cli/bundles/<hash>/jre/`. Verify with `android --version`.
- [x] **0.2** `android sdk install platforms/android-34 build-tools/34.0.0` — installs to `~/Android/Sdk/`. Bump version when AGP requires.
- [x] **0.3** JDK 21 bundled by the Android CLI is sufficient for AGP 9. System Java only matters if a subagent invokes `./gradlew` directly without going through `android` — set `JAVA_HOME` to the bundled JRE in that case (see CLAUDE.md).
- [x] **0.4** `mobile` MCP server registered at **project scope** (`tonearm/.mcp.json`):
      `claude mcp add mobile --scope project -- npx -y @mobilenext/mobile-mcp@latest`
- [x] **0.5** `android-skills` MCP server registered at **project scope** (`tonearm/.mcp.json`):
      `claude mcp add android-skills --scope project -- npx -y android-skills-mcp`
- [x] **0.6** Test target: **headless AVD `medium_phone`** (Android 16, API 36, x86_64, google_apis_playstore). Created via `android emulator create --profile=medium_phone`. Started headlessly via `~/Android/Sdk/emulator/emulator -avd medium_phone -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect`. RSS ~3.2 GB. Visible to ADB as `emulator-5554`. Waydroid declined (would need root); wifi-adb deferred (phone testing later when notification + lock-screen behaviour matters).

**Shipped:** 0.1–0.6 in commit _(this commit)_.

---

## Phase A — scaffold

Goal: a buildable, sideload-able APK that boots into a blank Compose screen. Everything that follows assumes this exists.

- [x] **A.0** Browse `android create list` and pick the closest official template. Default expectation: `empty-activity-agp-9` (or whatever the current Compose-with-AGP-9 template is named when this phase runs). Document the chosen template in the commit message. — chose `empty-activity` (the only template currently shipped, tagged `compose,activity,agp-9`).
- [x] **A.1** `android create --name=tonearm --output=. <template>` from inside the repo root. Verify the generated layout: `app/`, `gradle/wrapper/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. The template should give us Compose, Material 3, AGP 9, JDK 17 toolchain, and a Hello-Compose `MainActivity`. — scaffolded into a temp dir then rsynced in with `--ignore-existing`; package renamed from `com.example.tonearm` to `com.eight87.tonearm` everywhere; theme renamed from `MyApplicationTheme` to `TonearmTheme`.
- [x] **A.2** Add Media3 BOM and core deps to `app/build.gradle.kts`: `media3-exoplayer`, `media3-session`, `media3-ui`. Pin via the Media3 BOM. Versions resolve through the BOM, not hand-pinned. — Media3 does not publish a Maven BOM (verified against Google Maven group-index.xml); equivalent behavior achieved via a single `media3 = "1.10.0"` key in `libs.versions.toml` shared by all three module entries, so a bump touches one line.
- [x] **A.3** `AndroidManifest.xml` adds: `READ_MEDIA_AUDIO` (Android 13+), `READ_EXTERNAL_STORAGE` (legacy, conditional with `android:maxSdkVersion`), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`. `minSdk = 26` (Android 8.0+). `compileSdk` and `targetSdk` set to current stable. — `minSdk=26`, `compileSdk=36`, `targetSdk=36` (template default; matches the API-36 system image on the running AVD).
- [x] **A.4** `.gitignore` covers `build/`, `.gradle/`, `local.properties`, `captures/`, `.cxx/`, `*.apk`, `*.aab`, `keystore.properties`, `*.keystore`, `*.jks`, plus a "no Android Studio droppings" block (`.idea/`, `*.iml`). — pre-existing repo `.gitignore` already covers everything; verified `local.properties` is excluded.
- [x] **A.5** Build verification: `./gradlew assembleDebug` succeeds. APK lands at `app/build/outputs/apk/debug/app-debug.apk`. — built with `JAVA_HOME=/usr/lib/jvm/java-26-openjdk` (the bundled JRE-21 lacks the `java.rmi` module that Gradle 9.1's Kotlin DSL classpath fingerprinter needs). Required SDK license stubs written to `~/Android/Sdk/licenses/` and `build-tools/36.0.0` installed via `android sdk install`. APK 16 MB.
- [x] **A.6** Install verification: `android run --apks=app/build/outputs/apk/debug/app-debug.apk` launches the placeholder activity on the connected target. — installed and launched on `emulator-5554`; `dumpsys window` confirms `mCurrentFocus=...com.eight87.tonearm/.MainActivity`; `android layout` returns `"text": "Hello Android!"` from the rendered Compose surface.

**Shipped:** A.0–A.6 in commit `b49571c`.

---

## Phase B — core playback

Goal: ExoPlayer plays a known audio file. MediaSession is registered. Audio focus is honored.

- [x] **B.1** `PlayerHolder` wraps an ExoPlayer instance. Singleton for now; replaced by DI in Phase H if/when scope warrants it. — `app/.../playback/PlayerHolder.kt`. Builds an `ExoPlayer` with `setAudioAttributes(..., handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`, and `setWakeMode(WAKE_MODE_LOCAL)`.
- [x] **B.2** `PlaybackService : MediaSessionService` with a stub notification (replaced for real in Phase E). — declared in the manifest with `foregroundServiceType="mediaPlayback"` and the `androidx.media3.session.MediaSessionService` intent filter. Media3's default `MediaStyle` notification is sufficient as the stub; Phase E will replace it.
- [x] **B.3** `MediaSession` wired to the Player, custom layout for play / pause / next / previous / seek. — built via `MediaSession.Builder(this, player)` in `PlaybackService.onCreate`. Media3's default button layout (play/pause/prev/next/seek) covers Phase B; custom layouts will land alongside the Phase E notification rework. `PlaybackController.connect(...)` exposes the canonical `MediaController` connection helper for `MainActivity`.
- [x] **B.4** `AudioFocusRequest` — duck on transient focus loss, pause on permanent loss, resume on focus regain. — delegated to ExoPlayer's built-in audio-focus handling via `setAudioAttributes(..., handleAudioFocus = true)` (verified against `kb://android/media/media3/session/background-playback`). This is the official Media3 pattern; manual `AudioFocusRequest` is not needed.
- [x] **B.5** Format smoke test on the real target: play one each of MP3, FLAC, OGG Vorbis, OPUS. ExoPlayer handles all natively but verify codec coverage end-to-end. — `scripts/smoke-test.sh` generates 1-second sine fixtures with `ffmpeg`, lands them in the app's internal data dir via `/data/local/tmp` + `run-as` (scoped storage on API 30+ blocks raw `file://` reads of `/sdcard/Music` from app processes), broadcasts `com.eight87.tonearm.action.SMOKE_PLAY` to drive playback through the service, and asserts `STATE_READY` from logcat. **All four codecs pass on `emulator-5554` (API 36)**. Fixtures stay local (not committed).

**Shipped:** B.1–B.5 in commit `70fb244`.

---

## Phase C — library

Goal: scan the device's audio files into a queryable, searchable library cache.

- [x] **C.1** `MediaStore.Audio` query — pull `_ID`, `TITLE`, `ARTIST`, `ALBUM`, `ALBUM_ARTIST`, `DURATION`, `TRACK`, `YEAR`, `GENRE`, `DATA`, `DATE_ADDED`. Permission flow for `READ_MEDIA_AUDIO`. — `data/mediastore/MediaStoreScanner.kt` runs the audio-table query and a separate `MediaStore.Audio.Genres.Members` walk (the per-row `GENRE` column has been deprecated since API 30); results are wrapped in `data/model/Track`. `data/mediastore/MediaStorePermissions.kt` exposes a Compose-friendly helper for the `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (API ≤ 32) permission name without owning a launcher.
- [x] **C.2** Room database — entities for Track, Album, Artist, Genre, Playlist, PlaylistTrack join. Migration baseline. — `data/db/` holds `TrackEntity`, `AlbumEntity`, `ArtistEntity`, `GenreEntity`, `PlaylistEntity`, `PlaylistTrackEntity` (with cascading `@ForeignKey`s) plus per-entity DAOs and a cross-entity `LibraryDao`. `LibraryDatabase` is `version = 1`, `exportSchema = true` (writes to `app/schemas/`), no `fallbackToDestructiveMigration`. Room 2.8.4 + KSP 2.3.7 added to the version catalog.
- [x] **C.3** `LibraryRepository` — single source of truth, exposes Flows for grouped views. Initial scan + incremental rescan via `MediaStore` change observer. — `data/LibraryRepository.kt` owns the scanner + Room DB. `observeTracks` / `observeAlbums` / `observeArtists` / `observeGenres` / `observePlaylists` return `Flow`s of domain models, the first of which kicks off the initial scan via `Dispatchers.IO`. A `ContentObserver` on `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` (descendants = true) fires through a `MutableSharedFlow` debounced 750ms; the worker computes a diff and applies it through `LibraryDao.applyDelta` in one transaction. `LibraryScanReceiver` provides a CLI smoke entry point (logs scan counts under the `tonearm` tag).
- [x] **C.4** Search — full-text over title / artist / album. FTS4 if Room supports cleanly, else `LIKE` fallback. Use `android docs search room fts` if uncertain on the current API. — `@Fts4(contentEntity = TrackEntity::class)` on `TrackFts` keeps an FTS shadow table over `(title, artist, album)`. `LibraryRepository.search()` builds a safe MATCH expression via `data/db/SearchExpressions.ftsMatch` (strips FTS metacharacters per token and appends a `*` to the last token for prefix search); when the user input contains nothing usable the repo falls back to `LIKE %query%` with metacharacters escaped. Both paths share the same `Flow<List<Track>>` signature.
- [x] **C.5** Playlist support — Room-backed custom playlists, M3U / M3U8 import. — `LibraryRepository` exposes `createPlaylist` / `addTrackToPlaylist` / `removeTrackFromPlaylist` / `reorderPlaylist` / `deletePlaylist`, all backed by `PlaylistDao` (positions stored as a `(playlistId, position)` composite key with a CASCADE foreign key to `tracks`). `data/playlist/M3UImporter.kt` parses M3U/M3U8: comments and `EXTINF` are tolerated, http(s) URLs are skipped, relative paths resolve against the playlist file's parent directory, charset is selected from the file extension (UTF-8 for `.m3u8`, ISO-8859-1 for `.m3u`). Match is done by `MediaStore.Audio.Media.DATA`; unresolved entries are returned in `M3UImportResult.skipped` for the caller to surface. Verified on `emulator-5554` with four MP3 fixtures via `scripts/library-smoke-test.sh`.

**Shipped:** C.1–C.5 across commits `c6cb939` (C.1+C.2), `e990cd1` (C.3+C.4), `2bcd495` (C.5 + tests + smoke).

---

## Phase D — UI

Goal: full Compose UI, navigable, themed.

- [x] **D.1** Navigation: pick **Navigation 3** (the new compose-first nav from Google, late 2025) — there's an official Android skill `navigation-3-setup` that codifies the pattern; consult it before hand-rolling. Destinations: Home / Library / Search / Now Playing / Playlist Detail / Settings. — adopted Navigation 3's `NavDisplay` + `entryProvider` DSL with serializable `NavKey`s in `ui/nav/Destinations.kt`. Per-tab back stacks live in `ui/nav/TonearmBackStack.kt` (lifted from the official skill's "Common UI" recipe). Bottom nav surfaces Home / Library / Search / Settings; `NowPlaying` and `PlaylistDetail` push onto the active tab. Library was kept top-level (the five sub-views are tabs of one screen, not five top-level destinations) — see D.2 note.
- [x] **D.2** Library browse screens: Albums grid, Artists list, Tracks list, Genres list, Playlists list. Material 3 lists, sticky headers, fast-scroll. — single `LibraryScreen` with `PrimaryTabRow` over the five views (`AlbumsGridScreen`, `ArtistsListScreen`, `TracksListScreen`, `GenresListScreen`, `PlaylistsListScreen`) in `ui/library/`. Albums use `LazyVerticalGrid(GridCells.Adaptive(140.dp))`. Artists, Tracks, Playlists use `LazyColumn` with stickyHeader letter sections. Tracks ships an alphabet-letter scroller column on the right; tapping a letter scrolls to that section. Playlists has a Material 3 `ExtendedFloatingActionButton` + create-playlist `AlertDialog`. All five views consume Flows directly off `LibraryRepository`.
- [x] **D.3** Now Playing screen: album art, scrubber, transport controls, queue. — `ui/playing/NowPlayingScreen.kt`. Album-art placeholder card, scrubber bound to `MediaController.currentPosition` / `duration` via the new `playback/PlaybackUiController.kt` (a UI-friendly wrapper around the Phase B `PlaybackController.connect` helper). Transport row is prev / seek-back-10 / play-pause / seek-forward-10 / next; queue button is a topbar action stub for Phase F. Connection lifecycle: the activity-scope `PlaybackUiController` calls `connect()` once on activity start; `NowPlayingScreen` calls it again (idempotent) so deep-link entry still works.
- [x] **D.4** Mini-player persistent bottom sheet across all screens. — `ui/playing/MiniPlayer.kt` rendered as a slot directly above the `NavigationBar` whenever `PlaybackUiState.hasMedia` is true and the current destination isn't `NowPlaying`. Tapping the row pushes `NowPlaying`; the play / pause and close icons act in place.
- [x] **D.5** Theming: Material 3, dark mode default, dynamic color (Material You) on Android 12+. — `theme/Theme.kt` now takes an explicit `darkTheme` parameter driven by the persisted `ThemePreference` (System / Light / Dark) stored via DataStore Preferences in `ui/settings/ThemePreference.kt`. Dynamic color is on by default for API 31+ (`dynamicDarkColorScheme(LocalContext.current)`); brand palette is the fallback.
- [x] **D.6** Edge-to-edge: official Android skill `edge-to-edge-implementation` covers the current best practice. Consult. — consulted via `mcp__android-skills__get_skill edge-to-edge`. Activity already calls `enableEdgeToEdge()`; every screen is now wrapped in a Material 3 `Scaffold` and respects `innerPadding`. Lists pass insets via `Modifier.padding(innerPadding)` on the outer container — the tab content lives inside the Scaffold so the `NavigationBar` and Mini-Player draw to the bottom of the screen and the system bars do not clip content.
- [x] **D.7** Settings screen: theme, library scan controls, dangerous actions (clear cache, rescan). — `ui/settings/SettingsScreen.kt`. Theme section is a 3-radio Row (System / Light / Dark) writing through `ThemePreferenceStore`. Library section: "Rescan now" + "Clear cache" both confirm via `AlertDialog` and route to `LibraryRepository.rescanNow()`. About section names version + MIT + repo URL.

**Verification:**
- `JAVA_HOME=… ANDROID_HOME=… ./gradlew testDebugUnitTest` passes. New JVM tests:
  - `app/src/test/java/com/eight87/tonearm/ui/search/SearchInputReducerTest.kt`
  - `app/src/test/java/com/eight87/tonearm/ui/settings/ThemePreferenceStoreTest.kt` (Robolectric, exercises real DataStore)
  - `app/src/test/java/com/eight87/tonearm/ui/nav/TonearmBackStackTest.kt`
- APK runs on `emulator-5554`; all seven screens render, mini-player floats over Library/Tracks while a track is queued, Now Playing reflects player state. Screenshots committed under `docs/screenshots/phase-d/`.
- `scripts/ui-smoke-test.sh` (new) installs the APK, navigates Library → Tracks → first row, asserts the "Now Playing" topbar title appears via `uiautomator dump`. Passes.

**Shipped:** _(this commit)_

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
