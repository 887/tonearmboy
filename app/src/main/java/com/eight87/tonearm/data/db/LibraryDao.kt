package com.eight87.tonearm.data.db

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
   * Atomically replace the cached track set + derived rollups
   * (albums / artists / genres). Used by the repository after a full
   * scan; for incremental updates we use [applyDelta].
   */
  @Transaction
  suspend fun replaceAll(
    tracks: List<TrackEntity>,
    albums: List<AlbumEntity>,
    artists: List<ArtistEntity>,
    genres: List<GenreEntity>,
  ) {
    deleteAllTracks()
    deleteAllAlbums()
    deleteAllArtists()
    deleteAllGenres()
    insertTracks(tracks)
    insertAlbums(albums)
    insertArtists(artists)
    insertGenres(genres)
  }

  /**
   * Apply an incremental change: remove gone ids, upsert added/changed
   * rows, then refresh the rollup tables. Run inside a single Room
   * transaction so observers fire once.
   */
  @Transaction
  suspend fun applyDelta(
    removed: List<Long>,
    upserted: List<TrackEntity>,
    albums: List<AlbumEntity>,
    artists: List<ArtistEntity>,
    genres: List<GenreEntity>,
  ) {
    if (removed.isNotEmpty()) deleteTracksById(removed)
    if (upserted.isNotEmpty()) insertTracks(upserted)
    deleteAllAlbums()
    deleteAllArtists()
    deleteAllGenres()
    insertAlbums(albums)
    insertArtists(artists)
    insertGenres(genres)
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
