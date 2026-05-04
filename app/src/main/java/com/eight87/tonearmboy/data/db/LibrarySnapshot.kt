package com.eight87.tonearmboy.data.db

/**
 * R.F.6 — bundle of cached library tables that move together through
 * [LibraryDao.replaceAll] / [LibraryDao.applyDelta] (Data-F9).
 *
 * Replaces the four-explicit-list signature so adding a new rollup
 * (e.g. composers, year-buckets) is one new field on this data class
 * instead of an N+1 fanout across every call site.
 */
data class LibrarySnapshot(
  val tracks: List<TrackEntity>,
  val albums: List<AlbumEntity>,
  val artists: List<ArtistEntity>,
  val genres: List<GenreEntity>,
)

/**
 * Incremental variant: same rollup tables, plus a removed-id list and
 * the tracks that need upserting. The albums / artists / genres lists
 * are full re-derives (current Room model rebuilds them every delta).
 */
data class LibraryDelta(
  val removedTrackIds: List<Long>,
  val upsertedTracks: List<TrackEntity>,
  val albums: List<AlbumEntity>,
  val artists: List<ArtistEntity>,
  val genres: List<GenreEntity>,
)
