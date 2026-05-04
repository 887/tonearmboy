package com.eight87.tonearmboy.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Albums are derived (album_name + albumArtist), but cached as a row to
 * keep the join cheap and to give the UI a stable id for navigation.
 */
@Entity(
  tableName = "albums",
  indices = [Index(value = ["name", "artist"], unique = true)],
)
data class AlbumEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val artist: String?,
  val year: Int?,
  /**
   * D.9b.1 — album-level ReplayGain in dB. The album-tagged value is
   * the same on every track of an album; deriveAlbums picks the first
   * non-null value seen across the album's tracks.
   */
  val replayGainAlbumDb: Float? = null,
  /** D.9b.1 — album-level peak (linear). Null when missing. */
  val replayGainAlbumPeak: Float? = null,
  /**
   * D.9b.3 — MediaStore album id used for the album-art content URI.
   * Folded in from the per-track `ALBUM_ID` during scan; the standard
   * holds that all tracks of an album share the same id, so we keep
   * the first non-null value seen.
   */
  val mediaStoreAlbumId: Long? = null,
)
