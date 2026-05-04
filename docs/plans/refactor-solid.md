# tonearmboy — SOLID refactor plan

## Status: 🟡 IN PROGRESS — Phase R.A starts after the next user kick-off.

_Synthesized 2026-05-03 from a four-agent SOLID audit (data / ui-library+playing / settings / playback+nav). 50 findings collapsed into six lettered phases below, ordered so each phase unblocks the next. Phase R.A is mechanical and low-risk; phases get hairier downstream._

---

## Cross-cutting themes (the audit in one breath)

Five god-objects shape the codebase: `LibraryRepository.kt` (565 LOC, 6 concerns), `SettingsRepository.kt` (826 LOC, 25+ keys + fat snapshot), `PlaybackUiController.kt` (882 LOC, 6 axes), `LibraryScreen.kt` (1528 LOC, 5 near-duplicate tab dispatchers), `TonearmboyApp.kt` (820 LOC, every nav destination inline-wired). Composables take these god-handles wholesale (ISP), so a leaf row that needs `track.title + onClick` ends up depending on `LibraryRepository` (~30 methods). Two wrong-direction dependencies leak across layers (`data/LibraryRepository` imports `ui.settings.SettingsRepository`; `playback/PlaybackService` imports `MainActivity` deeplink constants). Several `when`-over-type chains should be sealed-type-dispatched (5 tab dispatchers, 4 `FilterCondition` enumerations, every nav `entry<Destination>`). One boilerplate quartet repeats 25× in settings (`stringPreferencesKey + Flow + setter + snapshot field`).

The phases below attack these in unblock-order: narrow data interfaces first (cheapest, opens up every UI consumer), then settings facets, then the playback split, then the LibraryScreen reshape, then the TonearmboyApp shrink, then standalone polish wins.

---

## Phase R.A — narrow data interfaces (LibraryRepository → focused readers) — shipped in commits `1b2f68e..b702585` (R.A.7 verified in this commit)

**Why:** Every UI screen takes the whole `LibraryRepository` (~30 methods) when it needs 1–3 Flows. This forces recomposition coupling, makes preview/test setup heavy (a real `Context` + Room are needed for the simplest screen), and means a change to playlist CRUD risks breaking the tab renderers. Fixing it first unblocks Phases R.D and R.E (composables can shrink to narrow params).

**How to apply:** Define narrow interfaces in `data/`. The composition root (`AppGraph`) is the only place that maps interface → concrete `LibraryRepository`.

- [x] **R.A.1** Define `TrackSource`, `AlbumSource`, `ArtistSource`, `GenreSource`, `PlaylistStore`, `CustomTabStore`, `LibraryScanner`, `MediaChangeSource` interfaces in `data/`. Each carries only the methods its consumers need (audit reports F1+F2 enumerate them).
- [x] **R.A.2** `LibraryRepository` implements all eight interfaces (single class, multiple narrow contracts) — no behaviour change yet.
- [x] **R.A.3** Update `AppGraph` to expose each interface separately (`val tracks: TrackSource = libraryRepository`, etc.). Mark `libraryRepository` `@Deprecated` to flag remaining direct uses.
- [x] **R.A.4** Migrate UI call sites to take the narrow interface: tab dispatchers → `TrackSource`/`AlbumSource`/etc.; `PlaylistTile` → just the playlist-cover Flow (function-typed param). Audit findings UI-F6, UI-F7, UI-F8 enumerate the screens.
- [x] **R.A.5** Remove the data → ui import: move `SettingsRepository` to a neutral package (`data.settings/` or top-level `settings/`) **OR** define `ScanConfigSource` in `data/` and have `SettingsRepository` implement it. `LibraryRepository` constructor takes `ScanConfigSource`, not the concrete settings repo. (Data-F3.)
- [x] **R.A.6** Drop the constructor self-defaults in `LibraryRepository` (Data-F6); `AppGraph` supplies all collaborators explicitly.
- [x] **R.A.7** Verify: `:app:assembleDebug` clean, `:app:testDebugUnitTest` green, AVD smoke (open Library tabs, play a track, edit MyMix).
- [x] **R.A.8** Ship + tick.

