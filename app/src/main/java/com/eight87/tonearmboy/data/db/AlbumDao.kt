package com.eight87.tonearmboy.data.db

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

  /**
   * D.9b.1 — look up an album by (name, artist) to retrieve its
   * ReplayGain album-level dB / peak. The (name, artist) pair is
   * unique per the table's index. `IS` lets the query match when
   * `:artist` is null (NULL = NULL is false in SQL, which is wrong
   * here — there genuinely is one album per artist key, including
   * the null-artist key for various-artists rips).
   */
  @Query("SELECT * FROM albums WHERE name = :name AND artist IS :artist LIMIT 1")
  suspend fun byNameAndArtist(name: String, artist: String?): AlbumEntity?

  data class AlbumWithCount(
    val id: Long,
    val name: String,
    val artist: String?,
    val year: Int?,
    val trackCount: Int,
    val mediaStoreAlbumId: Long?,
  )

  @Query(
    """
    SELECT a.id AS id, a.name AS name, a.artist AS artist, a.year AS year,
           COUNT(t.id) AS trackCount, a.mediaStoreAlbumId AS mediaStoreAlbumId
    FROM albums a
    LEFT JOIN tracks t
      ON t.album = a.name AND IFNULL(t.albumArtist, t.artist) IS a.artist
    GROUP BY a.id
    ORDER BY a.name COLLATE NOCASE ASC
    """
  )
  fun observeAlbumsWithCounts(): Flow<List<AlbumWithCount>>
}
