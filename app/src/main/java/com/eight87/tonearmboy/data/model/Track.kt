package com.eight87.tonearmboy.data.model

/**
 * Domain model for an audio track. Cache-faithful: every field maps
 * 1:1 to a column on [com.eight87.tonearmboy.data.db.TrackEntity], so
 * a Track loaded from cache and a Track produced by `Mapping.toDomain`
 * carry exactly the same data.
 *
 * R.F.4 — scan-only fields (album-level ReplayGain, multi-value-splitter
 * outputs) live on [ScannedTrack], a separate scanner-output type. The
 * pre-R.F.4 contract drift, where [Track] carried defaultable scan-only
 * fields that the cache silently dropped, is gone. (Data-F4 + F10.)
 */
data class Track(
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
  /** D.9b.1 — track-level ReplayGain in dB. Null when missing. */
  val replayGainTrackDb: Float? = null,
  val replayGainTrackPeak: Float? = null,
  /**
   * D.9b.3 — MediaStore album id, captured at scan time so the UI can
   * resolve cover-art via the album-art content provider.
   */
  val mediaStoreAlbumId: Long? = null,
)
