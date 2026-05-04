package com.eight87.tonearmboy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * D.18.1 — CRUD + reorder for user-defined library tabs.
 *
 * Reorder is a single transaction so observers fire once instead of
 * once per row, and the table is never observed in a half-shuffled
 * state by the rail.
 */
@Dao
interface CustomTabDao {

  @Query("SELECT * FROM custom_tabs ORDER BY position ASC")
  fun observeAll(): Flow<List<CustomTabEntity>>

  @Query("SELECT * FROM custom_tabs ORDER BY position ASC")
  suspend fun getAll(): List<CustomTabEntity>

  @Query("SELECT * FROM custom_tabs WHERE id = :id")
  suspend fun getById(id: Long): CustomTabEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(tab: CustomTabEntity): Long

  @Update
  suspend fun update(tab: CustomTabEntity)

  @Query("DELETE FROM custom_tabs WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("UPDATE custom_tabs SET position = :position WHERE id = :id")
  suspend fun updatePosition(id: Long, position: Int)

  @Query("SELECT MAX(position) FROM custom_tabs")
  suspend fun maxPosition(): Int?

  /**
   * Atomically rewrite the position column for every supplied id so
   * the new ordering matches `orderedIds`. Ids absent from the list
   * are left untouched (they'll trail the explicit ones in their old
   * order, which is the same shape `replaceJoins` uses for playlists).
   */
  @Transaction
  suspend fun reorder(orderedIds: List<Long>) {
    orderedIds.forEachIndexed { index, id ->
      updatePosition(id, index)
    }
  }
}
