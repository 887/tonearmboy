# tonearmboy — album-art roadmap

## Status: PLANNED

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

## Phase A — per-album cover override

- [ ] **A.1** Add `AlbumCoverEntity` (Room) keyed by
  `(albumName, albumArtist)` with a `coverUri` column. Migration adds
  the new table; existing albums reload covers from MediaStore as
  before.
- [ ] **A.2** `LibraryRepository.albumCoverUri(albumKey)` → `Flow<String?>`
  and `setAlbumCoverUri(albumKey, uri)`. Mirrors the playlist
  primitive.
- [ ] **A.3** `CoverArt` composable gains an optional
  `coverUriOverride: String?` parameter. When non-null, Coil loads
  the override URI; otherwise the existing legacy MediaStore path.
- [ ] **A.4** AlbumDetailScreen's overflow menu gains "Replace cover"
  + "Reset cover" rows. Replace launches an image picker
  (`OpenDocument` SAF intent, persistent grant). Reset clears the
  override. Both go through `setAlbumCoverUri`.
- [ ] **A.5** Robolectric test: setting an override emits the new URI;
  resetting reverts to MediaStore; surviving an app-restart works
  (Room persistence).
- [ ] **A.6** AVD smoke: pick a JPEG from the gallery for an album,
  back out, re-enter — cover sticks.
- [ ] **A.7** Ship + tick.

**Effort:** S–M (½–1 day). **Risk:** low (mirrors an existing
shipped primitive).

## Phase B — folder cover-art source

- [ ] **B.1** Scanner extension: when ingesting an album whose tracks
  have no embedded art, look in the album's folder for files named
  `cover.{jpg,png,webp}`, `folder.{jpg,png,webp}`,
  `{albumname}.{jpg,png,webp}`. First match wins.
- [ ] **B.2** Persist the discovered URI in the same
  `album_covers.coverUri` column as Phase A so the override mechanism
  doesn't need two paths.
- [ ] **B.3** Run on every rescan; manual override (Phase A) takes
  precedence over folder discovery.
- [ ] **B.4** Add a Settings › Content toggle "Scan folders for cover
  art" (default on). Persisted; consumed by the scanner.
- [ ] **B.5** AVD smoke: drop a `cover.jpg` next to an album's tracks,
  rescan — album picks it up; user override still wins.
- [ ] **B.6** Ship + tick.

**Effort:** M (1 day). **Risk:** low–medium (filesystem walking adds
to scan time on large libraries).

## Phase C — refresh-album-art action

- [ ] **C.1** Add a Settings › Library row "Refresh album art" that
  invalidates Coil's disk cache for `content://media/.../albumart/*`
  URIs and triggers a rescan. Useful when the user has dropped new
  `cover.jpg` files and wants the change to land without a full
  rescan.
- [ ] **C.2** Snackbar feedback ("Album art refreshed").
- [ ] **C.3** Ship + tick.

**Effort:** XS (1–2 hours). **Risk:** low.

## Phase D — MusicBrainz Cover Art Archive (deferred)

- [ ] **D.1** Build an HTTP client around the okhttp instance Coil 3
  already pulls in. Rate limit to 1 req / sec per MB's TOS.
- [ ] **D.2** Lookup strategy: prefer the track's MBID
  (`musicbrainz_releaseid` tag); fall back to a fuzzy MB query by
  `artist + album`.
- [ ] **D.3** Background worker (existing WorkManager) walks albums
  missing local art (after Phase B), enqueues HTTP fetches, persists
  results into the `album_covers` table.
- [ ] **D.4** Settings › Content toggle "Auto-discover missing album
  art (MusicBrainz)". Default OFF (privacy: any HTTP fetch leaks
  metadata).
- [ ] **D.5** Robolectric test for the MB fuzzy lookup parser. AVD
  smoke against a small known-album set.
- [ ] **D.6** Ship + tick.

**Effort:** L (2–3 days, mostly testing the rate-limit and offline
fallback paths). **Risk:** medium (third-party service + network
behaviour). **Privacy note:** the toggle ships off by default and the
settings copy must be explicit that enabling sends artist/album text
to a third party.

## Phase E — multiple-source UI

- [ ] **E.1** Restructure Settings › Content's album-art rows into a
  grouped "Album art sources" section. Each provider (Embedded,
  MediaStore, Folder scan, MusicBrainz, Per-album override) gets its
  own toggle row. Order is fixed; toggles control whether the scanner
  + CoverArt composable consult that source.
- [ ] **E.2** "Add custom folder" row reuses the SAF tree-picker
  pattern from Music Sources. Folders here don't contain audio; they
  contain loose art files keyed by some convention (`<artist> -
  <album>.jpg`).
- [ ] **E.3** Ship + tick.

**Effort:** S (½ day). **Risk:** low (UI restructure, not new
plumbing). **Sequence note:** lands AFTER Phase D so there's actually
something to toggle.

## What ships in v1 vs. later

- **v1 ships:** the existing scanner + `CoverArt` legacy path. The
  dishonest "Coming in v1.1" toggle is gone.
- **v1.1 plan:** Phase A (per-album override) + Phase B (folder
  scanning). These are the two highest-value additions and don't
  require any network plumbing.
- **v1.2 plan:** Phase C (refresh) + Phase D (MusicBrainz) + Phase E
  (multi-source UI). Land together once D's behaviour has been
  verified.
