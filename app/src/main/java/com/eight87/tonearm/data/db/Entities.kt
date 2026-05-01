package com.eight87.tonearm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached representation of a MediaStore audio row.
 *
 * Primary key is the MediaStore `_ID`, which lets us delete + upsert
 * cheaply during incremental rescans without keeping a side-mapping.
 */
@Entity(
  tableName = "tracks",
  indices = [
    Index("title"),
    Index("artist"),
    Index("album"),
    Index("albumArtist"),
    Index("genre"),
  ],
)
data class TrackEntity(
  @PrimaryKey val id: Long,
  val title: String,
  val artist: String?,
  val album: String?,
  val albumArtist: String?,
  val durationMs: Long,
  val trackNumber: Int?,
  val year: Int?,
  val genre: String?,
  /** Absolute filesystem path (`MediaStore.Audio.Media.DATA`). */
  val data: String,
  val dateAddedSeconds: Long,
)

/**
 * FTS4 mirror of [TrackEntity]. Used by [com.eight87.tonearm.data.LibraryRepository.search].
 *
 * Declared as a content-entity FTS table — Room keeps the FTS row in
 * sync with the contentEntity by mirroring inserts and deletes
 * (`rowid` is matched to the content table's primary key column).
 */
@Fts4(contentEntity = TrackEntity::class)
@Entity(tableName = "tracks_fts")
data class TrackFts(
  val title: String,
  val artist: String?,
  val album: String?,
)

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
)

@Entity(
  tableName = "artists",
  indices = [Index(value = ["name"], unique = true)],
)
data class ArtistEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
)

@Entity(
  tableName = "genres",
  indices = [Index(value = ["name"], unique = true)],
)
data class GenreEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
)

@Entity(
  tableName = "playlists",
  indices = [Index(value = ["name"], unique = true)],
)
data class PlaylistEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAtSeconds: Long,
)

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
