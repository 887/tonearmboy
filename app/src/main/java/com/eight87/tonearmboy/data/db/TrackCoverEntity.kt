package com.eight87.tonearmboy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * album-art R1 — per-track cover override. Same tri-state shape as
 * [AlbumCoverEntity]: row missing = default chain, row + URI =
 * pinned, row + null URI = intentionally empty.
 */
@Entity(tableName = "track_covers")
data class TrackCoverEntity(
  @PrimaryKey val trackId: Long,
  val coverUri: String?,
)
