package com.eight87.tonearmboy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackCoverDao {
  @Query("SELECT * FROM track_covers WHERE trackId = :id")
  fun row(id: Long): Flow<TrackCoverEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: TrackCoverEntity)

  @Query("DELETE FROM track_covers WHERE trackId = :id")
  suspend fun delete(id: Long)
}
