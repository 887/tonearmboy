package com.eight87.tonearm.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import com.eight87.tonearm.data.Mapping.toDomain
import com.eight87.tonearm.data.Mapping.toEntity
import com.eight87.tonearm.data.db.LibraryDatabase
import com.eight87.tonearm.data.db.PlaylistEntity
import com.eight87.tonearm.data.db.SearchExpressions
import com.eight87.tonearm.data.db.TrackEntity
import com.eight87.tonearm.data.mediastore.MediaStoreScanner
import com.eight87.tonearm.data.model.Album
import com.eight87.tonearm.data.model.Artist
import com.eight87.tonearm.data.model.Genre
import com.eight87.tonearm.data.model.Playlist
import com.eight87.tonearm.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single source of truth for the library. Owns the [MediaStoreScanner]
 * (the device's authoritative metadata) and the [LibraryDatabase] (the
 * cached queryable mirror), and exposes Flows that emit whenever the
 * underlying tables change.
 *
 * The repository performs the initial scan on construction-and-first-
 * subscription, then keeps the cache in sync via a
 * [ContentObserver] registered on the audio collection URI. Observer
 * callbacks are debounced to coalesce the bursts MediaStore emits when
 * a directory of files is indexed in one shot.
 *
 * No DI framework — `Context`, scanner, db, and scope are constructor
 * parameters, per the project convention.
 */
