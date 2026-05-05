package com.eight87.tonearmboy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumCoverDao {
  /**
   * Phase A — emits the row for [albumKey], or null when no row
   * exists. Callers map:
   *   - null entity                    → "no user choice"
   *   - entity.coverUri non-null/blank → pinned URI
   *   - entity.coverUri null/blank     → "intentionally empty"
   *
   * Tri-state because nullable URIs are not enough: row missing
   * (no choice) and row present with null URI (chose to clear) need
   * to mean different things to the upcoming auto-fetch pass.
   */
  @Query("SELECT * FROM album_covers WHERE albumKey = :albumKey")
  fun row(albumKey: String): Flow<AlbumCoverEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: AlbumCoverEntity)

  @Query("DELETE FROM album_covers WHERE albumKey = :albumKey")
  suspend fun delete(albumKey: String)
}
