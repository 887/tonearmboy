package com.eight87.tonearmboy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Cross-entity DAO for queries that span multiple tables in shapes the
 * single-entity DAOs don't cover cleanly.
 */
@Dao
interface LibraryDao {

  /**
   * Atomically replace the cached track set + derived rollups. Used
   * by the repository after a full scan; for incremental updates use
   * [applyDelta]. (R.F.6 / Data-F9.)
   */
  @Transaction
  suspend fun replaceAll(snapshot: LibrarySnapshot) {
    deleteAllTracks()
    deleteAllAlbums()
    deleteAllArtists()
    deleteAllGenres()
    insertTracks(snapshot.tracks)
    insertAlbums(snapshot.albums)
    insertArtists(snapshot.artists)
    insertGenres(snapshot.genres)
  }

  /**
   * Apply an incremental change: remove gone ids, upsert added/changed
   * rows, then refresh the rollup tables. Run inside a single Room
   * transaction so observers fire once. (R.F.6 / Data-F9.)
   */
  @Transaction
  suspend fun applyDelta(delta: LibraryDelta) {
    if (delta.removedTrackIds.isNotEmpty()) deleteTracksById(delta.removedTrackIds)
    if (delta.upsertedTracks.isNotEmpty()) insertTracks(delta.upsertedTracks)
    deleteAllAlbums()
    deleteAllArtists()
    deleteAllGenres()
    insertAlbums(delta.albums)
    insertArtists(delta.artists)
    insertGenres(delta.genres)
  }

  @Query("DELETE FROM tracks") suspend fun deleteAllTracks()
  @Query("DELETE FROM albums") suspend fun deleteAllAlbums()
  @Query("DELETE FROM artists") suspend fun deleteAllArtists()
  @Query("DELETE FROM genres") suspend fun deleteAllGenres()

  @Query("DELETE FROM tracks WHERE id IN (:ids)")
  suspend fun deleteTracksById(ids: List<Long>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTracks(tracks: List<TrackEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAlbums(albums: List<AlbumEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertArtists(artists: List<ArtistEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertGenres(genres: List<GenreEntity>)
}
