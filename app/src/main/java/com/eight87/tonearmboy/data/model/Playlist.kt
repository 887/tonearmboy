package com.eight87.tonearmboy.data.model

data class Playlist(
  val id: Long,
  val name: String,
  val trackCount: Int,
  val createdAtSeconds: Long,
  /**
   * D.27.6 — optional cover URI selected by the user (SAF picker) or
   * derived from a track's album art. Null = fall back to first
   * track's album art > letter avatar.
   */
  val coverUri: String? = null,
)

data class PlaylistWithTracks(
  val playlist: Playlist,
  val tracks: List<Track>,
)
