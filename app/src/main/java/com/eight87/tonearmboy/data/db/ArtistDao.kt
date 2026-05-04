package com.eight87.tonearmboy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(artists: List<ArtistEntity>)

  @Upsert
  suspend fun upsertAll(artists: List<ArtistEntity>)

  @Query("DELETE FROM artists")
  suspend fun deleteAll()

  @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE ASC")
  suspend fun all(): List<ArtistEntity>

  data class ArtistWithCount(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
  )

  @Query(
    """
    SELECT a.id AS id, a.name AS name,
           COUNT(DISTINCT t.album) AS albumCount,
           COUNT(t.id) AS trackCount
    FROM artists a
    LEFT JOIN tracks t
      ON IFNULL(t.albumArtist, t.artist) = a.name
    GROUP BY a.id
    ORDER BY a.name COLLATE NOCASE ASC
    """
  )
  fun observeArtistsWithCounts(): Flow<List<ArtistWithCount>>
}
