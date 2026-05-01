package com.eight87.tonearm.data.model

/**
 * Domain model for an audio track. Constructed from the MediaStore audio
 * cursor and persisted via [com.eight87.tonearm.data.db.TrackEntity].
 *
 * The library exposes domain models, never raw cursors or Room entities,
 * to the UI / playback layers.
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
)
