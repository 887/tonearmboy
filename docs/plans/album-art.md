# tonearmboy — album-art roadmap

## Status: ✅ DONE — all five phases shipped.

Shipped:
- Phase A: per-album cover override via Room v6 + SAF picker. Commit `9d0a948`; migration fix `dcf4aa4`.
- Phase B: SAF folder cover-art scanner (cover/folder/albumart/front .jpg/.png/.webp). Toggle in Album art sources group. Default ON.
- Phase C: refresh-album-art row drops Coil's cache. Commit `dcf4aa4`.
- Phase D: MusicBrainz auto-discover + per-album on-tap search. Commit `7bfc9cc`.
- Phase E: Settings › Content "Album art sources" group containing the Phase B + D toggles.

## Why

Album art is currently anaemic. The scanner picks up embedded cover art
from track tags via the legacy `MediaStore.Audio.AlbumColumns` path, and
the `CoverArt` composable resolves them through
`content://media/external/audio/albumart/<albumId>`. Anything beyond
that — albums whose tracks don't carry embedded art, custom user art,
or a "fix the wrong cover" affordance — is missing. The "Auto-discover
missing album art" toggle that used to live on Settings › Content was a
persisted boolean with no consumer (tapping it just surfaced a "Coming
in v1.1" snackbar); G+ removed it pending a real implementation.

User-stated goal: album art sources as **toggles** (multiple
simultaneously-active providers), with **custom folders** as one of the
sources, an **update-album-art** button, and a per-album **Replace
cover** action reachable from a context menu.

## Locked decisions

- **Per-album override is the cheapest meaningful win.** The user can
  always pick an image file and pin it to one album; everything else
  (auto-discover, MusicBrainz, folder scanning) is a strictly larger
  project that can build on top of this primitive.
- **No HTTP integration in v1.** MusicBrainz / Cover Art Archive needs
  rate limiting, background workers, caching, MBID lookup. Not yet.
- **Override storage:** mirror the existing playlist-cover pattern
  (`PlaylistEntity.coverUri`). Add an `album_covers` table keyed by
  `(albumName, albumArtist)` with a `coverUri` column. Use the same
  Room migration shape that shipped the playlist column.
- **CoverArt composable becomes async-aware.** It already wraps Coil,
  so adding "if there's a per-album override URI, use that first;
  otherwise fall back to the MediaStore `albumart` URI" is a single
  branch above the existing path.

## Phase A — per-album cover override (shipped in `9d0a948`)

- [x] **A.1** `AlbumCoverEntity` (Room v5) keyed by `albumKey(name,
  artist)` with a *nullable* `coverUri` column. Tri-state semantics:
  row missing = no choice, row + URI = pinned, row + null URI =
  intentionally empty.
- [x] **A.2** `AlbumSource` gains `albumCoverChoice(key) →
  Flow<AlbumCoverChoice>` (sealed: `NoChoice / Pinned(uri) /
  IntentionallyEmpty`), `setAlbumCoverUri`,
  `clearAlbumCoverIntentional`, `resetAlbumCover`. Implemented on
  `LibraryRepository`.
- [x] **A.3** `CoverArt` composable gains
  `coverUriOverride: String?`. The IntentionallyEmpty branch
  translates at the call site to `(albumId = null, override = null)`
  so the placeholder renders.
- [x] **A.4** `AlbumDetailScreen` TopAppBar overflow menu gains
  "Replace cover…" (SAF `OpenDocument`, persistable read grant),
  "Set to no cover", "Reset to default cover" (only shown when there
  *is* a user choice).
- [x] **A.5** Migration covered by Room's exported schema (v5.json
  committed). DAO unit tests deferred — the surface is small enough
  that the AVD smoke + Compose integration covers it.
- [x] **A.6** AVD smoke: pick a JPEG, sticks across cover-mode
  changes + app restart.
- [x] **A.7** Ship + tick.

**Effort:** S–M (½ day landed). **Risk:** low (mirrored existing
playlist-cover pattern).

## Phase B — folder cover-art source (shipped)

- [x] **B.1** New `FolderArtScanner.walk(context, treeUris)` returns
  `Map<dirNameLowercase, coverUriString>` for every directory whose
  listing contains a `cover` / `folder` / `albumart` / `front` image
  (jpg/jpeg/png/webp). Match priority cover → folder → albumart →
  front. SAF only; users in System mode are unaffected.
- [x] **B.2** Hooked into `LibraryRepository.runScan` after the audio
  pass: maps each scanned track's parent-dir basename to the
  discovered cover, walks unique album-keys, only writes
  `album_covers` rows where no row exists yet (Phase A's
  Pinned/IntentionallyEmpty wins).
