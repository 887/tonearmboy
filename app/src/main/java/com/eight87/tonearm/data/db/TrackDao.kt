package com.eight87.tonearm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

  @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
  fun observeAll(): Flow<List<TrackEntity>>

  @Query("SELECT * FROM tracks WHERE id = :id")
  suspend fun getById(id: Long): TrackEntity?

  @Query("SELECT id FROM tracks")
  suspend fun allIds(): List<Long>

  @Query("SELECT * FROM tracks WHERE id IN (:ids)")
  suspend fun getByIds(ids: List<Long>): List<TrackEntity>

  @Upsert
  suspend fun upsert(tracks: List<TrackEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(tracks: List<TrackEntity>)

  @Query("DELETE FROM tracks WHERE id IN (:ids)")
  suspend fun deleteByIds(ids: List<Long>)

  @Query("DELETE FROM tracks")
  suspend fun deleteAll()

  /**
   * FTS4 MATCH search. Falls back to LIKE on the same columns when the
   * MATCH expression rejects the input (e.g. a single dangling quote);
   * see [LibraryDao.searchTracks] for the wrapped form.
   */
  @Query(
    """
    SELECT t.* FROM tracks t
    JOIN tracks_fts fts ON fts.rowid = t.id
    WHERE tracks_fts MATCH :match
    ORDER BY t.title COLLATE NOCASE ASC
    """
  )
  fun searchFts(match: String): Flow<List<TrackEntity>>

  @Query(
    """
    SELECT * FROM tracks
    WHERE title LIKE :pattern COLLATE NOCASE
       OR artist LIKE :pattern COLLATE NOCASE
       OR album LIKE :pattern COLLATE NOCASE
    ORDER BY title COLLATE NOCASE ASC
    """
  )
  fun searchLike(pattern: String): Flow<List<TrackEntity>>
}
