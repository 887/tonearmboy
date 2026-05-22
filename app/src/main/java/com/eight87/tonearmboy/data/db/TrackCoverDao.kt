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

  /** Every pinned row at once. Empty `coverUri` (intentionally-empty
   *  choice) is filtered at the call site. */
  @Query("SELECT * FROM track_covers WHERE coverUri IS NOT NULL")
  suspend fun allPinned(): List<TrackCoverEntity>

  /** Hot flow of every pinned row. Re-emits on any change to the
   *  `track_covers` table so the library cover-URI map stays in sync
   *  when the user pins / clears covers at runtime. */
  @Query("SELECT * FROM track_covers WHERE coverUri IS NOT NULL")
  fun observeAllPinned(): Flow<List<TrackCoverEntity>>
}
