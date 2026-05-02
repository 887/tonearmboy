package com.eight87.tonearm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(playlist: PlaylistEntity): Long

  @Query("UPDATE playlists SET name = :name WHERE id = :id")
  suspend fun rename(id: Long, name: String)

  @Query("DELETE FROM playlists WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("SELECT * FROM playlists WHERE id = :id")
  suspend fun getById(id: Long): PlaylistEntity?

  @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
  suspend fun getByName(name: String): PlaylistEntity?

  @Query(
    """
    SELECT p.id AS id, p.name AS name, p.createdAtSeconds AS createdAtSeconds,
           p.coverUri AS coverUri,
           COUNT(pt.trackId) AS trackCount
    FROM playlists p
    LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
    GROUP BY p.id
    ORDER BY p.name COLLATE NOCASE ASC
    """
  )
  fun observePlaylistsWithCounts(): Flow<List<PlaylistWithCount>>

  data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAtSeconds: Long,
    val coverUri: String?,
    val trackCount: Int,
  )

  /**
   * D.27.6 — set or clear the playlist's cover image URI.
   */
  @Query("UPDATE playlists SET coverUri = :uri WHERE id = :id")
  suspend fun setCoverUri(id: Long, uri: String?)

  /**
   * D.27.6 — first track's MediaStore album id for the given playlist,
   * or null when the playlist is empty / no track has album art. Used
   * by the tile renderer's "fall back to first track's album art" path.
   */
  @Query(
    """
    SELECT t.mediaStoreAlbumId FROM tracks t
    JOIN playlist_tracks pt ON pt.trackId = t.id
    WHERE pt.playlistId = :playlistId AND t.mediaStoreAlbumId IS NOT NULL
    ORDER BY pt.position ASC
    LIMIT 1
    """
  )
  fun observeFirstTrackAlbumId(playlistId: Long): Flow<Long?>

  @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
  suspend fun maxPosition(playlistId: Long): Int?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertJoin(row: PlaylistTrackEntity)

  @Query(
    """
    DELETE FROM playlist_tracks
    WHERE playlistId = :playlistId AND position = :position
    """
  )
  suspend fun deleteJoinAt(playlistId: Long, position: Int)

  @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
  suspend fun clearJoins(playlistId: Long)

  @Query(
    """
    SELECT t.* FROM tracks t
    JOIN playlist_tracks pt ON pt.trackId = t.id
    WHERE pt.playlistId = :playlistId
    ORDER BY pt.position ASC
    """
  )
  fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

  @Query(
    """
    SELECT pt.position AS position, pt.trackId AS trackId
    FROM playlist_tracks pt
    WHERE pt.playlistId = :playlistId
    ORDER BY pt.position ASC
    """
  )
  suspend fun rawJoins(playlistId: Long): List<RawJoin>

  data class RawJoin(val position: Int, val trackId: Long)

  /**
   * Atomically: clear the playlist's join rows then re-insert them in
   * the supplied order. Used by both `addTrackToPlaylist` (after
   * computing the next position via [maxPosition]) and
   * `reorderPlaylist`.
   */
  @Transaction
  suspend fun replaceJoins(playlistId: Long, ordered: List<Long>) {
    clearJoins(playlistId)
    ordered.forEachIndexed { index, trackId ->
      insertJoin(PlaylistTrackEntity(playlistId = playlistId, trackId = trackId, position = index))
    }
  }
}
