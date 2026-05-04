package com.eight87.tonearmboy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(genres: List<GenreEntity>)

  @Upsert
  suspend fun upsertAll(genres: List<GenreEntity>)

  @Query("DELETE FROM genres")
  suspend fun deleteAll()

  data class GenreWithCount(
    val id: Long,
    val name: String,
    val trackCount: Int,
  )

  @Query(
    """
    SELECT g.id AS id, g.name AS name, COUNT(t.id) AS trackCount
    FROM genres g
    LEFT JOIN tracks t ON t.genre = g.name
    GROUP BY g.id
    ORDER BY g.name COLLATE NOCASE ASC
    """
  )
  fun observeGenresWithCounts(): Flow<List<GenreWithCount>>
}
