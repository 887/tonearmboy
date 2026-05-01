package com.eight87.tonearm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Albums are derived from tracks rather than maintained independently —
 * the repository rebuilds the [albums] table after each scan and exposes
 * a SELECT that joins the cached counts back in.
 */
@Dao
interface AlbumDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(albums: List<AlbumEntity>)

  @Upsert
  suspend fun upsertAll(albums: List<AlbumEntity>)

  @Query("DELETE FROM albums")
  suspend fun deleteAll()

  @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE ASC")
  suspend fun all(): List<AlbumEntity>

  data class AlbumWithCount(
    val id: Long,
    val name: String,
    val artist: String?,
    val year: Int?,
    val trackCount: Int,
  )

  @Query(
    """
    SELECT a.id AS id, a.name AS name, a.artist AS artist, a.year AS year,
           COUNT(t.id) AS trackCount
    FROM albums a
    LEFT JOIN tracks t
      ON t.album = a.name AND IFNULL(t.albumArtist, t.artist) IS a.artist
    GROUP BY a.id
    ORDER BY a.name COLLATE NOCASE ASC
    """
  )
  fun observeAlbumsWithCounts(): Flow<List<AlbumWithCount>>
}