- [x] **B.3** Runs on every rescan; manual override takes precedence
  by virtue of `dao.row(key).first() == null` guard.
- [x] **B.4** Settings › Content toggle "Scan folders for cover art"
  (default ON). New `ScanConfigSource.folderCoverScanEnabled` Flow
  fed from `LibrarySettings.scanFoldersForCoverArt` (default true).
- [x] **B.5** Ship + tick.

**Effort:** S–M (½ day landed). **Risk:** low (SAF walk re-uses
the existing audio scanner's tree-grant infrastructure).

## Phase C — refresh-album-art action (shipped in `dcf4aa4`)

- [x] **C.1** Settings › Library row "Refresh album art" between
  Rescan and the playlist export rows. `Icons.Outlined.Image`. On
  tap drops Coil's memory + disk caches via
  `SingletonImageLoader.get(context).memoryCache?.clear() /
  diskCache?.clear()`. The user's pinned overrides (Phase A) are
  unaffected — only the render cache is cleared, so the next
  `CoverArt` re-reads from disk.
- [x] **C.2** Snackbar feedback "Album art cache cleared" (en + de).
- [x] **C.3** Ship + tick.

**Effort:** XS (~1 hour landed). **Risk:** none.

## Phase D — MusicBrainz Cover Art Archive (shipped in `7bfc9cc`)

- [x] **D.1** `MusicBrainzClient` (okhttp). Mutex + delay(1100) so
  serialised calls stay ≤ 1 req/sec per MB TOS. User-Agent
  identifies the app + a contact URL per their requirements.
- [x] **D.2** Lookup: Lucene-quoted fuzzy `release:"<album>" AND
  artist:"<artist>"` query on the JSON API. Picks the top score
  ≥ 95. MBID-from-tag prefer-path **deferred** — none of the
  scanned tracks carry `MUSICBRAINZ_RELEASE_ID` today, so adding
  the tag-read path adds no value until tag plumbing is there.
- [x] **D.3** `AlbumArtBulkWorker` walks `albums.observeAlbums()
  .first()`, skips albums with embedded MediaStore art, skips rows
  with `Pinned` / `IntentionallyEmpty`. `NoChoice` only — Phase A's
  sentinel is honoured, the user's "no cover here" choice survives
  the bulk pass.
- [x] **D.4** Settings › Content › "Auto-discover missing album art"
  toggle. Default OFF (privacy: enabling sends artist + album text
  to a third-party service). On = enqueue one-shot worker
  (`UNMETERED` constraint), off = cancel pending work.
- [x] **D.5** Per-album on-tap "Search MusicBrainz for cover" row in
  the Album Detail overflow menu. Calls the fetcher with
  `overwriteUserChoice = true` so the user can intentionally
  override even a previously-pinned cover via this path.
  **Candidate chooser** (instead of auto-pinning the top hit)
  deferred — current behaviour pins the score≥95 top hit
  immediately, which is acceptable for v1.
- [ ] **D.6** Robolectric test for the MB fuzzy lookup parser.
  Deferred — covered by AVD smoke for now.
- [x] **D.7** Ship + tick.

**Effort:** L (2–3 days, mostly testing the rate-limit and offline
fallback paths). **Risk:** medium (third-party service + network
behaviour). **Privacy note:** the toggle ships off by default and the
settings copy must be explicit that enabling sends artist/album text
to a third party.

## Phase E — multiple-source UI (shipped)

- [x] **E.1** New `Groups.AlbumArtSources` group on the Content
  sub-page. Holds the Phase B + D toggles, in source-of-truth order
  (folder scan first since it's the user's local files, MusicBrainz
  second since it's the network fallback). Embedded MediaStore art
  is implicit (always on); no info row for it.
- [ ] **E.2** "Add custom folder" row for art-only folders. Deferred
  — the existing Music sources › FilePicker covers any folder the
  user wants the scanner to look at; a separate "art-only folders"
  picker is convenience that hasn't been asked for.
- [x] **E.3** Ship + tick.

**Effort:** XS (~1 hour landed). **Risk:** none.

## What ships in v1 vs. later

- **v1 ships:** the existing scanner + `CoverArt` legacy path. The
  dishonest "Coming in v1.1" toggle is gone.
- **v1.1 plan:** Phase A (per-album override) + Phase B (folder
  scanning). These are the two highest-value additions and don't
  require any network plumbing.
- **v1.2 plan:** Phase C (refresh) + Phase D (MusicBrainz) + Phase E
  (multi-source UI). Land together once D's behaviour has been
  verified.
