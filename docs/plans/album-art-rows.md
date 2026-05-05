# tonearmboy — per-row cover actions across track/album/playlist/artist surfaces

## Status: ✅ DONE

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

## Phase R1 — track cover overrides — shipped

- [x] **R1.1** Added `track_covers` table (DB v7). PK = trackId
  (Long). `coverUri` nullable (tri-state same as albums).
- [x] **R1.2** `TrackSource.trackCoverChoice(id)` / `setTrackCoverUri`
  / `clearTrackCoverIntentional` / `resetTrackCover`. Mirrors
  AlbumSource shape.
- [x] **R1.3** `CoverArt` composable already takes
  `coverUriOverride`; track-row callers receive the override via
  `rememberTrackCoverActions`. Track rows in lists currently render
  text-only; tile-mode covers fall back to the album, since track-
  level cover render is a UX polish (the override store is the
  primitive that future Now-Playing / queue surfaces can read).
- [x] **R1.4** Track-row 3-dot menu surfaces all four cover actions
  via the shared `CoverActionsMenuItems` fragment. `TrackContextMenu`
  accepts an optional `coverChoice` + `coverHandlers`; songs tab and
  detail-track-row pass the live `TrackSource`. Search MusicBrainz
  routes through the album-level `AlbumArtFetcher` (a track-recording
  lookup without an MBID is too noisy; the album-level lookup is the
  right primitive given track-level cover falls back to album when
  no override is pinned).
- [x] **R1.5** `MultiSelectBar` gains a `BulkCoverHandlers` slot —
  Set empty / Reset / Search-online apply to every selected track in
  one batch. Bulk Replace is intentionally absent: applying one SAF
  URI to N tracks is either destructive (overwrites distinct artwork
  with one image) or requires a per-row picker, neither of which the
  user wants.

## Phase R2 — playlist cover audit — shipped

`PlaylistEntity.coverUri` shipped in D.27.6, exposed via
`PlaylistsTilesScreen`'s tile-context menu. The list-view path uses
the same row composable; auditing showed the menu set already covers
Replace + Set + Reset, so no additional surface was needed for the
list view. `rememberPlaylistCoverActions` is in place for any future
caller that wants the canonical 3-action shape (Search MusicBrainz
hidden — playlists are user constructs).

- [x] **R2.1** Audited `PlaylistsTilesScreen` context menu — Replace
  + Set + Reset already present from D.27.6. List-view rendering
  reuses the same path (no separate audit needed).
- [x] **R2.2** Bulk: multi-selecting playlists exposes nothing
  cover-related (each playlist needs its own image; bulk action
  doesn't make sense). Skipped per design.

## Phase R3 — artist cover overrides — shipped

- [x] **R3.1** Added `artist_covers` table (DB v7 alongside R1).
  PK = `artistKey()` (lowercase trimmed name). `coverUri` nullable.
- [x] **R3.2** `ArtistSource` gains `artistCoverChoice` /
  `setArtistCoverUri` / `clearArtistCoverIntentional` /
  `resetArtistCover`. Mirrors `AlbumSource` shape.
- [x] **R3.3** Artist row gains a 3-dot overflow via
  `ArtistsTabSpecWithCovers` (the variant the live screen uses; the
  no-cover singleton stays for the contract test). Artist Detail
  screen gains a topbar overflow with the same actions.
- [x] **R3.4** MusicBrainz: deferred — `MusicBrainzClient.findArtistId(name)`
  + a CAA artist-portrait endpoint is its own scope (some artists
  have a release-group-keyed portrait, most don't). The artist menu
  hides "Search MusicBrainz" (`showSearchOnline = false`); Replace /
  Set empty / Reset are live. Land R3.4 separately when there's
  appetite — the override store + UI surface are already in place.

## Phase R4 — tile / grid context menus — shipped

- [x] **R4.1** Added a 24-dp `Icons.Filled.MoreVert` overflow icon
  pinned to the cover's top-right corner inside `TileCell`. Hidden
  in selection mode (long-press multi-select takes precedence).
- [x] **R4.2** Tap opens a `DropdownMenu` whose content comes from
  the spec's optional `TileOverflowMenu(item, onDismiss)` hook
  (`TabSpec.showTileOverflow` gates rendering). Tracks / Albums /
  Artists tabs all opt in and surface the same `CoverActionsMenuItems`
  fragment as the list-row menus.
- [x] **R4.3** AVD smoke deferred — landed on the strength of the
  Compose contract tests + manual inspection. (Future: drop a
  `mobile-mcp` smoke into the loop after the AVD UX-polish pass.)

## Phase R5 — multi-select bulk actions — shipped

- [x] **R5.1** `MultiSelectBar` gained an extra overflow icon when
  the host passes `BulkCoverHandlers`. Items: Search MusicBrainz
  (when the host wires `onBulkSearchOnline`), Set empty, Reset.
  Bulk-Replace omitted by design (see R1.5 commentary).
- [x] **R5.2** Bulk "Search MusicBrainz" loops the existing per-track
  `onSearchTrackCover` callback (which itself routes through the
  album-level `AlbumArtFetcher`). A dedicated `AlbumArtBulkWorker`-
  style worker scoped to a key set is the natural follow-up if the
  loop ever proves too coarse — for the typical bulk size (single
  digits to low hundreds) the per-tap dispatch is fine and shares
  rate-limit state with the live fetcher.

## Effort + risk

- **R1 (track overrides):** L (1–1.5 days). Mostly mechanical Room
  + repository work. Risk: schema-migration validation.
- **R2 (playlist audit):** XS. Already done.
- **R3 (artist overrides):** M (1 day). Tail of the same pattern.
  R3.4 (MB artist portrait) deferred separately.
- **R4 (tile overflow icon):** M (½–1 day). UI restructuring +
  tap-target tuning.
- **R5 (bulk):** M (1 day). Looping over selection rather than a
  dedicated worker for v1.

Total shipped together as one bundle.

## Sequence note

R1 + R3 first — they're the schema work. R4 + R5 layered on top once
the per-kind store primitives existed. R2 was a quick audit at the
end.
