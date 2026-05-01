package com.eight87.tonearm.data.model

data class Playlist(
  val id: Long,
  val name: String,
  val trackCount: Int,
  val createdAtSeconds: Long,
)

data class PlaylistWithTracks(
  val playlist: Playlist,
  val tracks: List<Track>,
)