**Effort:** L (1–2 days). **Risk:** medium — wide call-site change, but each migration is mechanical. **Blast radius:** UI library/playing/playlist/customtab; no DB / no Room migration.

---

## Phase R.B — settings: facets + `Setting<T>` + kill `SettingsSnapshot` — shipped in commits `43a142b..680907f` (R.B.7 verified on AVD this commit)

**Why:** `SettingsRepository` (826 LOC) reads/writes 25+ preferences via a hand-rolled quartet (`stringPreferencesKey` + `Flow` + setter + snapshot field), then projects them into a 27-field `SettingsSnapshot` that every sub-page eagerly subscribes to — so toggling theme recomposes the audio screen. Sub-pages take the concrete repo (DIP miss). Splitting into facets compresses ~300 LOC of boilerplate and stops the cross-screen recomposition.

**How to apply:** Keep one DataStore on disk (no migration). Introduce a `Setting<T>(key, default, encode, decode)` value type with `flow(store)` + `set(store, value)` extensions. Group settings into facets that each sub-page can take in isolation.

- [x] **R.B.1** Introduce `Setting<T>` + `EnumSetting<E>` in `data/settings/`. Express two existing keys via the new abstraction as a smoke test.
- [x] **R.B.2** Migrate the rest of `SettingsRepository`'s keys to `Setting<T>` declarations (~25 keys). Hand-rolled boilerplate gone; on-disk keys unchanged.
- [x] **R.B.3** Define facet interfaces: `ThemeSettings`, `PlaybackSettings`, `LibrarySettings`, `MusicSourcesSettings`, `TabLayoutSettings`. `SettingsRepository` implements all five (mirror of R.A.2).
- [x] **R.B.4** Sub-pages take only the facet they need: `SettingsLookAndFeelScreen(theme: ThemeSettings)`, `SettingsAudioScreen(playback: PlaybackSettings)`, etc. Drop the `SettingsRepository`-wholesale parameter (Settings-F4).
- [x] **R.B.5** Delete `SettingsSnapshot` and the `combine` that builds it (Settings-F3). Each consumer reads its narrow `Flow<T>` directly via `collectAsStateWithLifecycle`.
- [x] **R.B.6** Move UI-only helpers out of the repo: `BaseTheme.pickerOptions` + `baseThemeMatch` → `ui/settings/BaseThemeUi.kt` (Settings-F11). `parseLibraryTabs` → `LibraryTabOrder` value type (Settings-F12).
- [x] **R.B.7** Verify: tests + AVD round-trip every settings sub-page, confirm nothing recomposes-on-unrelated-key any more (rough check via logcat).
- [x] **R.B.8** Ship + tick.

**Effort:** L (1–2 days). **Risk:** low-medium — DataStore keys unchanged, but recomposition behaviour shifts (good direction; verify no screen relied on the snapshot identity for state). **Blast radius:** every settings sub-page + a few `MusicSourcesDialog` / `LibraryTabsDialog` call sites.

---

## Phase R.C — `PlaybackUiController` split (5 collaborators behind narrow interfaces)

**Why:** One 882-LOC class owns playback connection lifecycle, state projection, transport commands, queue mutation, ReplayGain re-application, sleep timer, position ticker, settings flag mirrors, and `Player.Listener` callbacks. At least six independent reasons to change. Composables that just want `state: StateFlow<PlaybackUiState>` take the whole controller, including ReplayGain + library handle (which `MiniPlayer` does not need).

**How to apply:** Split along axes already implicit in the code. Composables consume narrow interfaces (`NowPlayingState`, `TransportCommands`, `QueueCommands`); the god class becomes a small composition root.