class LibraryRepository(
  private val context: Context,
  private val scanner: MediaStoreScanner = MediaStoreScanner(context),
  private val db: LibraryDatabase = LibraryDatabase.get(context),
  private val externalScope: CoroutineScope = MainScope(),
) {

  private val rescanRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
  private val initialScanDone = AtomicBoolean(false)
  private val scanMutex = Mutex()

  private val observerThread by lazy {
    HandlerThread("tonearm-mediastore-observer").apply { start() }
  }
  private val observerHandler by lazy { Handler(observerThread.looper) }
  private val mediaStoreObserver = object : ContentObserver(observerHandler) {
    override fun onChange(selfChange: Boolean, uri: Uri?) {
      // Coalesce bursts; the worker debounces.
      rescanRequests.tryEmit(Unit)
    }
  }

  init {
    @OptIn(FlowPreview::class)
    externalScope.launch(Dispatchers.IO) {
      rescanRequests
        .debounce(RESCAN_DEBOUNCE_MS)
        .onEach { runScan(initial = false) }
        .collect()
    }
    context.contentResolver.registerContentObserver(
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      /* notifyForDescendants = */ true,
      mediaStoreObserver,
    )
  }

  // -- Public surface --------------------------------------------------------

  fun observeTracks(): Flow<List<Track>> {
    ensureInitialScan()
    return db.trackDao().observeAll().map { rows -> rows.map { it.toDomain() } }
  }

  fun observeAlbums(): Flow<List<Album>> {
    ensureInitialScan()
    return db.albumDao().observeAlbumsWithCounts().map { rows ->
      rows.map { Album(it.id, it.name, it.artist, it.trackCount, it.year) }
    }
  }

  fun observeArtists(): Flow<List<Artist>> {
    ensureInitialScan()
    return db.artistDao().observeArtistsWithCounts().map { rows ->
      rows.map { Artist(it.id, it.name, it.albumCount, it.trackCount) }
    }
  }

  fun observeGenres(): Flow<List<Genre>> {
    ensureInitialScan()
    return db.genreDao().observeGenresWithCounts().map { rows ->
      rows.map { Genre(it.id, it.name, it.trackCount) }
    }
  }

  fun observePlaylists(): Flow<List<Playlist>> =
    db.playlistDao().observePlaylistsWithCounts().map { rows ->
      rows.map { Playlist(it.id, it.name, it.trackCount, it.createdAtSeconds) }
    }

  fun observePlaylistTracks(playlistId: Long): Flow<List<Track>> =
    db.playlistDao().observeTracks(playlistId).map { rows -> rows.map { it.toDomain() } }

  /**
   * Full-text search over (title, artist, album). Uses Room FTS4 when
   * possible, falls back to LIKE for inputs that are pure punctuation
   * or otherwise unsafe to feed to MATCH.
   */
  fun search(query: String): Flow<List<Track>> {
    val match = SearchExpressions.ftsMatch(query)
    val flow = if (match != null) {
      db.trackDao().searchFts(match)
    } else {
      val trimmed = query.trim()
      if (trimmed.isEmpty()) {
        return kotlinx.coroutines.flow.flowOf(emptyList())
      }
      db.trackDao().searchLike(SearchExpressions.likePattern(trimmed))
    }
    return flow.map { rows -> rows.map { it.toDomain() } }
  }

  /** Force a rescan now (used by the smoke test + the Settings UI in Phase D). */
  suspend fun rescanNow() {
    runScan(initial = false)
  }

  // -- Playlist CRUD ---------------------------------------------------------

  suspend fun createPlaylist(name: String, nowSeconds: Long = System.currentTimeMillis() / 1000): Long {
    return db.playlistDao().insert(
      PlaylistEntity(name = name, createdAtSeconds = nowSeconds),
    )
  }

  suspend fun deletePlaylist(playlistId: Long) {
    db.playlistDao().delete(playlistId)
  }

  suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
    val dao = db.playlistDao()
    val next = (dao.maxPosition(playlistId) ?: -1) + 1
    dao.insertJoin(
      com.eight87.tonearm.data.db.PlaylistTrackEntity(
        playlistId = playlistId,
        trackId = trackId,
        position = next,
      ),
    )
  }

  suspend fun removeTrackFromPlaylist(playlistId: Long, position: Int) {
    val dao = db.playlistDao()
    val joins = dao.rawJoins(playlistId).filter { it.position != position }
    dao.replaceJoins(playlistId, joins.map { it.trackId })
  }

  suspend fun reorderPlaylist(playlistId: Long, orderedTrackIds: List<Long>) {
    db.playlistDao().replaceJoins(playlistId, orderedTrackIds)
  }

  // -- Internals -------------------------------------------------------------

  private fun ensureInitialScan() {
    if (initialScanDone.compareAndSet(false, true)) {
      externalScope.launch(Dispatchers.IO) { runScan(initial = true) }
    }
  }

  private suspend fun runScan(initial: Boolean) = scanMutex.withLock {
    val snapshot = scanner.scanTracks().map { it.toEntity() }
    val snapshotIds = snapshot.map { it.id }.toHashSet()

    if (initial) {
      val albums = Mapping.deriveAlbums(snapshot)
      val artists = Mapping.deriveArtists(snapshot)
      val genres = Mapping.deriveGenres(snapshot)
      db.libraryDao().replaceAll(snapshot, albums, artists, genres)
      Log.i(TAG, "scan(initial): tracks=${snapshot.size}")
    } else {
      val cachedIds = db.trackDao().allIds().toHashSet()
      val removed = (cachedIds - snapshotIds).toList()
      val upserted = snapshot // simplest correct: REPLACE-on-id covers both new + changed
      val albums = Mapping.deriveAlbums(snapshot)
      val artists = Mapping.deriveArtists(snapshot)
      val genres = Mapping.deriveGenres(snapshot)
      db.libraryDao().applyDelta(removed, upserted, albums, artists, genres)
      Log.i(TAG, "scan(delta): added/updated=${upserted.size}, removed=${removed.size}")
    }
  }

  fun shutdown() {
    runCatching {
      context.contentResolver.unregisterContentObserver(mediaStoreObserver)
    }
    runCatching { observerThread.quitSafely() }
  }

  companion object {
    private const val TAG = "tonearm-library"
    private const val RESCAN_DEBOUNCE_MS = 750L
  }
}
