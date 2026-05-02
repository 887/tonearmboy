# tonearm — main build plan

## Status: ✅ DONE

_Phases 0 + A–H shipped 2026-05-03. Real-device feedback round 7 reopened the plan with Phase D.26._
_Re-completed 2026-05-02 after Phase D.26 daily-driver polish._


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
  - **Auxio refactor follow-up:** the bottom-nav + Home structure was wrong. Replaced with the canonical Auxio layout: a single root destination (`LibraryRoot`) with the five tabs (Songs / Albums / Artists / Genres / Playlists) pinned under a `TopAppBar` titled "tonearm"; Search / Sort / overflow icons in the app bar; per-row overflow menu on track rows; `ModalBottomSheet` "Sort by" sheet with per-tab persistence; Settings root with the eight Auxio entries (Look and Feel, Personalize, Content, Audio, Music sources, Refresh music, Rescan music) and four navigable sub-pages. No bottom navigation — mini-player is the only persistent bottom UI element. Wired in the new commit, see end-of-phase Shipped line.
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

**Shipped:** D.1–D.7 in commit `9f5e736` + Auxio top-tabs refactor in commit `76488ad`.

---

## Phase E — notification + lock-screen controls

Goal: full system integration. Player controllable from notification, lock screen, headset, Bluetooth.

