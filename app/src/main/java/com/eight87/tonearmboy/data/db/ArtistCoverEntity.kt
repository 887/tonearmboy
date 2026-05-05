package com.eight87.tonearmboy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * album-art R3 — per-artist cover override. Keyed by lowercase
 * artist name (`artistKey()` does the normalisation). Tri-state same
 * as [AlbumCoverEntity] / [TrackCoverEntity].
 */
@Entity(tableName = "artist_covers")
data class ArtistCoverEntity(
  @PrimaryKey val artistKey: String,
  val coverUri: String?,
)
