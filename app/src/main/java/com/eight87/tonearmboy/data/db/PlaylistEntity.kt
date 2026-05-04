package com.eight87.tonearmboy.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "playlists",
  indices = [Index(value = ["name"], unique = true)],
)
data class PlaylistEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAtSeconds: Long,
  /**
   * D.27.6 — optional cover image URI for the playlist tile. Stored as
   * an opaque string so we can hold either a `content://` SAF URI
   * (user-picked image, persisted via `takePersistableUriPermission`)
   * or a `mediastore://` form pointing at a track's album-art id. Null
   * means: fall back to the first track's album art, then the
   * letter-avatar default. The chooser sheet writes this column via
   * [com.eight87.tonearmboy.data.LibraryRepository.setPlaylistCoverUri].
   */
  val coverUri: String? = null,
)