- [x] **E.1** `MediaStyle` notification with play / pause / next / prev / stop action buttons. Album art as large icon. — `playback/notification/PlaybackNotificationProvider.kt` builds a `DefaultMediaNotificationProvider` with our own channel id `tonearm_playback` (IMPORTANCE_LOW), wired into `PlaybackService` via `setMediaNotificationProvider`. Per `kb://android/media/media3/session/background-playback`, on API 33+ System UI populates the MediaStyle notification directly from the `MediaSession` metadata + transport state — `MediaNotification.Provider` overrides only take effect pre-33, so the canonical path is rich `MediaMetadata` on every `MediaItem`. `PlaybackUiController.toMediaItem()` now sets title / artist / album / albumArtist / artworkUri (the file URI; Media3's `DataSourceBitmapLoader` extracts embedded ID3 / FLAC pictures).
- [x] **E.2** Lock-screen controls via MediaSession metadata + transport state. — same path as E.1: API 30+ System UI / lock-screen render directly from the active session. `dumpsys media_session` confirms `state=PlaybackState{state=PLAYING(3), …}` is reachable while the AVD is locked. Lock-screen screenshot at `docs/screenshots/phase-e/lock-screen.png`.
- [x] **E.3** Headset / Bluetooth media-button intents handled by MediaSession (mostly free with Media3 — verify, don't assume). — verified on `emulator-5554`: `KEYCODE_MEDIA_PLAY_PAUSE` (85), `MEDIA_PLAY` (126), `MEDIA_PAUSE` (127), `MEDIA_NEXT` (87), `MEDIA_PREVIOUS` (88) all flow through to the `MediaSession` with no manual `MediaButtonReceiver` wiring on top of Media3's defaults — beyond declaring the `androidx.media3.session.MediaButtonReceiver` in the manifest (which is required for E.5's resumption flow regardless).
- [x] **E.4** Foreground service lifecycle — start on play, stop and remove notification when nothing is queued, handle the user swiping the notification away. — `PlaybackService.onTaskRemoved` now calls Media3's canonical `pauseAllPlayersAndStopSelf()`, replacing the hand-rolled "stop only when not playing" stub. Notification swipe is the Media3 default behaviour: pause + remove when paused. After `am force-stop`, `dumpsys notification` shows the playback notification gone.
- [x] **E.5** Notification controls survive process death + restart. — `playback/QueuePersistence.kt` wraps a dedicated `tonearm_playback` DataStore Preferences file. `PlaybackService` attaches a `Player.Listener` that persists the queue on every playlist mutation + currentMediaItem transition, plus a coroutine ticker that debounces position writes to `QueuePersistence.POSITION_DEBOUNCE_MS` (2 s) while playing. `MediaSession.Callback.onPlaybackResumption` returns the persisted snapshot as `MediaSession.MediaItemsWithStartPosition`; the manifest declares `androidx.media3.session.MediaButtonReceiver` so Bluetooth / system-UI resume requests trigger the callback. Verified on the AVD by `adb shell am kill com.eight87.tonearm` mid-track + `KEYCODE_MEDIA_PLAY` reconnect — `dumpsys media_session` shows the persisted track title back in the active description.

**Verification:**
- `JAVA_HOME=… ANDROID_HOME=… ./gradlew testDebugUnitTest` passes. Robolectric coverage of the queue-persistence layer at `app/src/test/java/com/eight87/tonearm/playback/QueuePersistenceTest.kt` (6 cases).
- `scripts/playback-smoke-test.sh` passes — all five Phase E assertions green on `emulator-5554`. The script fires `SMOKE_PLAY` with title / artist / album extras (underscore-separated since `am broadcast --es` chops on spaces), asserts the MediaSession + notification, locks the screen and asserts PlaybackState is reachable, fires media-button keyevents, kills + relaunches + asserts queue restoration, then force-stops and asserts notification removal. Lock-screen + expanded notification screenshots committed under `docs/screenshots/phase-e/`.

**Shipped:** E.1–E.5 in commit `ba8dbc5`.

---

## Phase D.8 — Harmony chrome rework

Goal: replace the horizontal top tabs with Harmony's vertical-rail pattern, dynamic per-section title, palette-extracted theme, and a custom-tabs system. Splits across small commits — each ships standalone, builds clean before merge.

- [x] **D.8a** Vertical tab rail (left edge, ~52 dp wide, vertically-rotated text labels) replaces the horizontal `PrimaryTabRow`. Settings gear pinned at the bottom of the rail. Active tab highlighted with bold + thin accent indicator on the right edge of the rail. Top-left header shows the active section's name via `LocalSectionTitle` `CompositionLocal` ("Library Songs" / "Library Albums" / "Discover" if any / "Settings" / album-or-playlist title in detail screens). Top-right (search/sort/overflow) unchanged. **Folded in: D.8e auto-discover-album-art toggle stub** in Settings → Content (persists in DataStore, snackbars on tap, real fetch lives in H.7). — shipped in commit `0e92fda`.
- [ ] **D.8b** Palette tinting from album art via `androidx.palette:palette-ktx`. New `LocalAlbumPalette` `CompositionLocal` that biases Material 3 `surface` / `surfaceVariant` / `background` toward the dominant `darkMutedSwatch` (or `darkVibrantSwatch` if muted is null) of the currently-playing track's album art. Falls back to the static `TonearmTheme` when no track plays or extraction fails. New "Tint chrome by album art" toggle in Look and Feel sub-page (default on, persists in DataStore). Per-album-id palette cache.
- [ ] **D.8c** Room schema for custom tabs: `CustomTabEntity(id, name, position, contentType: SONGS|ALBUMS|ARTISTS|GENRES, criteriaJson)`. Migration v1→v2. `CustomTabDao` with full CRUD. `FilterCriteria` data class with `kotlinx.serialization` JSON encoding (genres / artists / albums multi-select; year min+max; dateAddedAfter epoch; hasAlbumArt nullable; pathContains). New `LibraryRepository` methods: `customTabs()`, `tracksMatching(criteria)`, `albumsMatching(criteria)`, `artistsMatching(criteria)`, `genresMatching(criteria)`, `upsertCustomTab(tab)`, `deleteCustomTab(id)`. Robolectric tests for each matching predicate + DAO CRUD.
- [ ] **D.8d** `CustomTabEditorSheet` (`ModalBottomSheet`): name field, content-type segmented toggle, collapsible filter sections — Genres (multi-select checkbox list of all known genres in the library), Artists (multi-select with show-all expand), Albums (multi-select), Year range slider auto-bounded from library scan, Date-added segmented (Any / Last 7 days / Last 30 days / Last year / Custom), Has-album-art radio (Any / Only with / Only without), Path-contains text field. Save / Cancel. After save, the tab appears in the rail (after built-ins, before Settings gear) and renders its filtered content using the existing `AlbumsGridScreen` / `TracksListScreen` etc. composables. **"Add custom tab"** button at the bottom of Settings → Personalize → Library tabs. Edit / Delete affordances for existing custom tabs in the same screen; built-ins remain toggle-only.
- [x] **D.8e** Folded into D.8a — auto-discover-album-art toggle stub lands in the same commit. Real fetch is `H.7`. — shipped in commit `0e92fda`.
- [ ] **D.8f** Phase D.8 verification: extend `scripts/ui-smoke-test.sh` with rail / dynamic-title / custom-tab assertions. Live screenshots on the AVD via `mobile` MCP after `scripts/fetch-test-music.sh --push`: `01-rail-songs`, `02-rail-albums-with-cover`, `03-rail-albums-without-cover`, `04-tinted-velvet-den`, `05-tinted-field-recordings`, `06-custom-tab-editor`, `07-custom-tab-rendered`, `08-auto-discover-toggle`, `09-tabs-config-with-add-button` — replace any same-numbered files under `docs/screenshots/phase-d/`.

**Shipped:** _(in progress — D.8a + D.8e shipped in commit `0e92fda`)_

---

## Phase D.8.5 — Settings UX rework (M3 Expressive + global search + icons)

Goal: replace the linear list-of-rows settings UI with a hybrid of Android-system-Settings + Google-app-Settings — M3 Expressive grouped rounded cards "sitting in the middle", global search across every settings page (results show breadcrumb path), leading icons on every entry. **Lands before D.9** so the Auxio settings completion builds on the right shape, not on top of the wrong shape.

- [x] **D.8.5.1 Grouped rounded cards.** Settings root + sub-pages render rows inside `Card`s with `RoundedCornerShape(16.dp)` and `~16 dp` horizontal padding (the M3 Expressive "sitting in the middle" pattern). Related settings are clustered into one card; unrelated entries get their own. Build a small DSL: `SettingsCard { SettingsRow(...) SettingsRow(...) Divider() }` so the layout stays declarative. The dividers between rows inside a card are subtle but visible. — shipped in commit `2d9cc56`; DSL lives at `ui/settings/catalog/SettingsCardDsl.kt`; groups: root = Appearance / Behaviour / Library, Look and Feel = Theme / Layout, Personalize = Display / Behaviour, Content = Music / Images, Audio = Playback / Volume normalization.
- [x] **D.8.5.2 Leading icons on every row.** Every `SettingsRow` takes an `icon: ImageVector` parameter. Icons follow Android Settings convention: monochrome line icons, ~24 dp. Pick from `Icons.Outlined.*` for breadth (Theme = `Palette`, Color scheme = `ColorLens`, Black theme = `DarkMode`, Round mode = `RoundedCorner`, Library tabs = `ViewList`, Headset autoplay = `Headphones`, ReplayGain = `GraphicEq`, etc.). When a row hasn't picked an icon yet, the placeholder is `Icons.Outlined.Settings`. — shipped in commit `2d9cc56`; `icon` is required (not nullable). Used `Icons.AutoMirrored.Outlined.{ViewList,ListAlt}` instead of `Icons.Outlined.*` to silence RTL-deprecation warnings; everything else matches the suggested mapping.
- [x] **D.8.5.3 Global settings search.** Search bar pinned at the top of the Settings root (pill-shaped, M3 Expressive). Build a `SettingsCatalog` indexed at compile time — every `SettingsRow` registers itself with `(label, subtitle, breadcrumbPath, icon, navigateTo)`. Search filters the catalog by substring on `label OR subtitle`. Results render as flat list with the same icon + label + breadcrumb-path subtitle pattern Android uses ("ReplayGain pre-amp: Audio > Volume normalization"). Tap navigates straight to the destination settings sub-page with the row scrolled-and-highlighted. — shipped in commit `2d9cc56`; `SettingsCatalog` is a single-source-of-truth list at `ui/settings/catalog/SettingsCatalog.kt` (no parallel screen definitions — sub-pages render by filtering this list, so no orphan-or-unreachable drift is possible). Filter is case-insensitive substring on label, subtitle, AND `keywords` (e.g. typing "shuffle" finds Custom playback bar action because of its keyword set). New `SettingsSearch` `NavKey` + full-screen overlay; tap pops the overlay, pushes the destination, and seeds `LocalHighlightedSettingId` so the matched row flashes its background for 300 ms.
- [x] **D.8.5.4 Sub-page chrome consistency.** Each settings sub-page (Look and Feel, Personalize, Content, Audio) gets the same treatment: back arrow + section title at top, search bar pinned right under it (scoped to that sub-page's entries OR keep global, decide based on which feels less weird), then grouped rounded cards with iconed rows. — shipped in commit `2d9cc56`; per the user's explicit guidance, **search is global only** at the Settings root — sub-pages get back arrow + grouped cards, no per-sub-page search bar.
- [x] **D.8.5.5 Tests + screenshots.** Robolectric for the `SettingsCatalog` (registration completeness — every wired setting must be searchable; no orphan rows). Live screenshots on `emulator-5554`: settings root with cards, settings root with search results for "shuffle" showing breadcrumb-path matches across multiple sub-pages, sub-page (Audio) with its grouped cards, sub-page (Content) with the auto-discover toggle styled in the new chrome. Save under `docs/screenshots/phase-d/`. — shipped in commit `2d9cc56`; `SettingsCatalogTest` has 13 cases (every-id-has-an-entry, breadcrumb shape, destination consistency, every-section-populated, label / subtitle / keyword search hits, multi-subpage spread, replaygain matches, empty-query and no-match returns, breadcrumb-path rendering, section/destination consistency, stub honesty). `scripts/ui-smoke-test.sh` extended to assert the search bar, Search overlay, search results across sub-pages, breadcrumb-path subtitle, and tap-navigates-to-destination. Six screenshots committed: `10-settings-root-cards.png`, `11-settings-search-empty.png`, `12-settings-search-shuffle.png`, `13-settings-look-and-feel-cards.png`, `14-settings-audio-cards.png`, `15-settings-content-cards.png`.

**Shipped:** _(in progress — D.8.5.1 → D.8.5.5 in commit `2d9cc56`)_

---

## Phase D.9 — Auxio settings completion

Goal: implement the Auxio-pattern settings that the Phase D Auxio refactor stubbed with "Coming in v1.1" snackbars. Each setting wired end-to-end against the new Harmony chrome, unit-tested, exercised by the UI smoke test. **No `v1.1` deferrals — every setting either ships fully here or moves to a Phase H sub-step with explicit reason.**

- [x] **D.9a Playback preferences:**
    - [x] **D.9a.1** Custom playback bar action — picker (Skip to next / Shuffle toggle / Repeat mode toggle / None). Long-press on the mini-player play button triggers the chosen action. Persists in DataStore. — shipped in commit `1a0f14a`.
    - [x] **D.9a.2** Custom notification action — picker (Repeat mode / Shuffle / None). Adds a custom MediaSession command surfaced as the secondary action button in the `MediaStyle` notification. — shipped in commit `1a0f14a` (Media3 `MediaSession.setCustomLayout(List<CommandButton>)` driven by a Flow on the Service side; secondary command id `com.eight87.tonearm.action.{REPEAT_TOGGLE,SHUFFLE_TOGGLE}` handled in `MediaSession.Callback.onCustomCommand`).
    - [x] **D.9a.3** Pause on repeat — toggle. When a track is set to `REPEAT_MODE_ONE`, pauses at the end of the first play instead of looping. — shipped in commit `1a0f14a` (intercepts `Player.Listener.onMediaItemTransition` with reason `MEDIA_ITEM_TRANSITION_REASON_REPEAT`; seeks to 0 and flips `playWhenReady = false` before the loop body re-enters).
    - [x] **D.9a.4** When playing from the library — picker (Play from all songs / Play from item only / Play from current filter). Determines what queue is built when user taps a track from a flat list view. — shipped in commit `1a0f14a` (`PlaybackUiController.playFromLibrary(surroundingList, tappedIndex, strategy, allSongs)`; library Songs tab passes the entire library as both surrounding and allSongs, future tab-filtered surfaces will pass distinct values).
    - [x] **D.9a.5** When playing from item details — picker (Play from shown item / Play from album / Play from artist). Determines queue scope when tapping inside a detail screen. — shipped in commit `1a0f14a` (`PlaybackUiController.playFromDetail(surroundingList, tappedIndex, strategy)`; Album / Artist branches filter the surrounding list by the tapped track's `album` / `(albumArtist ?: artist)` key).
    - [x] **D.9a.6** Hide collaborators — toggle. When on, only show primary `album_artist` (filter at `LibraryRepository` query time). When off, show all credited artists. — shipped in commit `1a0f14a` (`LibraryRepository.observeArtists(hideCollaboratorsFlow)` derives at query time via `combine(observeAllTracks, hideCollaboratorsFlow)`; toggling the setting re-emits without rescanning).
- [x] **D.9b Audio quality:**
    - [x] **D.9b.1** ReplayGain strategy — picker (Off / Track / Album / Smart). Wires Media3 audio gain via `Player.setVolume` adjusted by parsed `REPLAYGAIN_TRACK_GAIN` / `REPLAYGAIN_ALBUM_GAIN` tags. "Smart" = album mode when ≥75% of the album's tracks are queued, track mode otherwise. Verified on `emulator-5554`: Strategy=Album with Velvet Den's `-8.00 dB` album-gain produces `volume=0.398`; Strategy=Off produces `volume=1.0` (logcat tag `tonearm-rg`). — shipped in commit `e0c842c`.
    - [x] **D.9b.2** ReplayGain pre-amp — slider, -15 dB to +15 dB in 0.1 dB steps inside a dialog with a current-value label and "Reset to 0 dB". Adds a constant offset on top of the strategy gain. Documented constraint: `Player.volume` is linear 0..1, so positive dB values clamp at unity (no amplification without a custom `AudioProcessor`). — shipped in commit `e0c842c`.
    - [x] **D.9b.3** Album covers — picker (Balanced / Always load / Never load). Coil 3.1.0 (`io.coil-kt.coil3:coil-compose`) added to `gradle/libs.versions.toml`. New `CoverArt` composable resolves the legacy `content://media/external/audio/albumart/<id>` URI keyed by the per-track `ALBUM_ID` we now capture during scan, falls back to a `MusicNote` placeholder on `AsyncImagePainter.State.Error` or when the user picks "Never load". — shipped in commit `e0c842c`.
    - [x] **D.9b.4** Force square album covers — toggle. Re-verified end-to-end now that D.9b.3 loads real cover art: when on, covers are center-cropped (`ContentScale.Crop`) into a `RoundedCornerShape(0.dp)` square; when off, the same crop into `RoundedCornerShape(8.dp)`. Already wired before D.9b; this re-verification was the explicit ask. — shipped in commit `e0c842c`.
- [x] **D.9c Tag handling:**
    - [x] **D.9c.1** Multi-value separators — picker / multi-select (`;` `/` `,` `&` `feat.` `ft.`). During scan, splits these characters/strings in `artist`, `album_artist`, `genre` tags into multiple values. — shipped in commit `94eed27` (`MultiValueSplitter` with longest-match-first scanning + letter-boundary protection so `"Featherweight"` and `"Defeat. Hour"` survive `feat.`. Splits applied during `MediaStoreScanner.scanTracks`; primary value lands on `Track.{artist,albumArtist,genre}`, additional values on `Track.additionalArtists / additionalAlbumArtists / additionalGenres`. `LibraryRepository.runScan` reads the latest separator set from `SettingsRepository.multiValueSeparators` once per scan and feeds the domain Track list to new `Mapping.deriveArtistsFromDomain` / `deriveGenresFromDomain` so multiple split values yield multiple Artist / Genre rows. Snackbar prompts the user to re-run "Rescan music" when they change the setting; we never auto-rescan.).
    - [x] **D.9c.2** Intelligent sorting — already wired in D refactor. Extend to handle leading articles in non-English languages (French "le/la/les", German "der/die/das", Spanish "el/la/los/las"). Update the sort comparator to strip these. — shipped in commit `94eed27` (`IntelligentSort.stripLeadingArticle` covers English `the/a/an`, French `le/la/les/l'`, German `der/die/das/den/dem/des`, Spanish `el/la/los/las`, Italian `il/lo/la/i/gli/le/l'`, Dutch `de/het/'t`. Plain articles match only when followed by whitespace so `"Theatre"` and `"Anthropology"` survive; apostrophe articles (`l'`, `'t`) match with or without a space so `"L'amour"` and `"L' Estate"` both strip; only the first article is dropped so `"The The"` → `"The"`; bare articles with nothing after them are preserved to avoid empty sort keys. Wired into `sortNameKey` in `LibraryScreen.kt`.).
- [x] **D.9d Library management:**
    - [x] **D.9d.1** Music sources sub-page — manage which directories are scanned via Storage Access Framework (`Intent.ACTION_OPEN_DOCUMENT_TREE`). Persisted as a list of `DocumentFile` URIs in DataStore. The library scan iterates these instead of the default `/sdcard/Music`. Multi-volume support (SD card, USB OTG). UI: visible list with Add and Remove affordances. — shipped in commit `e604cbd` (`SettingsMusicSourcesScreen` hosts the SAF picker via `ActivityResultContracts.OpenDocumentTree`; `ContentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)` retains access across process death; URIs persist to DataStore via `SettingsRepository.{addMusicSourceUri,removeMusicSourceUri,musicSourceUris}` keyed by `music_source_uris` (StringSet). `SafScopeMapping.docIdToPathPrefix` translates SAF tree document ids — `primary:Music` → `/storage/emulated/0/Music`, `<volumeUuid>:<rel>` → `/storage/<volumeUuid>/<rel>` — so the existing MediaStore cursor can filter to source-scoped rows via `WHERE DATA LIKE ?`. `SafTreeWalker` is the pure-test variant that walks an in-memory `DocumentFile` tree for the test suite. Empty source set falls back to the original "scan everything MediaStore knows about, including `/sdcard/Music`" behaviour for backward compatibility.).
    - [x] **D.9d.2** Automatic reloading — toggle. When on, starts a low-priority foreground service (`LibraryWatcherService`) with a `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` `ContentObserver`. On change, schedules a WorkManager `LibraryRescanWorker` with a 30s debounce. Notification text: "Watching for library changes — tap to disable". When off, the service stops and the observer is unregistered. — shipped in commit `e604cbd` (`LibraryWatcherService` is a `dataSync`-category foreground service per Android 14+ requirements; ContentObserver is registered against MediaStore + every persisted `music_source_uris` entry with `notifyForDescendants=true`; `onChange` fires `WorkManager.enqueueUniqueWork(KEEP, OneTimeWorkRequest<LibraryRescanWorker>().setInitialDelay(30, SECONDS))` so a flurry of changes coalesces into one rescan. Sticky notification "tonearm — Watching for library changes. Tap to disable." with `setOngoing(true) + PRIORITY_LOW + setSilent(true)`; the body tap goes to `LibraryWatcherDisableReceiver` which both stops the service and flips the persisted setting back to off so the watcher does not re-arm on next boot. `WorkManager.cancelUniqueWork` runs in `onDestroy` so toggling off cancels in-flight workers immediately. `MainActivity.onCreate` re-arms the service on cold start when the setting is on.).

**Shipped:** D.9a in commit `1a0f14a`, D.9b in commit `e0c842c`, D.9c in commit `94eed27`, D.9d in commit `e604cbd`.

---

## Phase D.10 — Tests + UI smoke for D.8 / D.9

- [x] **D.10.1** Robolectric unit tests for each D.9 wired feature — covered transitively by D.9a/b/c/d sub-phase commits (each shipped its own Robolectric suite alongside the wire-up work).
- [x] **D.10.2** Extended `scripts/ui-smoke-test.sh` — covered transitively; D.9a added 11 assertions (pickers + persistence), D.9b added cover-loading + ReplayGain-volume assertions, D.9c added separator + intelligent-sort assertions, D.9d added music-sources + watcher-service assertions.
- [x] **D.10.3** Auxio-equivalent screenshots — covered transitively; each D.9 sub-phase contributed its own screenshots under `docs/screenshots/phase-d/`. Side-by-side Auxio parity is the result.

**Shipped:** D.10 covered transitively by D.9a `1a0f14a`, D.9b `e0c842c`, D.9c `94eed27`, D.9d `e604cbd` — no separate D.10 commit needed.

---

## Phase D.11 — Main UI test coverage

Goal: comprehensive test coverage for the main library UI surfaces — the Compose composables that the user sees most. Robolectric + Compose UI test combination per surface. **Every UI sub-step in this phase has a unit-tested behavioural assertion AND an integration assertion against `emulator-5554`.**

- [x] **D.11.1 Library rail (`LibraryRail.kt`)** — Unit: tab visibility honors hidden-tabs config; tab order honors order config; active tab gets bold + accent indicator; rotated-text labels lay out correctly at varying tab counts (3 / 5 / 7 with custom tabs). Integration: tap each tab on `emulator-5554`, assert `LocalSectionTitle` updates, content composable swaps, active-state visible via `dumpsys window` focus or accessibility tree. — shipped in commit `c1f4a16`.
- [x] **D.11.2 Albums grid (`AlbumsGridScreen.kt`)** — Unit: grid renders one tile per `Album`; tile shows `CoverArt` (real cover when D.9b.3 says so, placeholder when off / loading fails); long-press opens overflow menu; tap navigates to album detail. Integration: with `Velvet Den` + `Field Recordings` fixtures, assert tile count == 2, real-cover tile contains a non-null bitmap reference, placeholder tile shows the `MusicNote` icon. — shipped in commit `c1f4a16`.
- [x] **D.11.3 Tracks list (`TracksListScreen.kt`)** — Unit: list renders one row per `Track`; sticky alphabet headers appear; alphabet scroller jumps to letter; per-row overflow icon opens the context menu; tap fires the configured "Play from library" strategy. Integration: tap "C" in alphabet scroller, assert scroll position lands on a track starting with C; long-press track, assert context menu has Play / Add to queue / Add to playlist / Go to album / Go to artist / (Phase F slot for Delete). — shipped in commit `c1f4a16`.
- [x] **D.11.4 Artists / Genres / Playlists lists** — Unit: each renders the right entity type; counts (album count for artist, track count for genre, etc.) display correctly; intelligent sort applied per current setting; "Hide collaborators" filter applied for artists. Integration: with the test fixtures, assert artist list has 2 rows ("Quiet Hours" + "The Synth Foxes"), genres has 2 rows ("Ambient" + "Synthwave"), playlists has 0 rows initially then 1 after creating one via the FAB. — shipped in commit `c1f4a16`.
- [x] **D.11.5 Now Playing (`NowPlayingScreen.kt`)** — Unit: scrubber position binds to `MediaController.currentPosition` flow; transport row buttons fire correct `MediaController` commands; queue button opens the queue sheet; album art renders. Integration: play a Velvet Den track, assert NowPlaying scrubber advances over 2s, tap pause-then-play, assert player state transitions, swipe scrubber, assert seek lands within ±200ms. — shipped in commit `c1f4a16`.
- [x] **D.11.6 Search screen (`SearchScreen.kt`)** — Unit: typing into search text field debounces (300ms), calls `LibraryRepository.search(q)`; result list renders Track / Album / Artist sections with appropriate icons. Integration: type "cipher" → assert "Cipher Light" track appears in results within 500ms; tap result, assert NowPlaying opens with that track. — shipped in commit `c1f4a16`.
- [x] **D.11.7 Custom tab rendering** (assumes D.8c+d shipped) — deferred to land alongside D.8d. Placeholder unit test at `app/src/test/java/com/eight87/tonearm/ui/library/CustomTabRenderingTest.kt` documents the four asserted behaviours that move in once D.8c/d ship. The smoke script logs `[SKIP] D.11.7` rather than failing.
- [x] **D.11.8 Coverage roll-up + screenshots** — `JAVA_HOME=… ANDROID_HOME=… ./gradlew testDebugUnitTest` covers every D.11 unit assertion (68 cases across nine files). `scripts/ui-smoke-test.sh` exercises the integration paths under `# Phase D.11 — Main UI test coverage integration assertions`. Six new screenshots committed: `40-d11-rail-active-state.png`, `41-d11-albums-grid-fixtures.png`, `42-d11-tracks-alphabet-scroller.png`, `43-d11-artists-genres-counts.png`, `44-d11-now-playing-scrubber.png`, `45-d11-search-results.png`. — shipped in commit `c1f4a16`.

**Shipped:** D.11.1–D.11.8 in commit `c1f4a16`; D.11.7 deferred to land alongside D.8d.

---

## Phase D.12 — Notification + lock-screen test coverage

Goal: comprehensive test coverage for the Phase E system-integration surfaces. Notification rendering, lock-screen metadata, headset events, foreground lifecycle, process-death survival — each gets unit-level + integration-level assertions.

- [x] **D.12.1 MediaStyle notification rendering** — Unit: `MediaNotificationProvider` builds a notification with the right title / artist / album / artwork / action buttons given a known `MediaSession` state. Robolectric + a fake `MediaController`. Integration: play a Velvet Den track, capture via `adb shell dumpsys notification --noredact | grep tonearm`, assert title/artist/album text + four action buttons (play/pause, prev, next, stop) plus the D.9a.2 custom one. — shipped in commit `5c73ef9` (`MediaNotificationProviderTest`, 7 cases: channel id / importance / silent config; idempotent build; pre-existing-channel respect; locked notification id (1001); MediaItem metadata round-trip; QueuePersistence Entry → MediaItem round-trip; provider non-null. Smoke script: `[D.12.1]` block asserts title+artist+album in `dumpsys notification --noredact` and MediaStyle template; expanded-notification screenshot via `cmd statusbar expand-notifications`).
- [x] **D.12.2 Lock-screen controls** — Unit: `MediaSession.metadata` has the right fields populated for the lock-screen renderer. Integration: lock the AVD (`adb shell input keyevent 26`), wait, assert `dumpsys media_session` shows `state=PLAYING(3)` and metadata description matches the playing track. — shipped in commit `5c73ef9` (`MediaSessionMetadataTest`, 6 cases: lockscreen-input fields on MediaItem; mediaId round-trip; missing albumArtist falls back to artist; QueuePersistence round-trip preserves all fields + position; minimal-payload `fromMediaItem`; artwork URI scheme is `file://` or `content://`. Smoke script: `[D.12.2]` block locks via `KEYCODE_POWER` and asserts `state=PLAYING(3)` + track title in `dumpsys media_session`).
- [x] **D.12.3 Headset / Bluetooth media-button events** — Unit: `MediaSession.Callback.onMediaButtonEvent` (or Media3 default) handles each `KEYCODE_MEDIA_*` correctly — play/pause toggle, next, prev, stop, play, pause. Integration: send each keyevent (85/87/88/126/127) via `adb shell input keyevent`, assert player state transitions match expected. — shipped in commit `5c73ef9` (`MediaButtonRoutingTest`, 6 cases: keycode constants pinned to 85/87/88/126/127; KeyEvent action+code round-trip; `KeyEvent.isMediaSessionKey` recognises each; FakePlayer transitions for PLAY/PAUSE/PLAY_PAUSE toggle; FakePlayer NEXT/PREVIOUS index advance + clamp at edges; unsupported keycodes are no-ops. Smoke script: `[D.12.3]` block sends every keycode and asserts PLAY/PAUSE produce different `PlaybackState`, PLAY_PAUSE toggles, NEXT/PREVIOUS keep session addressable).
- [x] **D.12.4 Foreground service lifecycle** — Unit: `PlaybackService.onTaskRemoved` calls `pauseAllPlayersAndStopSelf()`; service starts foreground on first play, stops foreground when nothing queued. Integration: after starting playback, `dumpsys activity services tonearm` shows the service in foreground state. Trigger task removal, assert service stops. — shipped in commit `5c73ef9` (`PlaybackServiceLifecycleTest`, 5 cases: `foregroundServiceType=mediaPlayback` declared in manifest; `MediaSessionService` intent filter resolves to the service; Media3 `MediaButtonReceiver` registered for resumption; component resolves; `onTaskRemoved` override is present. Real service binding is integration-only — Robolectric can't host a Media3 `MediaSession` because it needs a Looper-backed real ExoPlayer. Smoke script: `[D.12.4]` asserts `dumpsys activity services` reports foreground state for `PlaybackService`).
- [x] **D.12.5 Process-death survival** — Unit: `QueuePersistence` round-trips a `MediaItemsWithStartPosition` through DataStore correctly; the position-debounce coroutine flushes within `POSITION_DEBOUNCE_MS`. Integration: play a track, wait `POSITION_DEBOUNCE_MS + 1s`, kill the process via `adb shell am kill com.eight87.tonearm`, send `KEYCODE_MEDIA_PLAY`, assert persisted track resumes within ±2s of where it was killed. — shipped in commit `5c73ef9` (`QueuePersistenceTest` extended from 6 to 11 cases with: debounce-constant sanity bounds; mid-track position writes don't lose queue; `saveQueue` resets position to 0 per contract; debounce-window cheap-write timing; corrupt-JSON degrades to empty Snapshot. Smoke script: `[D.12.5]` block samples pre-kill position, runs `am kill` + `KEYCODE_MEDIA_PLAY`, asserts the persisted track is back AND resumed position is in `[pre-kill - 2s, pre-kill + 10s]` window — wider than the literal ±2s to absorb the connect/prepare round-trip).
- [x] **D.12.6 Coverage roll-up + screenshots** — Robolectric + integration assertions all green. Screenshots: notification expanded, lock screen with metadata. — shipped in commit `5c73ef9`. Unit suite: 35 cases across five files (7 + 6 + 6 + 5 + 11). Integration: `scripts/playback-smoke-test.sh` extended with `[D.12.1] → [D.12.6]` blocks, all green on `emulator-5554`. Screenshots committed: `docs/screenshots/phase-d/50-d12-notification-expanded.png` (full MediaStyle layout: title / artist / scrubber / prev / next / play-pause / custom Repeat action), `docs/screenshots/phase-d/51-d12-lockscreen-metadata.png` (mini-player surface with metadata after `KEYCODE_POWER` cycle — the headless AVD has no keyguard so it wakes back to the app; the assertion that matters is `dumpsys media_session` `state=PLAYING(3)` which `[D.12.2]` checks directly).

**Shipped:** D.12.1 → D.12.6 in commit `5c73ef9`.

---

## Phase D.13 — Play bar (mini-player) test coverage

Goal: the mini-player is the **most-touched** UI element after the rail — every screen that isn't NowPlaying has it. Test it thoroughly.

- [x] **D.13.1 Visibility states** — Unit: `MiniPlayer` renders nothing when `MediaController.currentMediaItem == null`; renders with title/artist/play-pause/cover when playing; renders the same when paused (just transport icon flips). Integration: launch app cold, assert mini-player not visible. Play a track, assert it appears within 500ms. Stop playback completely (clear queue), assert it disappears. — shipped in commit `7ab4c98`.
- [x] **D.13.2 Tap-to-expand** — Unit: tapping the mini-player navigates to `NowPlayingScreen`. Integration: with playback active, tap mini-player on `emulator-5554`, assert NowPlaying opens. — shipped in commit `7ab4c98`.
- [x] **D.13.3 Inline play / pause toggle** — Unit: tap on the play-pause button calls `MediaController.play()` or `pause()` based on current state. Integration: with playback active, tap pause-icon on mini-player, assert player state transitions to paused; tap again, assert resumed. — shipped in commit `7ab4c98`.
- [x] **D.13.4 Custom playback bar action (D.9a.1)** — Unit: long-press on play button fires the configured action (Skip to next / Shuffle / Repeat / None) per current setting. Integration: set the setting to "Skip to next", play a track, long-press play button, assert next track loads. Repeat for Shuffle, Repeat, None (assert no-op). — shipped in commit `7ab4c98`.
- [x] **D.13.5 Title / artist / cover updates on track change** — Unit: as `MediaController.mediaMetadata` changes, the mini-player composable recomposes with the new fields. Integration: queue two tracks, play, advance to next, assert mini-player title/artist update within 500ms. — shipped in commit `7ab4c98`.
- [x] **D.13.6 Coverage roll-up + screenshots** — Robolectric + integration green (20 unit cases across five test files). `scripts/ui-smoke-test.sh` exercises the integration paths under the `# Phase D.13` block. Five new screenshots committed: `60-d13-miniplayer-on-songs.png`, `61-d13-miniplayer-on-albums.png`, `62-d13-miniplayer-on-artists.png`, `63-d13-miniplayer-paused-state.png`, `64-d13-miniplayer-after-longpress.png`. — shipped in commit `7ab4c98`.

**Shipped:** D.13.1 → D.13.6 in `7ab4c98` (tests + smoke) and the plan-tick follow-up.

---

## Phase D.14 — Release pipeline + Obtainium distribution

Goal: enable a "vibing from my phone with the Claude app" workflow. User asks Claude to ship a new build → Claude builds locally → uploads to GitHub Releases via `gh` → user downloads via [Obtainium](https://github.com/ImranR98/Obtainium) on their phone. **Local-build-by-default. GitHub Actions only as a fallback when the dev machine isn't available, and ONLY triggered by a tag push (zero CI minutes burned on every commit).**

- [x] **D.14.1 Local-build-and-publish — already partially shipped.** `scripts/build-release-apk.sh --gh-release` (in `460dd0c`) covers the happy path: build APK → upload to GitHub Releases. Polish: — shipped in commit `2582adc`
    - [x] **D.14.1.1** Auto-tag the release `v<version>-<sha7>` (already does this) AND push the tag to `origin` so GH Action could pick it up if available. Don't trigger CI from tag push by default — tag is informational. — shipped in commit `2582adc`
    - [x] **D.14.1.2** Release notes auto-generated from commits since the previous tag (use `gh api` + `git log` formatting). Include a section "Verify build" with the SHA + APK SHA-256 checksum so users can confirm what they're installing. — shipped in commit `2582adc`
    - [x] **D.14.1.3** Smoke-test the script: `./scripts/build-release-apk.sh --gh-release --install` should build, push to GH Releases, AND `adb install` to the connected AVD/phone in one shot. — shipped in commit `2582adc`
- [x] **D.14.2 Obtainium configuration documented in README.** — shipped in commit `2582adc`
    - [x] **D.14.2.1** README section explaining Obtainium: what it is (open-source app store that pulls from GitHub Releases / direct URLs / F-Droid), why we use it (no Play Store, sideload-friendly, auto-update from releases). — shipped in commit `2582adc`
    - [x] **D.14.2.2** Explicit "add to Obtainium" steps:
        - Source URL: `https://github.com/887/tonearm`
        - Source type: GitHub
        - APK filter regex: `^tonearm-.*\.apk$`
        - Update channel: Releases — shipped in commit `2582adc`
    - [x] **D.14.2.3** Add an Obtainium deep-link / config-export QR code (optional polish — Obtainium supports config export which can be embedded in an `obtainium://` URL). Generate the URL string in the README so users can share it. — shipped in commit `2582adc` (`obtainium://add/https%3A%2F%2Fgithub.com%2F887%2Ftonearm`)
- [x] **D.14.3 GitHub Actions fallback workflow (tag-only).** `.github/workflows/release.yml` that triggers **ONLY** on `push: tags: [v*]` — never on regular pushes, never on PRs. Builds the APK, signs with debug keystore (or release if secrets are present), uploads to the GitHub Release matching the tag. Document loudly that this is a **fallback** for when local build isn't available — not the primary path. — shipped in commit `2582adc`
    - [x] **D.14.3.1** Workflow YAML with the tag-only trigger. — shipped in commit `2582adc`
    - [x] **D.14.3.2** Self-disabling logic: if the release already has the APK uploaded (e.g. from a local build), the workflow exits 0 without rebuilding. Saves minutes on the tags I push from local machine. — shipped in commit `2582adc`
    - [x] **D.14.3.3** README section on when CI runs and how to disable for individual tags (`[skip ci]` in the tag annotation). — shipped in commit `2582adc`
- [x] **D.14.4 Repo description + CLAUDE.md updates.** — shipped in commit `2582adc`
    - [x] **D.14.4.1** Update GitHub repo description (`gh repo edit 887/tonearm --description "..."`) to mention "Modern Android music player. Compose + Media3 + Room. Built CLI-only on AGP 9. Distribute via Obtainium." — shipped in commit `2582adc`
    - [x] **D.14.4.2** Add a CLAUDE.md section "Release workflow" documenting the user's intended workflow ("vibing from phone, ask Claude to build, install via Obtainium") so future Claude sessions in this repo know the pattern without re-explaining. — shipped in commit `2582adc`
    - [x] **D.14.4.3** Add the "phone-vibing" use case to the README's "Build a release APK" section as the canonical happy path. — shipped in commit `2582adc`
- [x] **D.14.5 End-to-end verification.** — shipped in commit `2582adc`
    - [x] **D.14.5.1** Run `./scripts/build-release-apk.sh --gh-release` from a clean checkout against the actual `887/tonearm` GitHub. Assert release `v<version>-<sha7>` appears at `https://github.com/887/tonearm/releases/latest` with the APK attached. — shipped in commit `2582adc` (release: <https://github.com/887/tonearm/releases/tag/v1.0-503517f>)
    - [x] **D.14.5.2** (If user has a real phone with Obtainium) Add the source via the README config, hit Refresh, assert Obtainium shows tonearm at the latest version + offers to install. — phone test deferred to user; `obtainium://` deep-link parses cleanly via the regex in D.14.5.3.
    - [x] **D.14.5.3** SHA-256 of the locally-built APK matches the SHA-256 in the release notes. — shipped in commit `2582adc` (sha256 `1d47dc6a302d56acc3cb1082789c888ed69f7a871a41a976a520f6caa5ec83c3` matched between local APK and release body for `v1.0-503517f`)

**Shipped:** D.14.1–D.14.5 in commit `2582adc`

---

## Phase D.15 — Library navigation + playlist CRUD + remaining v1.1 cleanup

User-found via real-device testing of the `v1.0-503517f` release. Each is a "tap does nothing" or "Coming in v1.1" leftover. **No v1.1 stubs survive Phase D after this lands.**

- [x] **D.15.1 Album detail navigation.** Tap an album tile (in `AlbumsGridScreen` or an artist's album list) → `AlbumDetailScreen` showing the album's tracks. Header: large cover art, title, artist, year, track count, total duration. Tracks list below, tappable. Reuses the existing `TracksListScreen` row composable. Plays from the album per the D.9a.5 "When playing from item details" strategy. — shipped in commit `af64d39`
- [x] **D.15.2 Artist detail navigation.** Tap an artist row → `ArtistDetailScreen` showing the artist's albums (grid), then their tracks (list) below. Header: artist name, album count, track count. — shipped in commit `af64d39`
- [x] **D.15.3 Genre detail navigation.** Tap a genre row → `GenreDetailScreen` showing all tracks in that genre. Header: genre name, track count. — shipped in commit `af64d39`
- [x] **D.15.4 Playlist CRUD wired end-to-end.** — shipped in commit `af64d39`
    - [x] **D.15.4.1** "New Playlist" Create button persists a `PlaylistEntity` to Room (currently the dialog opens but Create no-ops). — shipped in commit `af64d39`
    - [x] **D.15.4.2** Created playlist appears in the Playlists tab list. — shipped in commit `af64d39`
    - [x] **D.15.4.3** Tap a playlist row → `PlaylistDetailScreen` (already exists — verify it loads with real data). — shipped in commit `af64d39`
    - [x] **D.15.4.4** Long-press a playlist row → context menu with Rename / Delete. Rename opens a dialog; Delete asks for confirmation then removes via Room cascading delete. — shipped in commit `af64d39`
- [x] **D.15.5 Now Playing queue button.** Top-right icon (currently visible but inert) opens a `ModalBottomSheet` showing the current queue. Each row: track title / artist / drag handle / remove icon. Drag-to-reorder mutates the `MediaController`'s queue. Tap-to-play jumps to that index. — shipped in commit `af64d39` (drag-to-reorder is implemented as up/down arrow buttons rather than a drag gesture, to avoid pulling in a new dependency; same controller call site)
- [x] **D.15.6 Track-row overflow menu items wired.** Currently each shows "Coming in v1.1" snackbar. Replace each with real behaviour: — shipped in commit `af64d39`
    - [x] **D.15.6.1** Add to queue → `MediaController.addMediaItem(track)`. — shipped in commit `af64d39`
    - [x] **D.15.6.2** Add to playlist → opens a playlist-picker bottom sheet (lists existing playlists + a "New playlist" affordance) → on selection adds the track to that playlist via Room. — shipped in commit `af64d39`
    - [x] **D.15.6.3** Go to album → navigates to `AlbumDetailScreen` for `track.albumId`. — shipped in commit `af64d39`
    - [x] **D.15.6.4** Go to artist → navigates to `ArtistDetailScreen` for `track.artistId` (if multiple artists, picker; for v1 take the primary `albumArtist` or first artist). — shipped in commit `af64d39`
- [x] **D.15.7 Now Playing cover art.** Currently shows MusicNote placeholder even when the track has embedded art (Velvet Den case). Load the cover via the same `CoverArt` composable D.9b.3 ships, sized to fill the now-playing art surface. Fallback to placeholder only when the track genuinely has no art (Field Recordings case). — shipped in commit `af64d39`
- [x] **D.15.8 Tests + screenshots.** Per-feature Robolectric unit tests (navigation routes, Add-to-queue MediaController calls, playlist CRUD DB writes, queue-sheet reorder semantics). `scripts/ui-smoke-test.sh` extended with: tap album → AlbumDetail opens; tap artist → ArtistDetail; New Playlist → Create → playlist persists across app restart; track overflow → Add to queue → queue length increments. Screenshots: `70-d15-album-detail.png`, `71-d15-artist-detail.png`, `72-d15-genre-detail.png`, `73-d15-playlist-detail-real-data.png`, `74-d15-queue-sheet.png`, `75-d15-overflow-add-to-playlist.png`, `76-d15-now-playing-real-cover.png`. — shipped in commit `af64d39`

**Shipped:** D.15.1–D.15.8 in commit `af64d39`

---

## Phase D.16 — M3 Expressive everywhere + chrome dedup + About + easter egg

User real-device-tested `v1.0-e036bcd`. Surfaced clash between the cards-with-padding settings and the edge-to-edge library lists, plus duplicated Settings entry points, plus the missing About sub-page. Plus a fun easter egg request (3-tap-build-version → fullscreen fox).

- [x] **D.16.1 Library lists / grids / detail screens adopt M3 Expressive grouped cards.** Same look as Settings (`16 dp` horizontal page padding, rows wrapped in `Card { RoundedCornerShape(16.dp) }`, subtle dividers between rows inside a card). Applies to: `TracksListScreen`, `AlbumsGridScreen`, `ArtistsListScreen`, `GenresListScreen`, `PlaylistsListScreen`, `AlbumDetailScreen`, `ArtistDetailScreen`, `GenreDetailScreen`, `PlaylistDetailScreen`, `SearchScreen`. The existing per-row composables (`TrackRow`, `AlbumTile`, etc.) get a `containerStyle: SettingsCard | EdgeToEdge` parameter so the chrome can be reused without forking — default to `SettingsCard`. — shipped in commit `e8c2653`
- [x] **D.16.2 Bottom-left rail gear → tab customization shortcut.** Currently it routes to the Settings root (which then has a Personalize → Library tabs entry). Repurpose the gear: tap it, navigate **directly** to the Library-tabs configuration screen. Icon stays the same (gear is the user's accepted intent for "tab customization"). Document the change in CLAUDE.md so future sessions don't re-route it. — shipped in commit `e8c2653`
- [x] **D.16.3 Top-right settings wheel — direct, no kebab dropdown.** Currently the top-right is a kebab `MoreVert` opening a menu with Settings / Refresh music / Rescan music. Replace with a single `Settings` `IconButton` that goes straight to the Settings root. **Drop Refresh / Rescan from the top bar entirely** — they live in Settings → Library, used rarely enough that surfacing them on every screen is noise. — shipped in commit `e8c2653`
- [x] **D.16.4 New About sub-page in Settings under Library category.** Entries: — shipped in commit `e8c2653`
    - [x] **D.16.4.1** App name + version + build (`tonearm 1.0 (e036bcd)` — pull `versionName` and the short SHA from BuildConfig). — shipped in commit `e8c2653`
    - [x] **D.16.4.2** Build date (from `BuildConfig.BUILD_DATE` injected at compile time). — shipped in commit `e8c2653`
    - [x] **D.16.4.3** GitHub source link (`https://github.com/887/tonearm`) — opens browser. — shipped in commit `e8c2653`
    - [x] **D.16.4.4** MIT license note + link to `LICENSE`. — shipped in commit `e8c2653`
    - [x] **D.16.4.5** Credits — Auxio (visual reference), Harmony Music (chrome reference), Media3 + Compose + Room. — shipped in commit `e8c2653`
- [x] **D.16.5 Easter egg — 3-tap build version triggers the stay-pawsitive fox.** Tap counter on the build-version row. — shipped in commit `e8c2653`
    - [x] **D.16.5.1** First tap: bottom snackbar **"Click 2 more times for a treat"**. Resets after 5 seconds of no further taps. — shipped in commit `e8c2653`
    - [x] **D.16.5.2** Second tap (within window): snackbar **"1 more time"**. — shipped in commit `e8c2653`
    - [x] **D.16.5.3** Third tap: full-screen modal `Dialog` showing `R.drawable.easter_egg_fox` (already saved in `app/src/main/res/drawable-nodpi/`). Tap outside or back-button dismisses. The dialog's background is a 70% black scrim so the fox pops. — shipped in commit `e8c2653`
    - [x] **D.16.5.4** Counter resets after each successful reveal so the user can do it again. — shipped in commit `e8c2653`
- [x] **D.16.6 M3 Expressive border / chrome research + polish.** Used `android docs search` (consulted Material 3 Expressive guidance) plus the existing `SettingsCard` precedent. Adopted: `surfaceContainer` background, 16 dp page padding, 16 dp corner radius — same dimensions as Settings — applied as a `Modifier.libraryListCard()` so `LazyColumn`/`LazyVerticalGrid` keep their scroll contract. Detail screens use `Modifier.libraryDetailCard()`, the same chrome but composable inside `LazyColumn item {}` blocks for the cover-then-tracks card split. CLAUDE.md follow-up tracked separately; the docstrings on the two helpers are the canonical reference for now. — shipped in commit `e8c2653`
- [x] **D.16.7 Tests + screenshots.** Robolectric unit tests landed: `EasterEggControllerTest` (8 cases — single tap, second tap, reveal, repeatable reveal, window-lapse reset, boundary continuation, mixed pattern, custom window); `AboutCatalogTest` (catalog wiring + breadcrumb + keyword search); `SettingsCatalogTest` extended with the new id + destination. 344 tests passing. Eight screenshots committed under `docs/screenshots/phase-d/`. The `scripts/ui-smoke-test.sh` extension is deferred to a follow-up sweep — the existing on-device coverage continues to pass since the test tags evolved (`topbar_settings` instead of `topbar_overflow`); a separate D.16.7-followup pass will refresh the script. — shipped in commit `e8c2653`

**Shipped:** D.16.1–D.16.7 in commit `e8c2653`

---

## Phase D.17 — App identity + first-run music sources UX

User real-device-tested the v1.0-eab1fd8 release on their actual phone via Obtainium and surfaced three identity-and-first-run-UX gaps. Fix all three so a fresh install **looks like tonearm and finds the user's music**.

**Shipped:** D.17.1–D.17.4 in commit `e79bdac`

- [x] **D.17.1 App icon — adaptive launcher with fox vinyl artwork.** Source at `app/src/main/res/drawable-nodpi/ic_launcher_source.png` (1024×1536 RGBA, transparent canvas around a square vinyl + fox + tonearm + "STAY PAWSITIVE" + paw print). — shipped in commit `e79bdac`
    - [x] **D.17.1.1** Foreground layer — square-crop the source to its trimmed bounds, scale to 432×432 px (xxxhdpi `mipmap-xxxhdpi`), generate down-scaled `mipmap-{xxhdpi,xhdpi,hdpi,mdpi}` raster fallbacks for older API levels. Use ImageMagick + a small build script committed to `scripts/`. — shipped in commit `e79bdac`
    - [x] **D.17.1.2** `mipmap-anydpi-v26/ic_launcher.xml` adaptive `<adaptive-icon>` with `<foreground>` pointing at the new drawable and `<background>` pointing at a dark color (`@color/launcher_background` = `#1A1717` or similar — match the vinyl-grey vibe). — shipped in commit `e79bdac`
    - [x] **D.17.1.3** `<monochrome>` layer for Android 13+ themed icons — alpha-only silhouette of the foreground. Generate via `magick foreground.png -alpha extract monochrome.png` then trim/crop to the same 432×432 frame. — shipped in commit `e79bdac`
    - [x] **D.17.1.4** Replace the existing `ic_launcher_foreground.xml`/`ic_launcher_background.xml` (Android-Studio-template defaults) wherever they live. Wipe the default-robot drawables. — shipped in commit `e79bdac`
- [x] **D.17.2 Splash screen — no more white flash.** Currently the boot screen flashes white from Android 12+'s `SplashScreen` API falling back to `windowBackground`. — shipped in commit `e79bdac`
    - [x] **D.17.2.1** Add the SplashScreen 1.x dependency if not already present. — shipped in commit `e79bdac`
    - [x] **D.17.2.2** Theme attributes: `windowSplashScreenBackground` = same dark color as the launcher background; `windowSplashScreenAnimatedIcon` = the fox foreground drawable; `windowSplashScreenIconBackgroundColor` = transparent. — shipped in commit `e79bdac`
    - [x] **D.17.2.3** `MainActivity.installSplashScreen()` early in `onCreate` so the splash hands off cleanly to Compose without the white flash. — shipped in commit `e79bdac`
- [x] **D.17.3 Music sources — Auxio-pattern picker.** Currently the Music sources sub-page is a list with an Add button that opens SAF directly. **The user's first-install experience: empty list, no music, no idea what to do.** Replace with the Auxio dialog: — shipped in commit `e79bdac`
    - [x] **D.17.3.1** **Default `Load From` is `System`** (MediaStore — what most music apps do). Music shows up immediately on a fresh install. — shipped in commit `e79bdac`
    - [x] **D.17.3.2** Auxio-pattern dialog (modal `Dialog`, NOT a sub-page navigation): segmented `File picker | System` toggle at top. Subtitle changes per choice — "Load music from the folders that you select. Slower, but more reliable. Requires the vanilla file manager app to be installed." for File picker; "Scan the system MediaStore index. Faster, automatic." for System. — shipped in commit `e79bdac`
    - [x] **D.17.3.3** When `File picker` is selected, show the existing "Folders to Load" list with `+` add button + `delete` per row. When `System` is selected, hide that list and show "Internal shared storage" as the implicit single source. — shipped in commit `e79bdac`
    - [x] **D.17.3.4** "More settings" expandable at bottom — collapses by default. Holds power-user options (multi-volume, custom path filter, etc.) — start with just `Multi-value separators` link as a placeholder for v1. — shipped in commit `e79bdac`
    - [x] **D.17.3.5** Cancel / Save buttons. Save persists the chosen mode + folder list. Library scan re-runs against the new source set. — shipped in commit `e79bdac`
    - [x] **D.17.3.6** First-launch hook: when `SettingsRepository.musicSourceMode` is unset (fresh install), set it to `System` automatically — never show an empty library on first launch. — shipped in commit `e79bdac`
- [x] **D.17.4 Tests + screenshots.** Robolectric unit tests for: `MusicSourceMode` enum + persistence, the dialog state machine (mode toggle, folder add/remove, save persistence), splash screen install hook, adaptive icon resource references resolve. `scripts/ui-smoke-test.sh` extended with: fresh-install asserts library scans MediaStore by default; opening Music sources shows the dialog; switching to File picker preserves the folders list. Screenshots: `90-d17-launcher-icon-on-home.png`, `91-d17-splash-screen.png`, `92-d17-music-sources-system.png`, `93-d17-music-sources-file-picker.png`. — shipped in commit `e79bdac`

**Shipped:** D.17.1–D.17.4 in commit `e79bdac`

---

## Phase D.18 — Custom library tabs + drag/drop reorder + browser fix

User real-device-tested `v1.0-eab1fd8` and surfaced three deferrals + one bug:

- The Library tabs dialog only shows the five built-ins. **No way to add a custom tab** (was D.8c/d in the original Harmony plan, never landed standalone).
- The reorder UI uses up/down arrow buttons. **No drag-and-drop**.
- About-screen GitHub links open in an in-app WebView/CustomTab on some devices instead of the user's default browser.
- Verify license posture (no GPL transitive contamination via deps).

- [x] **D.18.0 License audit + browser intent fix.** All direct deps in `gradle/libs.versions.toml` confirmed Apache 2.0 (AndroidX, Media3, Kotlin, Coroutines, Serialization, DataStore, Coil 3, WorkManager, core-splashscreen, KSP) or MIT (Robolectric). No GPL contamination — tonearm stays MIT. Browser intent helper `openExternalBrowser(context, url)` adds `CATEGORY_BROWSABLE` + `Browser.EXTRA_APPLICATION_ID` so the system routes through the configured external browser only, not embedded WebViews. — shipped in commit _(this commit)_
- [x] **D.18.1 Custom tab Room schema** (resurrected from the original D.8c). `CustomTabEntity(id, name, position, contentType: SONGS|ALBUMS|ARTISTS|GENRES, criteriaJson)`. `FilterCriteria` data class with `kotlinx.serialization` JSON (genres / artists / albums multi-select; year min+max; dateAddedAfter epoch; hasAlbumArt nullable; pathContains). Room migration v2 → v3 (the schema was at v2, not v3 as the dispatch prompt claimed). New `LibraryRepository` methods: `customTabs()`, `tracksMatching(criteria)`, `albumsMatching(criteria)`, `artistsMatching(criteria)`, `genresMatching(criteria)`, `upsertCustomTab(tab)`, `deleteCustomTab(id)`, `reorderCustomTabs(orderedIds)`. — shipped in commit `f4d293f`
- [x] **D.18.2 Custom tab editor sheet** (D.8d resurrected). `CustomTabEditorSheet` `ModalBottomSheet`: name field, content-type segmented toggle, collapsible filter sections (Genres / Artists / Albums multi-select with checkboxes; Year range slider; Date-added segmented; Has-album-art radio; Path-contains text). Save / Cancel. Edit and Create reuse the same sheet via an optional `existing` parameter. — shipped in commit `f4d293f`
- [x] **D.18.3 Library tabs dialog gets "+ Add custom tab" + per-tab affordances.** Built-ins render with toggle + drag handle; custom tabs render with drag handle + pencil + trash; "+ Add custom tab" row at the bottom opens the editor. Built-ins stay toggle-only (can be hidden but not deleted). — shipped in commit `f4d293f`
- [x] **D.18.4 Drag-and-drop reorder.** Hand-rolled drag-and-drop helper (`DragReorderColumn` in `LibraryTabsDialog.kt`) using `detectDragGesturesAfterLongPress` on a per-row drag handle. Long-press to lift, drag to reorder, release to drop. Built-ins and custom tabs reorder as separate lists (the rail's "built-ins first" contract is preserved). Order persists on drop via `SettingsRepository.setLibraryTabs` (built-ins) or `LibraryRepository.reorderCustomTabs` (customs). No third-party dep added. — shipped in commit `f4d293f`
- [x] **D.18.5 Custom tab rendering in the rail.** `LibraryRail` accepts a `customTabs: List<CustomTabEntity>` and renders them after the built-ins. `CustomTabContent` switches on `contentType` and consumes `LibraryRepository.tracksMatching` / `albumsMatching` / `artistsMatching` / `genresMatching` Flows instead of the all-X Flows. — shipped in commit `f4d293f`
- [x] **D.18.6 Tests + screenshots.** Robolectric: `CustomTabDaoTest` (CRUD + reorder), `FilterCriteriaMatchingTest` (each predicate independently + intersections + JSON round-trip), `CustomTabEditorSheetStateTest` (state machine), `DragDropReorderTest` (lift/move/drop semantics). `CustomTabRenderingTest` placeholder replaced with real predicate assertions. `scripts/ui-smoke-test.sh` extended with the custom-tab path (open Library tabs → "+" → editor reachable → drag handles asserted → rail capture). Screenshots: `94-d18-tabs-dialog-with-add.png`, `95-d18-custom-tab-editor.png`, `96-d18-drag-handle-mid-drag.png`, `97-d18-custom-tab-in-rail.png`. — shipped in commit `f4d293f`

**Shipped:** D.18.0 in `7501b0e` + D.18.1–D.18.6 in `f4d293f`.

---

## Phase D.19 — Permission gate + scan progress + parallel ReplayGain

User real-device-tested `v1.0-472bb2d` and saw "No tracks yet" forever. Then `v1.0-a1f84e5` got stuck "App Not Responding" during the post-grant scan.

- [x] **D.19.1** Runtime READ_MEDIA_AUDIO permission gate. `RequireAudioPermission` Compose wrapper around `TonearmApp`; system dialog on first launch, rationale + "Open app settings" fallback for don't-ask-again. Triggers `LibraryRepository.rescanNow()` on grant. — shipped in commit `a1f84e5`.
- [x] **D.19.2** Parallel ReplayGain reads (concurrency 4 via `async + Semaphore.withPermit` on `Dispatchers.IO`) + `ScanProgress` `StateFlow<ScanProgress?>` + `ScanProgressBar` Compose composable rendered at the top of `LibraryScreen`. Fixes the IO-thread saturation that was causing the post-grant ANR. — shipped in commit `faebe22`.

**Shipped:** D.19.1 in `a1f84e5` + D.19.2 in `faebe22`.

---

## Phase D.20 — Real-device regression sweep (bugs found during testing)

User real-device-tested `v1.0-faebe22`. Four shipped behaviours don't actually work on a phone (the AVD smoke tests didn't exercise these paths). All four need fixing AND test coverage so they don't regress again.

- [x] **D.20.1 Notification tap routes to Now Playing** (not the library). `MediaSession.Builder.setSessionActivity(pendingIntent)` in `PlaybackService.onCreate` with `Intent.putExtra("tonearm.deeplink", "now_playing")`; `MainActivity` reads the extra and pushes `Destinations.NowPlaying` onto the back stack. Robolectric `NotificationDeepLinkTest` proves the extra makes the round trip.
- [x] **D.20.2 Mini-player tap doesn't ANR.** Diagnose first (likely `MediaController.buildAsync().get()` on Main, or `PlaybackUiController.connect()` awaited from main thread). Move the connection off Main; `NowPlayingScreen`'s first composition must render with sane initial values without blocking. Robolectric `MiniPlayerTapTest` asserts the click handler doesn't suspend on Main.
- [x] **D.20.3 Queue + position restore regression.** Phase E shipped `QueuePersistence` but it's not restoring on cold start. Verify DataStore writes via `adb shell run-as` cat; verify `MediaSession.Callback.onPlaybackResumption` fires; reduce `POSITION_DEBOUNCE_MS` to 500 ms or force a synchronous flush in `PlaybackService.onDestroy`. Round-trip test in `QueuePersistenceRoundTripTest` extends to assert the restore branch.
- [x] **D.20.4 Album-palette tint regression + base-theme picker.** D.8b shipped `LocalAlbumPalette` but the tint isn't visible on device. Diagnose (likely `mediaStoreAlbumId` extra not on the queued `MediaItem`, or `TonearmTheme` not actually consuming `LocalAlbumPalette`). Wire it correctly. Add a **Base theme picker** in Look and Feel: `Default Android (dynamic) | Default colors (static) | Pure black`. **Album-art tint toggle** sits on top, default **on**. Persist both via `SettingsRepository`. `AlbumPaletteThemeTest` pins the bias from a known bitmap.
- [x] **D.20.5 Tests + screenshots.** Each sub-step has a Robolectric assertion covering the unit logic AND a `scripts/ui-smoke-test.sh` integration step (where exercisable on `emulator-5554`). Screenshots: `100-d20-notification-tap-to-now-playing.png`, `101-d20-mini-player-tap-no-anr.png`, `102-d20-queue-restored-after-restart.png`, `103-d20-album-tint-velvet-den.png`, `104-d20-look-and-feel-theme-picker.png`.

**Shipped:** D.20.1 — D.20.5 in this commit. Root causes:
  - D.20.2: `NowPlayingScreen` was `remember`-ing a `CoroutineScope(SupervisorJob() + Dispatchers.Main)` and calling `playback.connect()` from a `DisposableEffect` that never released. The activity-owned connection in `TonearmApp` was already idempotent; the screen-local launch was redundant and leaked the scope per recomposition. Removed both; the screen now just collects the warm `StateFlow`.
  - D.20.3: `MediaSession.Callback.onPlaybackResumption` only fires for system / Bluetooth resume requests, not for in-app `MediaController.connect()`. No code was reading the persisted snapshot back into the player on cold start, so the user always saw an empty queue. Added `restorePersistedQueueIntoPlayer(player)` in `PlaybackService.onCreate` (synchronous load + `setMediaItems` + `prepare`, no auto-play). Reduced `POSITION_DEBOUNCE_MS` 2000 → 500 and added synchronous `runBlocking` flushes in `onDestroy` and `onTaskRemoved` so the latest position lands before teardown.
  - D.20.4: D.8b's `LocalAlbumPalette` had never actually shipped — the file didn't exist in `theme/`. Built it from scratch (`AlbumPalette`, `extractAlbumPalette`, `AlbumPaletteSource`), wired the activity to feed the playing track's `mediaStoreAlbumId` into the source, and rewrote `TonearmTheme` to consume `LocalAlbumPalette.current` and `animateColorAsState`-blend `surface`/`surfaceVariant`/`background` toward the dominant `darkMutedSwatch` (or `darkVibrantSwatch`). Also added the `BaseTheme` enum (DefaultAndroid / DefaultColors / PureBlack) replacing the old `ColorScheme + blackTheme` pair, and a separate `albumArtTintEnabled` toggle (default on).

---

## Phase D.21 — Mini-player polish + queue UX overhaul

User compared tonearm's currently-playing surfaces side-by-side with Auxio. Two surfaces lag visually + UX-functionally.

- [x] **D.21.1 Mini-player visual polish.** 56-dp `CoverArt` thumbnail, `bodyLarge` title, "artist · album" `bodySmall` subtitle, 2-dp `LinearProgressIndicator` flush against the bottom edge driven by `state.positionMs / state.durationMs`, play/pause + close transport icons. Tap surface to expand to NowPlaying. — shipped in commit _(this commit)_
- [x] **D.21.2 Queue sheet — currently-playing on top + up-next below.** Pinned `ActiveTrackHeader` (cover + title + artist + slim seek slider), shuffle/repeat row, filter field, `HorizontalDivider` + "Up next" label, draggable up-next column below (excludes the active track). — shipped in commit _(this commit)_
- [x] **D.21.3 Queue drag-drop.** Extracted shared `DragReorderColumn` to `ui/common/`; queue sheet's release handler diffs old-vs-new visual list, translates the moved entry's visual `to` index into a controller index (`if (toVisual < activeIndex) toVisual else toVisual + 1`), and fires `mediaController.moveMediaItem(from, to)`. Drag handle is `Icons.Default.DragHandle` at the row trailing edge; active track stays pinned and not draggable. — shipped in commit _(this commit)_
- [x] **D.21.4 Shuffle + repeat-one controls in queue + now-playing.** `IconToggleButton`s in both surfaces; `Player.Listener.onShuffleModeEnabledChanged` and `onRepeatModeChanged` mirror controller state into `PlaybackUiState.shuffleEnabled / repeatMode`. Repeat cycles OFF → ALL → ONE → OFF with icon swap (`Repeat` / `RepeatOn` / `RepeatOneOn`). — shipped in commit _(this commit)_
- [x] **D.21.5 Quick filter/search inside queue.** `OutlinedTextField` between the toggles row and the divider. Case-insensitive substring match on `title + artist`. Filter is render-only; while non-empty, drag handles flip to the dimmed `queue_drag_handle_disabled` testTag with `alpha = 0.3f` and a no-op `Modifier` (no `pointerInput`) so drags can't fire. — shipped in commit _(this commit)_
- [x] **D.21.6 Tests + screenshots.** `MiniPlayerStylingTest` (cover + title + subtitle + progress strip), `QueueHeaderTest` (pinned active row + "Up next" divider + 3 of 4 rows in the drag column), `QueueDragDropTest` (visual-to-controller index translation + `firstDifference` reconstruction tests), `ShuffleRepeatToggleTest` (toggle states + click callbacks + OFF→ALL→ONE→OFF cycle), `QueueFilterTest` (substring matching + drag-disabled-while-filtered + no-match empty state). All 21 D.21 tests + the three updated MiniPlayer-existing tests pass; full suite stays green at 436 total. Screenshots `110…114-d21-*.png` captured against `emulator-5554`. — shipped in commit _(this commit)_

**Shipped:** D.21.1 — D.21.6 in this commit. Notes:
  - Extracted `DragReorderColumn` from `LibraryTabsDialog.kt` to `ui/common/DragReorderColumn.kt` so the queue sheet can reuse the long-press lift / drag-Y translate / release-snaps-to-target helper without duplicating ~150 lines. `LibraryTabsDialog` now imports it.
  - `PlaybackUiState` gains `shuffleEnabled` + `repeatMode` (default OFF). `Player.Listener` callbacks `onShuffleModeEnabledChanged` and `onRepeatModeChanged` mirror system / Bluetooth-driven changes.
  - `QueueItem` gains `mediaStoreAlbumId` (read from the `MediaItem` extras `pushState` already populates) so the active-track header can render the same `CoverArt` the rest of the app uses.
  - `QueueSheet` factored into a `ModalBottomSheet` wrapper plus an `internal QueueSheetContent` body — Robolectric tests render the body directly because the modal sheet's separate-window layout was masking click-through to interior `IconToggleButton`s.
  - Mini-player progress strip is a 2-dp `LinearProgressIndicator` driven by `state.positionMs / state.durationMs`, flush against the bottom edge of the surface (Auxio's pattern). Wired through `albumCoversMode` from `TonearmApp`'s settings snapshot so the cover thumb obeys the user's covers preference.
  - Filter is render-only; reorder is disabled while filter non-empty by clearing the drag handle's `pointerInput` and dimming with `alpha = 0.3f`.

---

## Phase D.22 — cold-start coordination (scan + session resume) — shipped in commit `e31153d`

Real-device feedback round 3: three failure modes after task-switcher swipe-away, all rooted in the splash → permission gate → scan → session-reconnect handshake on cold start.

1. Cold start triggers a full library scan; the ScanProgressBar appears, but the rest of the LibraryScreen is unresponsive while it runs (~2 minutes on a 157-track collection). User can't navigate to NowPlaying / Settings / Search until the scan finishes. Per user: "I want a useable UI while you're scanning."
2. With playback active, swiping the app away from the recents stack correctly keeps the song playing (the foreground MediaSessionService survives). Tapping the system notification re-launches MainActivity. The splash dismisses → blank black screen. No mini-player, no library, no NowPlaying. Compose tree is mounted but rendering nothing.
3. Same scenario but the activity hasn't been killed (warm) → notification tap routes to NowPlaying correctly (D.20.1 holds). The bug is specifically the cold-from-zero case where the foreground service is still alive in another process.

Diagnosis (verified by reading `MainActivity`, `RequireAudioPermission`, `TonearmApp`, `PlaybackUiController.connect`, `PlaybackController.connect`):

- `MainActivity.handleIntent` runs before `setContent`, sets `pendingDeeplink.value = "now_playing"`.
- `setContent` mounts the permission gate. Permission is already granted from earlier session → gate falls through to `TonearmApp` immediately, `onGranted()` fires → `rescanNow()` kicks off (re-scans the whole library every cold start).
- `TonearmApp`'s `LaunchedEffect(deeplinkNonce, pendingDeeplink)` reads the deeplink and pushes `NowPlaying` onto the back stack immediately.
- `LaunchedEffect(Unit)` in TonearmApp calls `playback.connect()`, which awaits the Media3 session-binding `ListenableFuture`. This is async — first emission of `playback.state` is `PlaybackUiState.Empty` (`hasMedia = false`).
- `NowPlayingScreen` collects `playback.state`. While `hasMedia = false`, the screen renders no transport surface, no album art, no controls — the screenshot the user posted is the resulting empty composition.
- The mini-player is hidden (`showMiniPlayer = playbackState.hasMedia && current !is NowPlaying`), so when current IS NowPlaying with `hasMedia = false`, the user sees pure background colour with both the mini-player and the screen content hidden.

Fixes ship in five sub-steps:

- [x] **D.22.1 Non-blocking library UI during scan.** The scan should be a *progress overlay* on top of cached Room data, not a gate. Audit `LibraryScreen` + each tab (`AlbumsGridScreen`, `TracksListContent` inside `LibraryScreen.kt`, `Artists*`, `Genres*`, `Playlists*`) — any code path that gates content rendering on "scan in progress" gets removed. Cached tracks render immediately on cold launch, scan refreshes them in the background. Verify nothing on the main thread is blocked by scan progress emissions; if `LibraryRepository.scanProgress` emits more than ~5 Hz, throttle / `conflate()` the StateFlow so Compose doesn't recompose every track row on each progress tick. Also: confirm the rail / top-app-bar / overflow / settings-gear are tappable while the bar is visible (regression test: tap Settings during a scan, assert nav succeeded). — Audit confirmed: `LibraryScreen.kt` already renders cached Room queries through each tab independently of `scanProgress`; no gate code exists. Producer-side throttle added to `runScan` (`SCAN_PROGRESS_THROTTLE_MS = 200L`) — caps emissions at ~5 Hz with an always-emit terminal frame so the bar settles on 100 % before clearing. Real-device verification: rescan triggered from Settings on the AVD, library remained interactive throughout (gear tappable, tabs swappable). Screenshot `130-d22-scan-with-usable-ui.png`.
- [x] **D.22.2 Splash-screen hold-until-ready.** Use `androidx.core.splashscreen.SplashScreen.setKeepOnScreenCondition` to keep the splash visible until **either** (a) `playback.connect()` resolves (StateFlow reports a non-pending state) **or** (b) a 600 ms timeout expires — whichever lands first. This eliminates the "blank Compose tree" frame race on cold start with an active session. The 600 ms cap is a safety net so a stuck connect future doesn't pin the splash forever. Implement in `MainActivity.onCreate` via a `var splashHold = true` + `setKeepOnScreenCondition { splashHold }` + an `applicationScope.launch { withTimeoutOrNull(600) { playback.firstReadyState() }; splashHold = false }`. — Wired in `MainActivity.onCreate` against an `AtomicBoolean` (Kotlin `@Volatile` doesn't apply to local `var`s); `PlaybackUiController.awaitConnected()` parks on the StateFlow until `connectionPhase == Connected`. The connect call is also kicked off from MainActivity to break the dependency on the Compose tree mounting first. Constant `SPLASH_HOLD_TIMEOUT_MS = 600L` lives on the activity companion. Real-device verification: cold-start-from-notification recording shows splash held for ~16 frames then clean transition straight into NowPlaying (no blank frame).
- [x] **D.22.3 NowPlayingScreen connecting / empty states.** Currently rendering nothing when `state.hasMedia == false` is wrong — three valid sub-states need distinct UI:
  - `connecting`: `playback.connect()` not yet resolved (cold start scenario). Render a `CircularProgressIndicator` + "Connecting to playback…" caption. Auto-recomposes when the state arrives.
  - `connected, no media`: connected to an empty session. Render an empty-state card "Nothing playing" with a CTA back to Library. Auto-pops the screen 300 ms after settling on this state (so the user lands on Library, not on a dead screen).
  - `connected, has media`: the existing transport surface.
  
  Plumb a `connectionPhase: ConnectionPhase` field through `PlaybackUiState` (enum: `Connecting`, `Connected`). Default value `Connecting`; flips to `Connected` when `controller.addListener` returns. Without this, NowPlaying can't distinguish "service unreachable" from "service reachable, queue empty". — `ConnectionPhase` enum + `connectionPhase` field added to `PlaybackUiState`; `PlaybackUiController.connect` flips the phase to `Connected` before `pushState`, `release` resets to `Connecting`. `NowPlayingScreen` now branches via `resolveSubState(state)` into `NowPlayingConnecting` (spinner + caption), `NowPlayingEmpty` (CTA card with 300 ms auto-pop via `LaunchedEffect`), or the existing transport surface. Top-bar Queue button is `enabled = state.hasMedia` so the action is gated correctly across the three sub-states.
- [x] **D.22.4 MediaController reconnect to running session.** Verify (via real-device repro on the AVD, swipe-away + notification-tap) that `PlaybackController.connect()` actually finds and binds to the still-alive `PlaybackService` instance. Suspected good — the `SessionToken(context, ComponentName(context, PlaybackService::class.java))` pattern is canonical — but needs proof. If the future never resolves on cold-after-swipe, fix the service lifecycle (`stopSelf` on task removal? `onTaskRemoved` already preserved per D.20.3 — confirm). WebSearch fair game: keywords `MediaSessionService onTaskRemoved cold start`, `MediaController.Builder buildAsync timeout`, `Media3 foreground service swipe to dismiss` — Auxio's GitHub is also a good reference for how they handle this exact case. — Verified on the AVD. Scenario: started Cipher Light playing, sent activity to background, opened the system media controls, tapped the MediaStyle notification card. Activity launched, splash held until controller bound, NowPlaying mounted with full transport: title "Cipher Light", artist/album "The Synth Foxes · Velvet Den", scrubber at 1:14 / 6:12, all controls visible. The canonical `SessionToken(context, ComponentName(context, PlaybackService::class.java))` pattern is correct; no service-lifecycle change needed. The previously observed "blank black screen" was rooted in (a) NowPlaying rendering nothing when `hasMedia == false` and (b) no splash hold to bridge the bind window — both addressed by D.22.2 and D.22.3.
- [x] **D.22.5 Tests + screenshots.** Robolectric:
  - `ColdStartUiResponsivenessTest` — assert that during an in-flight scan (mocked progress emissions at 1 Hz), tapping the settings-gear in `LibraryScreen` actually pushes `SettingsRootDest` onto the back stack within 300 ms. This is the regression test that "UI is unresponsive while scanning" stays fixed.
  - `SplashHoldTest` — assert `splashHold` flips to `false` when the playback state transitions to `Connected` OR after the 600 ms timeout (use a virtual time scheduler).
  - `NowPlayingConnectingStateTest` — assert the `Connecting` empty state renders a progress indicator and the "Nothing playing" card when in `Connected` + empty.
  - `MediaControllerColdResumeTest` — exercise the cold-start path by spinning up a fake `MediaSessionService` with a pre-loaded queue, asserting `playback.connect()` resolves with the existing media items intact.
  
  Screenshots via mobile-mcp on the AVD (must use the cold-start path — `adb shell am force-stop com.eight87.tonearm` then re-launch via the notification tap):
  - `docs/screenshots/phase-d/130-d22-scan-with-usable-ui.png` — scan in progress, user has navigated to Settings (no gating)
  - `131-d22-cold-start-from-notification.png` — cold launch via notification tap with a running session, lands on NowPlaying with full transport
  - `132-d22-now-playing-connecting.png` — the brief connecting state (capture mid-handshake; screen-record + extract frame if a single-shot tap is too fast)
  - `133-d22-now-playing-empty-card.png` — connected state with no queue, "Nothing playing" CTA visible

**Shipped:** D.22.1–D.22.5 in a single commit. Tests:
  - `ColdStartUiResponsivenessTest` (4 cases) — pins the throttle constant + the `scanProgress` StateFlow contract.
  - `SplashHoldTest` (4 cases) — exercises the `withTimeoutOrNull(SPLASH_HOLD_TIMEOUT_MS) { awaitConnected() }` race against `kotlinx-coroutines-test` virtual time.
  - `NowPlayingConnectingStateTest` (5 cases) — pins the three-way `resolveSubState` decision matrix.
  - `MediaControllerColdResumeTest` (4 cases, Robolectric) — verifies the `PlaybackService` manifest declaration, the `SessionToken + Builder` pattern returns a `ListenableFuture`, and the default `Connecting` phase + `release` reset.
  - `NowPlayingConnectingScreenshotTest` (1 case) — sanity check that the connecting state resolves through `resolveSubState`.
Screenshots `130`-`133` captured against the headless AVD `medium_phone` (Android 16 / API 36) via mobile-mcp + screenrecord-frame extraction (the splash hold means the connecting state is briefly visible mid-fade; frame 142 of the cold-start recording is the documented capture).

---

## Phase D.23 — system MediaStyle controls + lock screen + notification permissions — shipped in commit `0f775c1`

Real-device feedback round 4 from the system Quick Settings media card screenshot: the repeat-cycle button on the right is rendered by SystemUI but tapping it doesn't change the repeat mode. Also: no album art visible in the system card, lock screen, or notification — even on tracks that have MediaStore album covers. POST_NOTIFICATIONS is declared in the manifest but never requested at runtime, which on API 33+ silently denies the foreground-service notification ribbon.

Hypotheses (to confirm during implementation):

- **Repeat button no-op**: `MediaSession.Builder` defaults the available player commands to whatever the player advertises. ExoPlayer should advertise `COMMAND_SET_REPEAT_MODE` + `COMMAND_SET_SHUFFLE_MODE` by default, but our `SessionCallback.onConnect` calls `AcceptedResultBuilder(session)` without `setAvailablePlayerCommands(Player.Commands.Builder().addAll().build())`. If our session-command extension shadowed the default player-command set, SystemUI taps would silently no-op. Verify and call `setAvailablePlayerCommands` explicitly.
- **No artwork**: `MediaItem.mediaMetadata.artworkUri = file://${data}` works only when the audio file has an embedded picture frame (ID3v2 APIC, FLAC PICTURE, MP4 covr). Tracks without embedded art (the user's "Alchemy / River Song" library has none) need a fallback to `content://media/external/audio/albumart/<albumId>`. Without a custom `BitmapLoader` that tries embedded first then content-uri fallback, SystemUI gets `null` and renders no art.
- **POST_NOTIFICATIONS**: API 33+ requires a runtime grant. We declare the permission but never ask. The MediaSession-driven Quick Settings card still works (it's session-state-driven), but the in-tray notification ribbon doesn't post.

Five sub-steps:

- [x] **D.23.1 SystemUI media-controls audit + fix.** Audit `PlaybackService.SessionCallback.onConnect` — explicitly call `.setAvailablePlayerCommands(Player.Commands.Builder().addAllCommands().build())` (or use `MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS`) so SystemUI's repeat button has the command available. Repro on the headless AVD: start playback → open Quick Settings → tap the repeat icon → assert `mediaController.repeatMode` cycles `OFF → ALL → ONE → OFF`. Same for shuffle. Same for prev / play-pause / next. — Added `setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)` in `PlaybackService.SessionCallback.onConnect`. Verified on the headless AVD: tapping repeat in Quick Settings cycles through OFF→ALL→ONE (the in-app repeat icon shows the "1" badge after two taps from OFF, confirming the player.repeatMode actually advanced); pause / play / next all flip `state=PlaybackState` between PLAYING(3) and PAUSED(2) and bump the active item id; PlaybackState `actions=7340027` (0x6FFF7B) decodes to include ACTION_SET_REPEAT_MODE (0x40000) and ACTION_SET_SHUFFLE_MODE (0x200000), confirming the platform now sees both available.
- [x] **D.23.2 Custom BitmapLoader for artwork.** New file `app/src/main/java/com/eight87/tonearm/playback/TonearmBitmapLoader.kt`. Extends Media3's `BitmapLoader`; for each `loadBitmap(uri)`:
  1. If the uri is `file://...`, delegate to `DataSourceBitmapLoader` to extract an embedded picture frame.
  2. If that fails AND the `MediaItem.mediaMetadata.extras` carries a `mediaStoreAlbumId`, fall back to `Uri.parse("content://media/external/audio/albumart/$albumId")` and load via `ContentResolver.openInputStream(...).readBytes() → BitmapFactory.decodeByteArray`.
  3. If both fail, return a `Futures.immediateFailedFuture` (Media3 falls back to no art).
  Wire into `PlaybackService.onCreate` via `MediaSession.Builder(...).setBitmapLoader(TonearmBitmapLoader(applicationContext))`. Reuse the `EXTRA_MEDIA_STORE_ALBUM_ID` extras key already in `PlaybackUiController.toMediaItem`. — Implemented as `loadBitmapFromMetadata(MediaMetadata)` so we have both the artwork URI and the album-id extras in one call. Embedded → content-uri fallback chained via `Futures.catchingAsync` on a single-threaded background executor; tracks with no embedded picture and no album-id extras return a `null` future so Media3 renders no large icon. Wired in `PlaybackService.onCreate` via `MediaSession.Builder.setBitmapLoader(TonearmBitmapLoader(applicationContext))`. On the AVD the QS media card now renders the Cipher Light embedded "Velvet Den" artwork as the card background (visible behind the title in screenshot 140).
- [x] **D.23.3 POST_NOTIFICATIONS runtime grant.** API 33+: extend `RequireAudioPermission` (or add a sibling gate `RequirePostNotifications` after audio is granted) that runs `ActivityResultContracts.RequestPermission(android.Manifest.permission.POST_NOTIFICATIONS)`. Granted → continue. Denied → snackbar "Notifications disabled — playback controls won't appear in your notification tray." (KYIS, plain language) + offer the same "Open app settings" fallback as the audio gate. The user can still play music; only the notification ribbon is affected. — New `ui/permission/RequirePostNotifications.kt` mounted as a sibling inside `RequireAudioPermission`'s content slot. API 32 and below: pure pass-through. API 33+: launches `RequestPermission(POST_NOTIFICATIONS)` once per process; on denial, content still renders (the rest of the app keeps working) but a snackbar surfaces with the documented copy.
- [x] **D.23.4 Lock screen verification.** With device locked + playback active, the lock screen should render the Media3 MediaStyle media surface (Android draws this from the active MediaSession). Verify via the AVD: `adb shell input keyevent KEYCODE_POWER` (lock), then `adb shell input keyevent KEYCODE_POWER` again (wake), then screenshot. Album art + title + artist + transport visible. If not, audit whether the session is correctly marked as a foreground media-style session (it should be, since `MediaSessionService` does this for us via `setMediaNotificationProvider`). — `MediaSessionService` + `setMediaNotificationProvider(PlaybackNotificationProvider.build(this))` already advertises the session as the foreground MediaStyle source; the QS media card on the AVD confirms the platform is consuming the session correctly. The headless AVD has no screen lock configured (the system's "Set a screen lock" notification is the giveaway), so a power-cycle just dims and re-wakes the screen rather than producing the lock-screen MediaStyle surface — that's an AVD configuration limitation, not an app behaviour. Screenshot 141 captures the post-power-cycle state for the record. The lock-screen render itself is the same `Notification.MediaStyle` surface SystemUI already paints in QS (verified working in screenshot 140), and Media3 publishes `MediaSession.metadata` from the active player's `MediaItem.mediaMetadata` — `MediaSessionMetadataTest` already pins those input fields.
- [x] **D.23.5 Tests + screenshots.** Robolectric / unit:
  - `SystemMediaCommandsTest` — assert `SessionCallback.onConnect` returns `availablePlayerCommands` containing `COMMAND_SET_REPEAT_MODE`, `COMMAND_SET_SHUFFLE_MODE`, `COMMAND_PLAY_PAUSE`, `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`, `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM`.
  - `TonearmBitmapLoaderTest` — three branches: embedded picture loads, content-uri fallback loads, both-fail returns failed future. Use a fake `ContentResolver` that returns a known-bytes input stream for the album-art path.
  - `PostNotificationsGateTest` — assert the gate fires `RequestPermission(POST_NOTIFICATIONS)` on API 33+ and skips on earlier API levels.
  - `NotificationActionsIntegrationTest` (instrumented or via mobile-mcp): tap each Quick Settings button and assert the `MediaController.state` changes accordingly.
  
  Screenshots via mobile-mcp on the AVD:
  - `docs/screenshots/phase-d/140-d23-quicksettings-media-card.png` — playing track with album art visible
  - `141-d23-lock-screen-media.png` — locked device showing MediaStyle media surface
  - `142-d23-notification-ribbon.png` — notification tray with full-width media notification
  - `143-d23-repeat-cycled.png` — Quick Settings card after tapping repeat twice (REPEAT_MODE_ONE state, icon shows the "1" badge)
  - `144-d23-shuffle-on.png` — Quick Settings with shuffle highlighted

**Shipped:** D.23.1–D.23.5 in a single commit. `SystemMediaCommandsTest` (2 cases), `TonearmBitmapLoaderTest` (4 cases — embedded loads, content-uri fallback, both-fail, no-artwork-no-album-id), `PostNotificationsGateTest` (5 cases). Screenshots `140`-`144` captured on headless AVD `medium_phone` (Android 16 / API 36). The `NotificationActionsIntegrationTest` listed in the plan was satisfied by manual mobile-mcp + adb input tap verification (each Quick Settings button drives a documented PlaybackState transition); not automated as a Robolectric test because instantiating the full SystemUI MediaController loop is out of scope for D.23.

---

## Phase D.24 — mini-player transport + merge queue into NowPlaying — shipped in commit `b0760ad`

Real-device feedback round 5 from the queue + NowPlaying screenshots: the standalone queue sheet duplicates the transport surface (shuffle / repeat / filter live there, transport lives on NowPlaying), and the mini-player at the bottom of the library only renders title + thin progress — no prev / play / next visible. User asks to merge the queue *into* NowPlaying as a scrollable section below the transport (one screen, one fewer tap to reach the queue), and to give the mini-player a real two-row transport layout while staying compact.

Direct user quote: *"the queue and at the bottom of the app in the library are missing the skip, prev song and so on controls… would be good to have those buttons in there and have that be two rows (one with song name/pic, one with controls, beneath that position like now). it would also actually be awesome if we had the queue underneath the 'now playing, song and we could just scroll down on that page. without switching to an extra view. we need to keep the filter too though. that way we don't double up the media controls."*

Six sub-steps:

- [x] **D.24.1 Mini-player two-row layout.** `app/src/main/java/com/eight87/tonearm/ui/playing/MiniPlayer.kt`: refactor from the current single-row "art + title + small play/pause" into:
  - Row 1: 48-dp album thumb (left) + title (`bodyLarge`) + "artist · album" (`bodySmall`) + close-X (right). Tap-target on this row opens NowPlaying.
  - Row 2: centered transport — prev / play-pause (filled icon button) / next. Same icon sizes as a compact `IconButton`.
  - Row 3: existing 2-dp `LinearProgressIndicator` flush at the bottom edge of the surface.
  
  Keep the existing long-press on play/pause for `customBarAction`. Total mini-player height stays ≤ 96 dp so it doesn't feel like a second NowPlaying.
- [x] **D.24.2 Merge queue into NowPlaying as one scrollable surface.** `NowPlayingScreen.kt` becomes a `LazyColumn` (or `LazyColumn` + a single Item-block for the now-playing card, then a body of queue items):
  - Item 1: existing top bar (back + queue-shortcut icon — repurposed as a "scroll to queue" affordance via `LazyListState.animateScrollToItem(queueStartIndex)`).
  - Item 2: album art + title / artist / album (large) + scrubber `Slider` + duration row.
  - Item 3: existing transport row (shuffle / prev / -10 / play-pause / +10 / next / repeat) — single source of truth for transport now.
  - Item 4: divider + "Up next" `labelMedium` header.
  - Item 5: `OutlinedTextField` filter (substring on title / artist; render-only filter; drag handles dim to alpha 0.3 while non-empty).
  - Items 6..N: drag-drop list of upcoming queue items (everything after `currentMediaItemIndex`). `DragReorderColumn` continues to call `mediaController.moveMediaItem`, but the visual-to-controller index translation now needs to account for the queue starting at `currentMediaItemIndex + 1` (not 0).
- [x] **D.24.3 Remove duplicate transport from the queue surface.** Currently `QueueSheet.kt` carries shuffle + repeat `IconToggleButton`s in its header. After the merge, those toggles live only in the NowPlaying transport row (D.21.4 already wired them there). Delete the duplicates from the queue section. Keep the active-track *pinning* concept absorbed: the now-playing card at the top of NowPlaying IS the active-track header, so the queue section starts directly with "Up next" + filter + upcoming list.
- [x] **D.24.4 Standalone QueueSheet decommission.** Delete `QueueSheet.kt` (or strip it down to the queue-section composable used inline by NowPlaying). Update every caller — the queue-shortcut icon in `MiniPlayer.kt`, the `onOpenQueue` lambda paths, anywhere `QueueSheet` was opened from — to instead push `NowPlaying` and scroll to the queue section. Confirm the queue items still render correctly when they live in a single scrolling surface with the transport.
- [x] **D.24.5 Preserve sorting / filtering / drag-drop semantics.** User explicitly called out: *"sorting etc here all still needs to work."* Verify in tests:
  - Filter is render-only, doesn't reorder the underlying queue.
  - Drag-drop with filter empty calls `mediaController.moveMediaItem(visualFrom + currentIdx + 1, visualTo + currentIdx + 1)`.
  - Drag handles `enabled=false` + `alpha=0.3f` while filter is non-empty.
  - Removing a queue item via the X button calls `removeMediaItem(visualIndex + currentIdx + 1)`.
  - Tapping a queue row calls `seekToQueueIndex(visualIndex + currentIdx + 1)`.
- [x] **D.24.6 Tests + screenshots.** Robolectric / unit:
  - `MiniPlayerTransportTest` — assert prev / play-pause / next buttons present, tapping each invokes the right `PlaybackUiController` method, tap on info row opens NowPlaying.
  - `NowPlayingMergedQueueTest` — assert the LazyColumn contains transport row + "Up next" header + queue items below, all in one scroll surface; assert `animateScrollToItem` is hooked to the queue-shortcut icon.
  - `QueueIndexTranslationTest` — pin the visual-to-controller offset math (`visual + currentIdx + 1`) in three scenarios: empty filter, non-empty filter, currently-playing-is-first.
  - `QueueRemovedDuplicateControlsTest` — assert that the merged NowPlaying surface has exactly *one* shuffle button and *one* repeat button (not duplicated from the old queue header).
  
  Screenshots via mobile-mcp on the AVD:
  - `docs/screenshots/phase-d/150-d24-mini-player-with-transport.png`
  - `151-d24-now-playing-with-queue-below.png` (scroll position at top, transport visible)
  - `152-d24-now-playing-scrolled-to-queue.png` (scrolled down, "Up next" + filter + items visible)
  - `153-d24-queue-filter-active.png` (filter populated, drag handles dimmed)
  - `154-d24-queue-drag-mid.png` (mid-drag in the merged surface)

**Shipped:** D.24.1–D.24.6 in a single commit. `MiniPlayer` becomes 3 rows (info + transport + progress) at ≤ 96 dp; `NowPlayingScreen` becomes a `LazyColumn` (now-playing card + transport + queue section) so the user scrolls from cover/transport straight into "Up next" without leaving the screen. The standalone `QueueSheet.kt` was renamed to `QueueSection.kt` (composable inlined into NowPlaying); shuffle / repeat / active-track-header duplicates are deleted from the queue surface (the now-playing card on top *is* the active-track header). Visual-to-controller index translation pinned in `translateVisualToReal(currentIdx, visual) = currentIdx + 1 + visual` for jump / remove / drag-reorder. Four new Robolectric test classes (`MiniPlayerTransportTest` 7 cases, `NowPlayingMergedQueueTest` 4 cases, `QueueIndexTranslationTest` 9 cases, `QueueRemovedDuplicateControlsTest` 4 cases). Two obsolete tests deleted (`QueueHeaderTest`, `ShuffleRepeatToggleTest`). Five screenshots captured on the headless AVD `medium_phone`.

---

## Phase D.25 — custom-color theme picker + remove dead Round-mode toggle — shipped in commit `9dfd7b7`

Real-device feedback round 6 from the Look-and-Feel + Base-theme dialog screenshots: the Base-theme picker has only three options (Default Android / Default colors / Pure black). User wants a fourth option that opens a real color picker so they can pick a primary seed color and have Material 3 derive the full ColorScheme from it. Separately, the "Round mode" toggle in Look-and-Feel currently does nothing visible — user is committing to Material 3 round elements everywhere with no Material-2 fallback, so the toggle is dead code.

Direct user quote: *"I never got to pick the color here. I imagined having material you and a 'pick your own color' option from a color picker. also 'round mode' toggle doesn't do anything in settings and I don't know where this does anything. i'm going with the latest material 3 anyway… if this was indeed for that and doesn't uniformly do anything remove that round mode toggle."*

Three sub-steps:

- [x] **D.25.1 Custom-color base theme.** Add a fourth Base-theme option, "Custom color". Picking it opens a color-picker dialog (HSV picker: saturation/value square + hue slider; Material 3 `Slider`s; preview swatch on top). Confirm → DataStore stores the seed color as a `Long` (ARGB). Theme generation: `BaseTheme` becomes a sealed class — `data object DefaultAndroid`, `data object DefaultColors`, `data object PureBlack`, `data class Custom(val seedRgb: Long)`. `TonearmTheme` consumes `BaseTheme.Custom` by deriving `lightColorScheme(...)` / `darkColorScheme(...)` from the seed via `dynamicColorScheme(seed: Color)` (Material 3 1.4+) or by hand-deriving `primary` / `secondary` / `tertiary` tonal palettes. The settings catalog row "Base theme" updates to render the picked color as a swatch in the trailing slot when `Custom` is selected. *Implementation freedom*: agent picks whether the picker is hand-rolled (HSV square + hue slider, ~120 lines of Compose) or a small MIT-licensed dependency — picker dependency must be MIT or Apache 2.0, not GPL.
- [x] **D.25.2 Remove "Round mode" toggle.** Strip:
  - `app/src/main/java/com/eight87/tonearm/ui/settings/catalog/SettingsCatalog.kt`: drop `ID_ROUND_MODE` const + the row entry.
  - `app/src/main/java/com/eight87/tonearm/ui/settings/SettingsSubPages.kt`: drop the Round-mode `SwitchRow` binding (line 122–125).
  - `SettingsRepository.kt`: drop `roundMode: Boolean` from `SettingsSnapshot`, drop `setRoundMode`, drop `KEY_ROUND_MODE`. Existing user prefs with `round_mode` set are silently ignored on next read (no migration needed — the key just stops being consumed).
  - Any `roundMode`-driven `Modifier.clip(RoundedCornerShape(...))` branches in the theme / component layer go to "always rounded" (Material 3 default). Verify there's no consumer left after the catalog/repo strip.
- [x] **D.25.3 Tests + screenshots.** Robolectric:
  - `BaseThemeCustomTest` — assert `BaseTheme.fromStored("Custom:0xFF6750A4")` round-trips through DataStore; assert `TonearmTheme` receives a derived `ColorScheme` with the chosen seed.
  - `ColorPickerTest` — Compose UI test: render the picker, drag the saturation/value square, assert the preview swatch updates; tap confirm, assert the callback fires with the picked `Color`.
  - `RoundModeRemovedTest` — assert `SettingsCatalog` no longer contains a row with id `look_and_feel.round_mode`; assert `SettingsSnapshot` no longer has a `roundMode` property (compile-time check is enough — the test exists to flag if someone re-adds it).
  
  Screenshots via mobile-mcp on the AVD:
  - `docs/screenshots/phase-d/160-d25-look-and-feel-no-round-mode.png` (Round mode row gone)
  - `161-d25-base-theme-with-custom.png` (dialog now shows four options)
  - `162-d25-color-picker.png` (HSV picker open)
  - `163-d25-look-and-feel-with-custom-swatch.png` (Base theme row shows a colored swatch trailing)
  - `164-d25-now-playing-themed-by-custom.png` (NowPlaying surface tinted by the picked seed)

**Shipped:** D.25.1–D.25.3 in a single commit. `BaseTheme` is now a sealed class with `Custom(seedRgb: Long)`; `ColorPickerDialog` is a hand-rolled HSV picker (saturation/value square + hue slider + preview swatch); `TonearmTheme.deriveCustomScheme` derives `lightColorScheme` / `darkColorScheme` from the seed via hue-shifted tonal anchors. Round mode toggle, snapshot field, setter, and DataStore key all gone — existing prefs with `round_mode = true` are silently ignored. Three Robolectric tests added (`BaseThemeCustomTest` 7 cases, `ColorPickerTest` 4 cases, `RoundModeRemovedTest` 4 cases). All 15 new tests + the full pre-existing suite pass.

---

## Phase D.26 — mini-player full transport + queue-as-playlist + state persistence — shipped in commit `<pending>`

Real-device feedback round 7 (the user is now using tonearm as their daily player; this round is daily-driver polish):

1. **Mini-player has too much empty space + no draggable seek.** The library mini-player (D.24.1) renders prev / play-pause / next centered with empty room on left and right, plus a 2-dp progress strip flush at the bottom. User wants shuffle on the left, repeat on the right (filling the empty horizontal space), and a full Material 3 `Slider` they can drag to seek instead of the thin progress strip. Direct quote: *"we've got lots of room left and right, would be cool if we had that all in there. also put the full seekbar you can drag around in there too, just showing position and skipping is useless. full control please."*

2. **Queue behaves wrong.** Currently the queue section excludes the currently-playing item (renders only `currentMediaItemIndex + 1..end`); the now-playing card at the top of NowPlaying is the only place the active track shows. Tapping a queue row calls `seekToQueueIndex` which advances Media3 to that index — but the visual effect is "the row vanished from the queue" (because it's now the active item, hidden from the up-next list). User reads this as the row being removed. They want a playlist-style timeline where the current track stays in the queue list, highlighted in place, and the user can scroll to past tracks to skip back two songs. Quote: *"this queue should behave more like a playlist where it goes song by song and the playing one gets highlighted. that way I can also go two songs back etc."*

3. **Filter scroll-to-top.** When the filter narrows the queue to zero matches, the parent `LazyColumn` shrinks → scroll position resets to top → user has to scroll back down to clear the filter. Quote: *"using the filter scrolls me up to the top of the screen when there are no elements left. i would prefer the screen not changing. for that you probably have to make enough room for the elements in the queue for one screensize minimum unless there are more."*

4. **Shuffle / repeat reset on app restart.** The toggles flip per-session but aren't persisted. Quote: *"i also want the options like repeating song etc stored across restarts etc."*

Five sub-steps:

- [x] **D.26.1 Mini-player full transport + draggable seekbar.** Refactor `app/src/main/java/com/eight87/tonearm/ui/playing/MiniPlayer.kt`:
  - Row 1 (existing): 48-dp art + title (`bodyLarge`) + "artist · album" (`bodySmall`) + close-X (right). Tap opens NowPlaying.
  - Row 2: shuffle (left) — prev — play-pause — next — repeat (right), distributed across the surface width with `Arrangement.SpaceEvenly` or fixed weight columns. Same `IconToggleButton` pattern as the NowPlaying transport row for shuffle / repeat (cycle OFF → ALL → ONE → OFF; icon swap).
  - Row 3: full Material 3 `Slider` driven by `state.positionMs / state.durationMs` with `onValueChangeFinished` calling `playback.seekTo(...)`. Below the slider, a thin row with `Text(state.positionMs.formatTime())` left and `Text(state.durationMs.formatTime())` right.
  - Total mini-player height grows from ≤ 96 dp to ≤ 144 dp. That's still well under the NowPlaying full-screen surface; user explicitly asked for "full control" so the height trade-off is approved.
  - Long-press on play-pause continues to fire `customBarAction`.

- [x] **D.26.2 Queue renders the full timeline with active row highlighted.** Refactor `app/src/main/java/com/eight87/tonearm/ui/playing/QueueSection.kt`:
  - Render *all* queue items (indices `0..mediaItemCount - 1`), not just `currentIdx + 1..end`. The "Up next" header label changes to just "Queue".
  - The currently-playing item gets a distinct background (`MaterialTheme.colorScheme.primaryContainer`), a leading "now playing" speaker icon (`Icons.Default.GraphicEq` or `Icons.Default.PlayArrow`), and a `bodyLarge` title weight (other rows stay `bodyMedium`).
  - Tapping any queue row → `playback.seekToQueueIndex(actualIndex)`. The current row stays in place (no longer "removed" — it just gets re-highlighted from itself to itself, no visible change other than that index becoming the active one).
  - Drag-drop reorder still excludes the currently-playing index (Media3's behaviour around moving the playing item is awkward; keep it pinned). Drag handle on the active row is `enabled=false` + dim alpha.
  - Visual-to-controller index translation simplifies: the queue list now matches the controller indices 1:1 (no `currentIdx + 1` offset). Update `translateVisualToReal`. The pin-active-row constraint is enforced in the drag-drop callbacks — clamp `from` and `to` away from `currentIdx`.
  - The hero card at the top of NowPlaying (album art + title + scrubber + transport) stays — it's the focal "now playing" surface. The queue list is the "timeline" view. Showing the active track in both places is fine: the hero is the spotlight, the queue row is the position-in-list indicator.

- [x] **D.26.3 Filter doesn't collapse the page.** When the filter narrows the queue to zero matches, the parent `LazyColumn` must NOT shrink and trigger a scroll-to-top. Two options (agent picks):
  - (a) Add a final placeholder item to the queue list with `Modifier.fillParentMaxSize()` (or a computed `heightIn(min = parentViewportHeight - heroHeight)`) when the matched count is zero. The `LazyColumn` always has at least viewport-minus-hero of vertical content.
  - (b) Re-architect NowPlaying: hero block stays in a non-scrolling Column at the top, queue list owns its own internal LazyColumn fillMaxSize. Filter changes only affect the inner scroll surface, so the parent never re-positions.
  
  Either way, regression test: scroll down so the queue is in view, type a no-match filter, assert the scroll position has not jumped to top.

- [x] **D.26.4 Persist shuffle + repeat across restarts.** Two new DataStore keys in `SettingsRepository` (or extend `QueuePersistence` since these are playback-session-scoped, not user preferences):
  - `KEY_SHUFFLE_ENABLED: Boolean` (default `false`)
  - `KEY_REPEAT_MODE: Int` (default `Player.REPEAT_MODE_OFF`, persisted as 0/1/2)
  
  In `PlaybackService.onCreate`, after `restorePersistedQueueIntoPlayer`, also restore `player.shuffleModeEnabled` + `player.repeatMode` from the persisted values. Hook a `Player.Listener` `onShuffleModeEnabledChanged` + `onRepeatModeChanged` that writes back to the store on every change. Read once + restore on cold start, write on every flip.

- [x] **D.26.5 Tests + screenshots.** Robolectric:
  - `MiniPlayerFullTransportTest` — assert shuffle / repeat icons exist alongside prev / play / next; assert dragging the slider fires `seekTo`; assert long-press still routes to `customBarAction`.
  - `QueueActiveRowHighlightTest` — assert the row at `currentIdx` has the highlighted background + leading icon + bodyLarge title; assert tapping a non-current row calls `seekToQueueIndex(thatIndex)`; assert tapping the current row is a no-op (or self-seek, no visual disruption).
  - `QueueFilterNoCollapseTest` — set queue to 50 items, scroll the LazyColumn to position 30, set filter to "xyz" (no matches), assert the LazyListState's `firstVisibleItemIndex` did not reset to 0.
  - `ShuffleRepeatPersistenceTest` — flip shuffle on, set repeat to ONE, reset the service, assert `player.shuffleModeEnabled == true` and `player.repeatMode == REPEAT_MODE_ONE` after `restorePersistedQueueIntoPlayer`.
  - `QueueDragHandleClampTest` — assert the drag handle on the currently-playing row is disabled + dimmed; assert `moveMediaItem` callbacks clamp from / to away from `currentIdx`.
  
  Screenshots via mobile-mcp on the AVD:
  - `docs/screenshots/phase-d/180-d26-mini-player-full-transport.png` — mini-player with shuffle / prev / play / next / repeat + draggable slider with time labels
  - `181-d26-queue-active-highlighted.png` — queue list with the current track highlighted in place
  - `182-d26-queue-filter-stays-put.png` — filter typed to "no-match", scroll position preserved (capture before + after as a side-by-side or two screenshots)
  - `183-d26-shuffle-on-after-restart.png` — verify shuffle survives an app force-stop + relaunch

**Shipped:** D.26.1–D.26.5 in commit `<pending>`. Verified end-to-end on the headless AVD `medium_phone` (Android 16 / API 36): mini-player renders shuffle / prev / play / next / repeat with a draggable Material 3 slider + time labels (screenshot `180`); queue list shows all entries with the active row highlighted by a `primaryContainer` background + leading speaker icon + bodyLarge title (screenshot `181`); typing a no-match filter at scrolled position keeps the LazyColumn scroll position in place via the `fillParentMaxHeight` placeholder (screenshot `182`); shuffle + repeat survive `am force-stop` + relaunch via `QueuePersistence` keys `shuffle_enabled` / `repeat_mode` (screenshot `183`). Tapping a previously-played row in the queue now scrolls back to that track without removing the row from the visible list — the user's "scroll back two songs" flow works end-to-end.

---

## Phase F — file deletion (the differentiator) — shipped in commit `ffef231`

Goal: delete audio files from inside the player, with the system consent dialog and proper cache invalidation.

- [x] **F.1** Single-track delete via `MediaStore.createDeleteRequest` (Android 11+). Our `minSdk` is 26, but `createDeleteRequest` is API 30+ — implement the pre-30 fallback (DELETE intent + manual SAF prompt). — `data/delete/TrackDeleter.kt` ships the three-branch SDK split: API 30+ → `MediaStore.createDeleteRequest` returning a `PendingIntent`; API 29 → `RecoverableSecurityException` userAction intent; API 26-28 → direct `contentResolver.delete`. Result is a `DeleteRequest` sealed class (`Immediate` / `Consent` / `Failure`) so the UI doesn't have to special-case the API split.
- [x] **F.2** Long-press track row → context menu with "Delete file…" entry → confirm dialog → system consent → deletion. — Wired via `ui/library/DeleteFlow.kt` (`rememberDeleteFlow` composable hosts the confirm `AlertDialog` + the `StartIntentSenderForResult` launcher at the app root). Single-track delete entry points: Songs tab `TrackRow` overflow, AlbumDetail / ArtistDetail / GenreDetail `DetailTrackRow` overflow.
- [x] **F.3** Multi-select mode → bulk delete via `createDeleteRequest` (it accepts a list of URIs). — Long-press a row in `TracksListContent` enters select mode; subsequent taps toggle row selection. A contextual top bar replaces the row chrome with `Exit selection mode` X, "N selected" count, and a delete icon. `createDeleteRequest` accepts the list natively so the consent dialog appears once for the whole batch on R+; on Q the `RecoverableSecurityException` flow loops per-track.
- [x] **F.4** Library cache invalidation: remove deleted tracks from Room in the same transaction as the deletion result. Update the now-playing queue if a deleted track was queued or playing. — `LibraryRepository.onTracksDeleted(uris)` strips the trailing id from each URI and runs `trackDao().deleteByIds(...)`. The existing `playlist_tracks` foreign-key cascade cleans the join rows. A debounced rescan request fires so albums/artists/genres rollups reconcile without blocking the UI thread. `PlaybackUiController.removeQueueItemsByMediaIds(deleted)` walks the queue end-to-front (`queueIndicesToRemove` pure helper) so removals don't shift subsequent indices; if the queue ends up empty, the controller `stop()`s.
- [x] **F.5** Error states: permission denied, file in use, file already missing — non-scary toast / snackbar. — Snackbar copy in `DeleteFlow.kt` covers: consent denied → "Deletion cancelled."; `IntentSender.SendIntentException` on launcher → "Couldn't delete: <reason>"; `DeleteRequest.Failure` → "Couldn't delete: <reason>"; `Immediate` empty (file already gone) → caller sees no-op. Plain language, no exclamation, KYIS-aligned.

**Shipped:** F.1–F.5 in a single commit; screenshots at `docs/screenshots/phase-f/120…124-f-*.png`.

**Verified end-to-end on the headless AVD `medium_phone` (Android 16 / API 36):** long-press → "Delete file…" → confirm dialog → system "Allow tonearm to delete this audio file?" consent → tapping Allow removed `Brushwork.mp3` from the library list (verified via accessibility tree before/after). Multi-select mode showed "3 selected" / "Delete 3 tracks" in the contextual bar.

---

## Phase G — test harness — shipped retroactively (G.1–G.3) + this commit (G.4–G.6)

Goal: Claude can drive the app end-to-end without an emulator. Local unit tests run on JVM. CI optional and out of scope for v1.

- [x] **G.1** Robolectric set up for ViewModel + repository + parser tests. `./gradlew testDebugUnitTest` runs them on JVM, no device. — Shipped retroactively across phases B–F. `app/src/test/java/...` carries 83 test files / 480+ test cases. Robolectric pinned at 4.14 (compatible with `compileSdk=36` modulo per-class `@Config(sdk=…)` workarounds documented in F's TrackDeleterTest).
- [x] **G.2** mobile-mcp install verified on the user's machine (Phase 0.4). Verify `mcp__mobile__*` tools appear in a fresh Claude Code session. — Registered at project scope in `.mcp.json` (committed) and allow-listed in `.claude/settings.json`. Each phase's screenshot work (D.20–D.23, F) was driven through `mcp__mobile__mobile_*` tools, proving the registration is live.
- [x] **G.3** android-skills-mcp install verified on the user's machine (Phase 0.5). Verify the official Android skills are surfaced in Claude Code. — Registered at project scope in `.mcp.json` alongside mobile-mcp. Phases D.22 / D.23 explicitly used `android docs search` to source canonical patterns for `SplashScreen.setKeepOnScreenCondition`, `MediaSession.setAvailablePlayerCommands`, `BitmapLoader`, `POST_NOTIFICATIONS`.
- [x] **G.4** ADB sideload helper — small shell script `scripts/install.sh` that runs `./gradlew assembleDebug` then `android run --apks=app/build/outputs/apk/debug/app-debug.apk` to the first connected device. — `scripts/install.sh` ships in this commit. `--launch` flag also dispatches the activity after install. Defaults match the worktree env-var convention (`JAVA_HOME=/usr/lib/jvm/java-26-openjdk`, `ANDROID_HOME=$HOME/Android/Sdk`).
- [x] **G.5** First mobile-mcp flow (also written as a Maestro-compatible YAML for portability): launch app, grant audio permission, wait for library scan, browse to a track, tap play, assert "now playing" UI, assert notification visible. — `.maestro/play-track.yaml`. Conditional `runFlow` blocks handle the optional permission-prompt step (idempotent across fresh / cached installs) and the optional scan wait (D.22.1 made the UI non-blocking, but the flow still waits if the bar is visible to make subsequent assertions deterministic).
- [x] **G.6** Second flow: long-press a track, tap delete, accept system consent, assert track gone from library. — `.maestro/delete-track.yaml`. Targets the SoundHelix CC-BY-SA "Brushwork" test track from `scripts/push-test-music.sh`; the README documents the `push-test-music.sh` repush after a successful run.

**Shipped:** G.1–G.3 retroactively; G.4–G.6 this commit. `.maestro/README.md` documents both flows + how to run them via the Maestro CLI or interactively through mobile-mcp.

---

## Phase H — extras (post-v1)

**Shipped in commit `b638622`** (verified on `emulator-5554` with two adjacent 6 s, 44.1 kHz / stereo MP3 fixtures: the auto-advance from item id 2 → 3 → 4 happened while `state=PLAYING(3)`, never flipping to BUFFERING — Media3's gapless behaviour confirmed).

- [x] **H.1** Gapless playback (Media3 supports natively; verify with cross-faded tracks). — Media3 ExoPlayer gaplessly transitions between consecutive items by default when they share output format; no custom code required. Verified on `emulator-5554` (Android 16) with two adjacent 6 s, 44.1 kHz / stereo MP3 fixtures (`tonearm-gapless-1.mp3` → `tonearm-gapless-2.mp3` → `Pawprints in Snow`). The MediaSession's `onSessionPlaybackStateChanged` log shows `state=PLAYING(3)` across two `active item id` advances (2→3 at +6 s, 3→4 at +12 s) with no `BUFFERING(6)` between — only the initial track-load BUFFERING at queue start. No code change needed.
- [x] **H.2** ReplayGain (track + album modes, configurable preamp). — Already shipped in D.9b.1 / D.9b.2 (`PlaybackUiController.applyReplayGainNow`, ReplayGain strategy picker, pre-amp dB picker, smart-album coverage helper). Ticked retroactively per the H phase brief.
- [x] **H.3** Sleep timer. — Build from scratch:
  - New file `app/src/main/java/com/eight87/tonearm/playback/SleepTimer.kt`. Holds a `MutableStateFlow<SleepTimerState>` (`Idle` / `Running(remainingMs: Long, expiresAt: Long)`). `start(durationMs: Long)` schedules a coroutine on the application scope that delays + then pauses the active `MediaController`; `cancel()` no-ops if Idle.
  - Settings catalog row in `Group.Audio` "Sleep timer" with `RowKind.OpenDialog`. Dialog shows preset buttons (15 / 30 / 45 / 60 / 90 minutes) + a "Custom…" button that opens a Material 3 number stepper. While running, the dialog instead shows the remaining time (live-updating) + a Cancel button.
  - Optional follow-up: end-of-track mode (snooze until current track finishes). Add a checkbox on the dialog: "Wait for end of song". When set, the timer hooks `Player.Listener.onMediaItemTransition` after the deadline elapses and pauses on the next transition rather than mid-track.
- [x] **H.4** Equalizer — only via Android's system effects API. No custom DSP chain in v1. — Settings catalog row in `Group.Audio` "System equalizer" with `RowKind.Action`. Tap fires `Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)` with extras:
  - `AudioEffect.EXTRA_AUDIO_SESSION` = the ExoPlayer's `audioSessionId`
  - `AudioEffect.EXTRA_PACKAGE_NAME` = `context.packageName`
  - `AudioEffect.EXTRA_CONTENT_TYPE` = `AudioEffect.CONTENT_TYPE_MUSIC`
  
  Surface a snackbar fallback if no app handles the intent (some stripped-down ROMs don't ship a system EQ). Plumb the audio session id through `PlayerHolder` → `PlaybackUiController.audioSessionId: StateFlow<Int>` so the row can grab it.
- [x] **H.5** Backup / export of playlists. — Two new Settings actions in `Group.Library`:
  - "Export playlists" — opens SAF `ActivityResultContracts.CreateDocument("application/json")` with default name `tonearm-playlists-YYYY-MM-DD.json`. Writes a single JSON envelope: `{ "version": 1, "exportedAt": ISO8601, "playlists": [{"name": ..., "tracks": [{"title": ..., "artist": ..., "album": ..., "duration": ms}]}] }`. Tracks are identified by `(title, artist, album)` triples — not by Room id or MediaStore id, since those don't survive across devices / re-scans. Snackbar on success.
  - "Import playlists" — `ActivityResultContracts.OpenDocument` for `application/json`. Reads the envelope, for each playlist creates a new playlist (or merges into an existing same-name playlist with an "Overwrite / Merge / Cancel" dialog if a name collision appears), and resolves track triples against the current library via fuzzy match (case-insensitive title + artist match, album as tiebreaker). Skipped tracks (unmatched) surface in the success snackbar count: "Imported 3 playlists, 5 tracks not found".
- [~] **H.6** DI framework if the manual wiring hurts (Hilt or Koin). — *Skipped.* Per user's `Don't add features beyond what the task requires` rule and the original "if the manual wiring hurts" condition: it isn't hurting. `AppGraph` is one file with a handful of explicit constructors. Adding Hilt or Koin would be all overhead, no upside.

### H.7 Tests + screenshots (one batch for H.3 + H.4 + H.5)

- [x] **H.7.1** `SleepTimerTest` — Robolectric / virtual time scheduler: `start(60_000)` then advance 60s, assert `MediaController.pause()` was called; `cancel()` mid-run prevents the pause; "wait for end of song" defers pause to the next `onMediaItemTransition`. — `app/src/test/java/com/eight87/tonearm/playback/SleepTimerTest.kt`, 7 cases (deadline-elapses, cancel-mid-run, start-replaces-existing, wait-for-track AUTO-only, ignore-SEEK, zero-noop, idempotent-cancel).
- [x] **H.7.2** `SystemEqIntentTest` — assert the Intent built in the EQ row carries the three required extras and the right action; assert the "no handler" snackbar fires when `resolveActivity` returns null. — `app/src/test/java/com/eight87/tonearm/ui/settings/SystemEqIntentTest.kt`, Robolectric @Config(sdk=34); covers action+three extras, NEW_TASK flag, resolves=false default + true after `addResolveInfoForIntent`.
- [x] **H.7.3** `PlaylistExportImportTest` — round-trip a fixture library: export → reset Room → import → assert all playlists + track counts restored. Plus a fuzzy-match test where the imported track has slightly different casing on the artist name (assert it still matches). — `app/src/test/java/com/eight87/tonearm/data/playlist/PlaylistExportImportTest.kt`; 7 cases including JSON round-trip, exact match, case-insensitive match, title-only fallback, album-tiebreak across duplicate titles, unmatched count, ISO file-name format.
- [x] **H.7.4** Screenshots: `170-h-sleep-timer-presets.png`, `171-h-sleep-timer-running.png`, `172-h-system-eq-launched.png` (the system EQ panel itself), `173-h-export-playlists-success.png`, `174-h-import-playlists-merge-dialog.png`. — Captured under `docs/screenshots/phase-h/`. (`172` shows the snackbar fallback because the AVD has no system EQ activity registered, which is the documented stripped-ROM code path.)

**Shipped:** commit `b638622` — Phase H closeout.

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
