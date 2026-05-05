package com.eight87.tonearmboy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistCoverDao {
  @Query("SELECT * FROM artist_covers WHERE artistKey = :key")
  fun row(key: String): Flow<ArtistCoverEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ArtistCoverEntity)

  @Query("DELETE FROM artist_covers WHERE artistKey = :key")
  suspend fun delete(key: String)
}
