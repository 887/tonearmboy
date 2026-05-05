# tonearmboy — per-row cover actions across track/album/playlist/artist surfaces

## Status: PLANNED

## Why

Phase A shipped per-album cover overrides reachable only from the
Album Detail TopAppBar overflow menu. The user wants the same actions
(Replace cover, Search MusicBrainz, Set empty, Reset) at the
discovery surfaces — wherever they're already looking at a row or tile,
not having to drill into a detail screen first.

User feedback (verbatim, paraphrased):

> "in the select mode in columns, double columns mode or on the dots
> in the list view I also need to be able to reload/replace the cover
> art. as I said for songs, albums and playlists. even artists."

So the gap is two-dimensional: **what types of rows need cover
actions** (tracks, albums, playlists, artists) and **where those
actions appear** (list view 3-dot menu, tile/grid context menu,
multi-select bar).

## Constraints (locked)

- **Re-use Phase A's `AlbumCoverChoice` tri-state** for albums; mirror
  the same shape for tracks / artists / playlists. Auto-fetch (Phase
  D) must continue to skip `IntentionallyEmpty`.
- **One Room table per entity-kind** rather than a polymorphic
  `entity_covers` table. Keys are the natural identity for each kind
  (track id, album-key, playlist id, artist name lowercase).
- **Same MusicBrainz client** for "Search MusicBrainz" — for tracks,
  search by `artist + album + track` and pick the recording's
  release; for artists, search the artist endpoint; for playlists,
  no MusicBrainz lookup makes sense (they're user constructs), only
  Replace + Set empty + Reset.

## Phase R1 — track cover overrides

- [ ] **R1.1** Add `track_covers` table (DB v7). PK = trackId
  (Long). `coverUri` nullable (tri-state same as albums).
- [ ] **R1.2** `TrackSource.trackCoverChoice(id)` / `setTrackCoverUri`
  / `clearTrackCoverIntentional` / `resetTrackCover`. Mirror
  AlbumSource shape.
- [ ] **R1.3** `CoverArt` composable already takes
  `coverUriOverride`; track-row callers pass the track's choice.
- [ ] **R1.4** Add menu items on track rows (3-dot menu in list
  view). Re-use the existing `TrackContextAction` enum.
- [ ] **R1.5** Bulk multi-select: when tracks are selected, the
  multi-select bar's overflow gets the same cover actions.

## Phase R2 — playlist cover already exists (Phase D.27.6)

`PlaylistEntity.coverUri` shipped in D.27.6. Already supports
Replace + Set + Reset. **Add to roadmap:** "Search MusicBrainz" is
nonsensical for playlists (user-defined collections), so the menu
is just Replace / Set empty / Reset.

- [ ] **R2.1** Audit `PlaylistsTilesScreen` context menu — confirm
  Replace + Set + Reset present, expose them from list view too.
- [ ] **R2.2** Bulk: multi-selecting playlists exposes nothing
  cover-related (each playlist needs its own image; bulk action
  doesn't make sense). Skip.

## Phase R3 — artist cover overrides

- [ ] **R3.1** Add `artist_covers` table (DB v7 alongside R1).
  PK = artistName lowercase. `coverUri` nullable.
- [ ] **R3.2** `ArtistSource` gains the same set of methods as
  AlbumSource.
- [ ] **R3.3** Artist row + Artist Detail screen menu items. Same
  shape as Album Detail.
- [ ] **R3.4** MusicBrainz: `MusicBrainzClient.findArtistId(name)`
  + the artist front image from CAA (only some artists have a
  release-group-keyed artist portrait; otherwise null).

## Phase R4 — tile / grid context menus

The current tile rendering (`LibraryTileGrid.TileCell`) has tap +
long-press hooks but no per-tile overflow icon. Long-press currently
triggers multi-select.

- [ ] **R4.1** Add a small overflow IconButton in the top-right of
  each tile (only when not in selection mode). 24-dp touch target,
  Icons.Filled.MoreVert.
- [ ] **R4.2** Tap on the overflow opens a DropdownMenu with the
  cover actions (or the full per-kind action set for tracks).
- [ ] **R4.3** AVD smoke: tile mode → tap overflow → cover actions
  appear → pick → cover updates.

## Phase R5 — multi-select bulk actions

- [ ] **R5.1** Multi-select bar's overflow gets a "Cover art"
  sub-menu. For selected tracks: Replace cover (one URI applied to
  all), Search MusicBrainz, Set empty, Reset. For selected albums:
  same.
- [ ] **R5.2** Bulk Search MusicBrainz: enqueue the existing
  `AlbumArtBulkWorker` style worker but scoped to the selected
  set (keys passed via WorkData).

## Effort + risk

- **R1 (track overrides):** L (1–1.5 days). Mostly mechanical Room
  + repository work. Risk: schema-migration validation.
- **R2 (playlist audit):** XS. Likely already done.
- **R3 (artist overrides):** M (1 day). Tail of the same pattern.
- **R4 (tile overflow icon):** M (½–1 day). UI restructuring +
  tap-target tuning.
- **R5 (bulk):** M (1 day). New WorkManager input type.

Total: ~5 days of focused work to land all five.

## Sequence note

R1 + R3 first — they're the schema work. R4 + R5 layer on top once
the per-kind store primitives exist. R2 is a quick audit at the end.
