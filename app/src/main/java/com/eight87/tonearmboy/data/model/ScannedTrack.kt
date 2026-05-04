package com.eight87.tonearmboy.data.model

/**
 * R.F.4 — scanner output. Superset of [Track] carrying the scan-only
 * fields that aren't persisted to the cache: album-level ReplayGain
 * (album dB / peak) and the multi-value-splitter outputs (additional
 * artists / album-artists / genres). (Data-F4 + F10.)
 *
 * The split removes the silent contract drift in `Mapping.toDomain`:
 * pre-R.F.4, [Track] carried these fields with default values so the
 * cache-loaded Track instances had nulls/empties for them, while
 * scanner-emitted Track instances had the populated values. Two
 * different Track shapes flowing through the same type. After the
 * split the cache only ever produces [Track]; only the scanner
 * produces [ScannedTrack], and the scan flow explicitly drops the
 * scan-only fields when persisting.
 *
 * The album-rollup derivation path consumes [ScannedTrack] (it needs
 * the album-level ReplayGain values + the splitter outputs to fold
 * extra artist / genre rows). The cache + UI surfaces consume [Track].
 */
data class ScannedTrack(
  val id: Long,
  val title: String,
  val artist: String?,
  val album: String?,
  val albumArtist: String?,
  val durationMs: Long,
  val trackNumber: Int?,
  val year: Int?,
  val genre: String?,
  val data: String,
  val dateAddedSeconds: Long,
  /** Track-level ReplayGain in dB (persisted; mirrored on `Track`). */
  val replayGainTrackDb: Float? = null,
  val replayGainTrackPeak: Float? = null,
  /** D.9b.3 — MediaStore album id (persisted; mirrored on `Track`). */
  val mediaStoreAlbumId: Long? = null,
  /**
   * D.9b.1 — album-level ReplayGain. Read from the file's
   * `REPLAYGAIN_ALBUM_*` tags during scan. Folded into the album
   * rollup table via `Mapping.foldAlbumReplayGain`; not persisted on
   * the per-track cache.
   */
  val replayGainAlbumDb: Float? = null,
  val replayGainAlbumPeak: Float? = null,
  /**
   * D.9c.1 — secondary artist values produced by the multi-value
   * splitter. Primary artist is in [artist]; additional values
   * contribute extra rows in the artist rollup. Not persisted.
   */
  val additionalArtists: List<String> = emptyList(),
  val additionalAlbumArtists: List<String> = emptyList(),
  val additionalGenres: List<String> = emptyList(),
)
