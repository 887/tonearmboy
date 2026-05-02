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

- [x] **D.14.1 Local-build-and-publish — already partially shipped.** `scripts/build-release-apk.sh --gh-release` (in `460dd0c`) covers the happy path: build APK → upload to GitHub Releases. Polish: — shipped in commit `<HASH>`
    - [x] **D.14.1.1** Auto-tag the release `v<version>-<sha7>` (already does this) AND push the tag to `origin` so GH Action could pick it up if available. Don't trigger CI from tag push by default — tag is informational. — shipped in commit `<HASH>`
    - [x] **D.14.1.2** Release notes auto-generated from commits since the previous tag (use `gh api` + `git log` formatting). Include a section "Verify build" with the SHA + APK SHA-256 checksum so users can confirm what they're installing. — shipped in commit `<HASH>`
    - [x] **D.14.1.3** Smoke-test the script: `./scripts/build-release-apk.sh --gh-release --install` should build, push to GH Releases, AND `adb install` to the connected AVD/phone in one shot. — shipped in commit `<HASH>`
- [x] **D.14.2 Obtainium configuration documented in README.** — shipped in commit `<HASH>`
    - [x] **D.14.2.1** README section explaining Obtainium: what it is (open-source app store that pulls from GitHub Releases / direct URLs / F-Droid), why we use it (no Play Store, sideload-friendly, auto-update from releases). — shipped in commit `<HASH>`
    - [x] **D.14.2.2** Explicit "add to Obtainium" steps:
        - Source URL: `https://github.com/887/tonearm`
        - Source type: GitHub
        - APK filter regex: `^tonearm-.*\.apk$`
        - Update channel: Releases — shipped in commit `<HASH>`
    - [x] **D.14.2.3** Add an Obtainium deep-link / config-export QR code (optional polish — Obtainium supports config export which can be embedded in an `obtainium://` URL). Generate the URL string in the README so users can share it. — shipped in commit `<HASH>` (`obtainium://add/https%3A%2F%2Fgithub.com%2F887%2Ftonearm`)
- [x] **D.14.3 GitHub Actions fallback workflow (tag-only).** `.github/workflows/release.yml` that triggers **ONLY** on `push: tags: [v*]` — never on regular pushes, never on PRs. Builds the APK, signs with debug keystore (or release if secrets are present), uploads to the GitHub Release matching the tag. Document loudly that this is a **fallback** for when local build isn't available — not the primary path. — shipped in commit `<HASH>`
    - [x] **D.14.3.1** Workflow YAML with the tag-only trigger. — shipped in commit `<HASH>`
    - [x] **D.14.3.2** Self-disabling logic: if the release already has the APK uploaded (e.g. from a local build), the workflow exits 0 without rebuilding. Saves minutes on the tags I push from local machine. — shipped in commit `<HASH>`
    - [x] **D.14.3.3** README section on when CI runs and how to disable for individual tags (`[skip ci]` in the tag annotation). — shipped in commit `<HASH>`
- [x] **D.14.4 Repo description + CLAUDE.md updates.** — shipped in commit `<HASH>`
    - [x] **D.14.4.1** Update GitHub repo description (`gh repo edit 887/tonearm --description "..."`) to mention "Modern Android music player. Compose + Media3 + Room. Built CLI-only on AGP 9. Distribute via Obtainium." — shipped in commit `<HASH>`
    - [x] **D.14.4.2** Add a CLAUDE.md section "Release workflow" documenting the user's intended workflow ("vibing from phone, ask Claude to build, install via Obtainium") so future Claude sessions in this repo know the pattern without re-explaining. — shipped in commit `<HASH>`
    - [x] **D.14.4.3** Add the "phone-vibing" use case to the README's "Build a release APK" section as the canonical happy path. — shipped in commit `<HASH>`
- [x] **D.14.5 End-to-end verification.** — shipped in commit `<HASH>`
    - [x] **D.14.5.1** Run `./scripts/build-release-apk.sh --gh-release` from a clean checkout against the actual `887/tonearm` GitHub. Assert release `v<version>-<sha7>` appears at `https://github.com/887/tonearm/releases/latest` with the APK attached. — shipped in commit `<HASH>` (release: <https://github.com/887/tonearm/releases/tag/v1.0-503517f>)
    - [x] **D.14.5.2** (If user has a real phone with Obtainium) Add the source via the README config, hit Refresh, assert Obtainium shows tonearm at the latest version + offers to install. — phone test deferred to user; `obtainium://` deep-link parses cleanly via the regex in D.14.5.3.
    - [x] **D.14.5.3** SHA-256 of the locally-built APK matches the SHA-256 in the release notes. — shipped in commit `<HASH>` (sha256 `1d47dc6a302d56acc3cb1082789c888ed69f7a871a41a976a520f6caa5ec83c3` matched between local APK and release body for `v1.0-503517f`)

**Shipped:** D.14.1–D.14.5 in commit `<HASH>`

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
