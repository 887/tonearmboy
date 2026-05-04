package com.eight87.tonearmboy.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table between playlists and tracks. `position` is 0-indexed and
 * used by `reorderPlaylist`. Cascading delete on the playlist side; on
 * the track side we rely on application-level cleanup (the repository
 * removes the join row when a track disappears from MediaStore).
 */
@Entity(
  tableName = "playlist_tracks",
  primaryKeys = ["playlistId", "position"],
  indices = [
    Index("trackId"),
    Index(value = ["playlistId", "trackId"]),
  ],
  foreignKeys = [
    ForeignKey(
      entity = PlaylistEntity::class,
      parentColumns = ["id"],
      childColumns = ["playlistId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = TrackEntity::class,
      parentColumns = ["id"],
      childColumns = ["trackId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class PlaylistTrackEntity(
  val playlistId: Long,
  val trackId: Long,
  @ColumnInfo(name = "position") val position: Int,
)