- [x] **R.C.1** Define `NowPlayingState`, `TransportCommands`, `QueueCommands`, `ReplayGainCommands` interfaces in `playback/`. `MiniPlayer` and `NowPlayingScreen` migrate to take only what they read (Playback-F4).
- [x] **R.C.2** Extract `PlaybackStateProjector` — owns `_state` + `_queue` + `pushState`. Split `pushPlaybackState()` (cheap, position-only, on the 250 ms ticker) from `pushQueueSnapshot()` (listener-driven only) so the queue isn't recomputed every position tick (Playback-F5).
- [x] **R.C.3** Extract `TransportCommands` impl + `QueueCommands` impl as separate classes wrapping the `MediaController`.
- [x] **R.C.4** Extract `ReplayGainController(library, scope)` — owns `applyReplayGainNow` + `computeQueueAlbumCoverage` + the settings-flag mirrors (`replayGainStrategy`, `replayGainPreampDb`). Removes `LibraryRepository` from `PlaybackUiController` entirely (Playback-F6).
- [x] **R.C.5** Move `SleepTimer` construction out of the controller into `AppGraph`; the controller no longer owns it (Playback-F10).
- [x] **R.C.6** `PlaybackUiController` shrinks to the connection lifecycle + listener attach + composing the four collaborators. Target: under 200 LOC.
- [x] **R.C.7** Verify: AVD smoke loop — play, pause, skip, scrub, queue add/remove/reorder, sleep timer, lock-screen controls, notification controls. **This is the highest-risk verification surface in the plan.**
- [x] **R.C.8** Ship + tick.

**Effort:** L (2 days). **Risk:** medium-high — Media3 listener wiring is fragile; mistakes break notification + lock-screen state. Lean on the existing `PlaybackController` smoke test + manual AVD pass. **Blast radius:** every UI surface that consumes playback state (~5 composables); `PlaybackService` unchanged.

---

## Phase R.D — `LibraryScreen` split + `TabSpec` engine

**Why:** `LibraryScreen.kt` at 1528 LOC houses the chrome scaffold, five tab dispatchers (~840 LOC of near-duplicate "pick filtered Flow → sort → group by section key → render LazyColumn|TileGrid + AlphabetScroller"), sort comparator factories, `TrackRow`, `MultiSelectBar`, `AlphabetScroller`, `SectionHeader`, `EmptyState`, and four pre-D.28 wrapper shims. At least five reasons to change in one file. Adding a sixth content type means editing every tab.

**How to apply:** Two-pass refactor. First, mechanical file split (no behaviour change) so each piece is reviewable. Then, collapse the five tab dispatchers behind a `TabSpec<T>` strategy.

- [x] **R.D.1** Split `LibraryScreen.kt` mechanically: `LibraryScreen.kt` (scaffold + top-bar + dispatch only, target ~200 LOC), `tabs/{Tracks,Albums,Artists,Genres,Playlists}TabScreen.kt`, `tabs/AlphabetScroller.kt`, `tabs/MultiSelectBar.kt`, `tabs/SectionHeader.kt`, `tabs/EmptyState.kt`, `LibrarySorting.kt` (the four `sortFoo` functions — pure, easy to test). No behaviour change.
- [x] **R.D.2** Define `TabSpec<T>` interface: `observe(filter)`, `sectionKey(item)`, `comparator(sort)`, `Row(item, callbacks)`, `Tile(item, callbacks)`. (UI-F2.) — shipped in commit `985a2d2`.
- [x] **R.D.3** Implement five `TabSpec` instances (one per content type). Replace the five tab dispatcher composables with one `LibraryTabRenderer(spec, …)` engine.
- [x] **R.D.4** Hoist multi-select state out of the tab body into `rememberSelectionState()` — pure transition methods, unit-testable (UI-F13).
- [x] **R.D.5** Move `firstDifference` / `clampMoveAwayFromActive` / `translateVisualToReal` from `QueueSection.kt` to `playing/QueueReorderLogic.kt` — pure logic, file-scope test seam unchanged (UI-F12).
- [x] **R.D.6** Verify: AVD smoke every tab, alphabet rail, sort, view-mode toggle, multi-select; tests green.
- [x] **R.D.7** Ship + tick.

**Effort:** L (2 days). **Risk:** medium — many test files have file-scope coupling. **Blast radius:** `ui/library/**` only; data + playback unchanged.

---

## Phase R.E — `TonearmboyApp` shrink: `RouteScope` + per-route renderers + extracted IO controllers

**Why:** `TonearmboyApp.kt` at 820 LOC has every `entry<Destination>` inline-rendering its route with full data plumbing — adding a destination means editing this file (closed against extension). It also hosts SAF launchers (export/import), the playlist picker overlay, the music-sources dialog, the import-collision dialog, the deeplink reactor, and four `LaunchedEffect`s mirroring settings into the controller. Six unrelated change-axes.

**How to apply:** Define a `RouteScope` data interface (graph + backstack + snackbar + facets + callbacks) and per-destination `Render(scope: RouteScope)` extensions. Lift cross-cutting concerns into `remember*Controller` helpers.

- [x] **R.E.1** Define `RouteScope` interface carrying everything a route needs (`graph`, `backStack`, `snackbar`, `playback: TransportCommands`, settings facets, etc.).
- [x] **R.E.2** Per-destination `Register(scope)` extensions on the sealed `Destination` interface — one file per destination grouping (e.g. `routes/SettingsRoutes.kt`, `routes/LibraryRoutes.kt`).
- [x] **R.E.3** `TonearmboyApp.kt` shrinks to: theme + scaffold + top-app-bar + a single `entryProvider { destination -> destination.Register(scope) }` block. Target: under 150 LOC. — landed at 149 LOC.
- [x] **R.E.4** Lift playlist export/import out of `TonearmboyApp` into `rememberPlaylistBackupController(graph, snackbar)` returning `{ onExport, onImport, collisionDialog }` (Playback-F9).
- [x] **R.E.5** Lift the playlist picker overlay (single + bulk) into `rememberAddToPlaylistController(graph)`.
- [x] **R.E.6** Lift the four settings → playback `LaunchedEffect`s into `rememberPlaybackSettingsBridge(playback, settings)` — one place to wire mirrors.
- [ ] **R.E.7** Push side-effect launchers out of settings sub-pages into injectable interfaces: `AutoReloadController`, `EqualizerLauncher`, `MusicSourceCommands` (Settings-F6).
- [x] **R.E.8** Define `SessionActivityIntentFactory` interface; `PlaybackService` uses it instead of `Intent(this, MainActivity::class.java)` so service no longer imports the UI module (Playback-F8).
- [ ] **R.E.9** Verify: deep-link from notification, every nav route, SAF import collision dialog, AVD config-change survival.
- [ ] **R.E.10** Ship + tick.

**Effort:** L (2 days). **Risk:** medium — `@Serializable` `Destination` keys must round-trip through `SavedStateHandle`; deep-link reactor has subtle ordering. **Blast radius:** nav + settings sub-pages; data + playback contract-stable.

---

## Phase R.F — cross-cutting polish (independent small wins)

**Why:** Each item below is independent and ships standalone. Pick whichever lands in front of the next feature you touch — they don't block each other and don't block earlier phases.

- [ ] **R.F.1** Unify `TrackRow` + `DetailTrackRow` + `QueueRow` behind one composable + sealed `TrackContextAction` (UI-F5). Removes a duplicated enum + future-proofs new actions.
- [ ] **R.F.2** Per-variant `ConditionUi` registry on `FilterCondition` (label + summary + `@Composable Editor` + default factory). Editor screen iterates the registry — true OCP for new variants (UI-F10).
- [ ] **R.F.3** Extract `PlaybackTransportRow(state, callbacks, iconSize)` shared between `MiniPlayer` and `NowPlayingScreen`. Mini-player passes `iconSize = 24.dp`, NowPlaying passes 36/56 (UI-F11).
- [ ] **R.F.4** Split `Track` into `Track` (cache-faithful domain) + `ScannedTrack` (scan-only superset with splitter outputs + album-level ReplayGain). Removes the silent contract drift in `Mapping.toDomain` (Data-F4 + F10).
- [ ] **R.F.5** Move `FilterCriteria.fromLegacyJson` + `buildLegacyConditions` into a sibling `FilterCriteriaLegacy` object. Frozen migration concern lives next door, not in the live type (Data-F8).
- [ ] **R.F.6** `LibraryDao.replaceAll` / `applyDelta` take `LibrarySnapshot` data class instead of 4 explicit lists (Data-F9).
- [ ] **R.F.7** Split `data/db/Entities.kt` per entity (Data-F12). Cosmetic but cheap; reduces merge friction in worktrees.
- [ ] **R.F.8** Delete vestigial `data/DataRepository.kt` + `MainScreenViewModel` placeholder if nav-graph-orphan (Data-F11).
- [ ] **R.F.9** Extract `MediaChangeObserver` shared by repository scan + `LibraryWatcherService`; one debounce policy (Data-F7).
- [ ] **R.F.10** Extract `MediaStoreCursorReader` + `ReplayGainEnricher` from `MediaStoreScanner`; drop `runBlocking`, make API `suspend` (Data-F5).
- [ ] **R.F.11** Extract `QueuePersistenceController` + `NotificationLayoutController` from `PlaybackService`; service `onCreate` becomes wiring only (Playback-F7).
- [ ] **R.F.12** Add `repository.observeTracksForAlbum/Artist/Genre/observeYearSpan` Flows; detail screens stop filtering in Compose (UI-F7 + F14).
- [ ] **R.F.13** Fold `RowKind` into `SettingsRowBinding` so each binding carries its own `@Composable Render(entry)`; the `null` arm becomes a compile-time error (Settings-F9).
- [ ] **R.F.14** Co-locate `SettingsCatalog` row definitions per section file; one aggregator `flatten`s. Removes the 683-LOC central edit-magnet (Settings-F7).
- [ ] **R.F.15** Replace `Group` enum with inline `GroupRef`; render order via list position. Removes `PersonalizeBehaviour`-style hacks (Settings-F8).
- [ ] **R.F.16** Rename `popToOrPush` to `popToFirstOrPush` (or fix to `indexOfLast` to match doc), so the LSP contract matches the name (Playback-F12).
- [ ] **R.F.17** `rememberSettingPicker<T>(...)` helper so each sub-page body becomes a `bindings` list, not 100+ LOC of `var xPicker by remember { mutableStateOf(false) }` (Settings-F5).
- [ ] **R.F.18** `CustomTabContent` callbacks grouped by audience (`TrackInteractions` / `NavInteractions`); ARTISTS/ALBUMS/GENRES branches drop 5 unused params (UI-F8).
- [ ] **R.F.19** `PlaylistsTilesScreen` extracts `PlaylistDialogHost` + sealed `PlaylistDialogState` (UI-F9).

---

## How to use this plan

- **Default work order:** R.A → R.B → R.C → R.D → R.E. R.F items can land any time, in any order, between or alongside the lettered phases.
- **Each phase ships independently.** Don't bundle two; the diffs get unreadable.
- **Tick checkboxes in the same commit as the work** (per global plan-discipline rule). Add `shipped in commit <id>` to the phase header when every sub-step is ticked.
- **Risk gates:** R.C requires manual AVD verification of every playback control surface. R.E requires deep-link + config-change verification. The others are mechanical-with-tests.
- **When this plan is fully ticked,** restore `## Status: ✅ DONE` at the top with a re-completion note covering R.A through R.F.

## Audit provenance

50 findings collapsed from four parallel Opus-4.7 agent reports run on 2026-05-03 against commit `9388357` (post-D.30 ship). Per-finding traceability via the audit-id suffixes (Data-Fn / UI-Fn / Settings-Fn / Playback-Fn) on each sub-step. The four raw reports are not preserved in repo — re-run the audit if granular re-derivation is needed.
