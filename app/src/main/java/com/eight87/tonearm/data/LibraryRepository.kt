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
import com.eight87.tonearm.data.db.CustomTabEntity
import com.eight87.tonearm.data.db.LibraryDatabase
import com.eight87.tonearm.data.db.PlaylistEntity
import com.eight87.tonearm.data.db.SearchExpressions
import com.eight87.tonearm.data.db.TrackEntity
import com.eight87.tonearm.data.mediastore.MediaStoreScanner
import com.eight87.tonearm.data.mediastore.SafScopeMapping
import com.eight87.tonearm.data.model.Album
import com.eight87.tonearm.data.model.Artist
import com.eight87.tonearm.data.model.Genre
import com.eight87.tonearm.data.model.Playlist
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.ui.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
  /**
   * D.9c.1 — read on every scan to apply the user's multi-value
   * separator selection. Defaults to a fresh [SettingsRepository] so
   * existing call sites keep working without ceremony.
   */
  private val settingsRepository: SettingsRepository = SettingsRepository(context),
) {

  private val rescanRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
  private val initialScanDone = AtomicBoolean(false)
  private val scanMutex = Mutex()

  /**
   * Per-track progress emitted as the scanner walks MediaStore.
   * `null` means no scan is in flight; non-null means UI should render
   * a progress indicator. The StateFlow is conflated, so a UI consumer
   * sees the latest value and never blocks the scanner producer even
   * if it's recomposing slowly.
   */
  data class ScanProgress(
    val scanned: Int,
    val total: Int,
    val currentTitle: String?,
  ) {
    val fraction: Float get() = if (total <= 0) 0f else scanned.toFloat() / total.toFloat()
  }
  private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
  val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

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
      rows.map {
        Album(
          id = it.id,
          name = it.name,
          artist = it.artist,
          trackCount = it.trackCount,
          year = it.year,
          mediaStoreAlbumId = it.mediaStoreAlbumId,
        )
      }
    }
  }

  fun observeArtists(): Flow<List<Artist>> = observeArtists(flowOf(false))

  /**
   * D.9a.6 — when [hideCollaboratorsFlow] emits `true`, the artist list
   * is derived purely from each track's `albumArtist` (falling back to
   * `artist` when album-artist is missing). When it emits `false`, the
   * derivation also includes `artist` values that differ from the
   * primary `albumArtist` on the same track — i.e. featured collaborators.
   *
   * The toggle re-emits the new artist list without rescanning: the
   * Flow combines the cached track table with the settings Flow and
   * recomputes the rollup at query time. This is fine for small / mid
   * libraries; if the user has tens of thousands of tracks we'd switch
   * to two precomputed views in the DB.
   */
  fun observeArtists(hideCollaboratorsFlow: Flow<Boolean>): Flow<List<Artist>> {
    ensureInitialScan()
    val tracks = db.trackDao().observeAll()
    return combine(tracks, hideCollaboratorsFlow) { rows, hide ->
      deriveArtistsFromTracks(rows, hide)
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
      rows.map { Playlist(it.id, it.name, it.trackCount, it.createdAtSeconds, it.coverUri) }
    }

  fun observePlaylistTracks(playlistId: Long): Flow<List<Track>> =
    db.playlistDao().observeTracks(playlistId).map { rows -> rows.map { it.toDomain() } }

  /**
   * D.27.6 — observe the first-track album-art id for a playlist tile.
   * Returns null when the playlist is empty or none of its tracks has
   * album art. Used by the tile cover-resolution chain.
   */
  fun observePlaylistFirstAlbumArt(playlistId: Long): Flow<Long?> =
    db.playlistDao().observeFirstTrackAlbumId(playlistId)

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

  /**
   * Phase F.4 — invalidate the cache after the system has deleted the
   * underlying audio files. The caller (the Compose layer that owns
   * the consent-launcher result) hands us the list of MediaStore
   * content URIs the user agreed to delete; we strip the trailing id
   * from each one and run a single Room transaction that:
   *
   * 1. removes those rows from the `tracks` table,
   * 2. relies on the `playlist_tracks` cascade to clean up join rows,
   * 3. queues a debounced rescan so the rollup tables (albums /
   *    artists / genres) reconcile against the new track set without
   *    blocking the UI thread.
   *
   * Idempotent — invoking with an empty / already-dropped id list is a
   * no-op.
   */
  suspend fun onTracksDeleted(uris: List<Uri>) {
    if (uris.isEmpty()) return
    val ids = uris.mapNotNull { it.lastPathSegment?.toLongOrNull() }
    if (ids.isEmpty()) {
      // Still trigger a rescan — the URIs may not have been MediaStore
      // ids (caller passed file URIs, etc.) so the safest fallback is
      // to let MediaStore tell us what is actually gone.
      rescanRequests.tryEmit(Unit)
      return
    }
    db.trackDao().deleteByIds(ids)
    // Don't rebuild the rollups here synchronously — the
    // ContentObserver's MediaStore callback fires on file removal and
    // the debounced rescan picks them up. Emit one anyway in case the
    // observer hasn't run yet (e.g. the user denied the consent dialog
    // for one file in a batch but accepted others).
    rescanRequests.tryEmit(Unit)
  }

  /**
   * D.9b.1 — look up the album-level ReplayGain dB / peak for the
   * given (album name, album artist) pair. Returns
   * `(null, null)` when the album row has no album-level tags or no
   * row exists. The `albumArtist` lookup falls back to the per-track
   * artist when album-artist is missing — same precedence as
   * `Mapping.deriveAlbums` uses to compose the unique album key.
   */
  suspend fun albumReplayGain(albumName: String?, albumArtist: String?): Pair<Float?, Float?> {
    if (albumName.isNullOrBlank()) return null to null
    val row = db.albumDao().byNameAndArtist(albumName, albumArtist)
    return row?.replayGainAlbumDb to row?.replayGainAlbumPeak
  }

  /** Track-by-id retrieval for the playback gain pipeline. */
  suspend fun trackById(id: Long): Track? =
    db.trackDao().getById(id)?.toDomain()

  /**
   * D.9b.1 — count of tracks for a given album key. Used to compute
   * how much of the album the queue covers in Smart mode.
   */
  suspend fun trackCountForAlbum(albumName: String?, albumArtist: String?): Int {
    if (albumName.isNullOrBlank()) return 0
    return db.trackDao().countForAlbum(albumName, albumArtist)
  }

  // -- Custom tabs (D.18) -----------------------------------------------------

  /** Live, position-ordered list of user-defined library tabs. */
  fun customTabs(): Flow<List<CustomTabEntity>> = db.customTabDao().observeAll()

  /**
   * D.18.1 — tracks that match [criteria]. Filtering happens in
   * Kotlin against the live track Flow, on the IO dispatcher (matching
   * is cheap; the Flow is already cold-mapped on `Dispatchers.IO`
   * inside the DAO). For empty criteria this is the all-tracks Flow.
   */
  fun tracksMatching(criteria: FilterCriteria): Flow<List<Track>> {
    val all = observeTracks()
    if (criteria.isEmpty()) return all
    return all.map { list -> list.filter { criteria.matchesTrack(it) } }
  }

  fun albumsMatching(criteria: FilterCriteria): Flow<List<Album>> {
    if (criteria.isEmpty()) return observeAlbums()
    val tracks = observeTracks()
    val albums = observeAlbums()
    return combine(tracks, albums) { tracksList, albumsList ->
      // Group tracks by album-key (name + artist). Match each album row
      // against the predicate via its tracks.
      val grouped = tracksList.groupBy { (it.album ?: "") to ((it.albumArtist ?: it.artist) ?: "") }
      albumsList.filter { album ->
        val key = (album.name) to (album.artist ?: "")
        val tracksOfAlbum = grouped[key].orEmpty()
        criteria.matchesAlbum(album, tracksOfAlbum)
      }
    }
  }

  fun artistsMatching(criteria: FilterCriteria): Flow<List<Artist>> {
    if (criteria.isEmpty()) return observeArtists()
    val tracks = observeTracks()
    val artists = observeArtists()
    return combine(tracks, artists) { tracksList, artistsList ->
      artistsList.filter { artist ->
        val tracksOfArtist = tracksList.filter {
          val a = it.albumArtist?.takeIf { v -> v.isNotBlank() } ?: it.artist
          a.equals(artist.name, ignoreCase = true)
        }
        criteria.matchesArtist(artist, tracksOfArtist)
      }
    }
  }

  fun genresMatching(criteria: FilterCriteria): Flow<List<Genre>> {
    if (criteria.isEmpty()) return observeGenres()
    val tracks = observeTracks()
    val genres = observeGenres()
    return combine(tracks, genres) { tracksList, genresList ->
      genresList.filter { genre ->
        val tracksOfGenre = tracksList.filter { it.genre.equals(genre.name, ignoreCase = true) }
        criteria.matchesGenre(genre, tracksOfGenre)
      }
    }
  }

  /**
   * Insert or update [tab]. If the tab is new (id == 0), the position
   * defaults to the end of the list when [tab.position] is 0 and a
   * row already exists at that position — preserves "newest goes
   * last" without forcing the caller to query maxPosition first.
   */
  suspend fun upsertCustomTab(tab: CustomTabEntity): Long {
    val dao = db.customTabDao()
    val resolved = if (tab.id == 0L && tab.position == 0) {
      val next = (dao.maxPosition() ?: -1) + 1
      tab.copy(position = next)
    } else tab
    return dao.upsert(resolved)
  }

  suspend fun deleteCustomTab(id: Long) {
    db.customTabDao().delete(id)
  }

  suspend fun reorderCustomTabs(orderedIds: List<Long>) {
    db.customTabDao().reorder(orderedIds)
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

  suspend fun renamePlaylist(playlistId: Long, name: String) {
    db.playlistDao().rename(playlistId, name)
  }

  /**
   * D.27.6 — store an optional cover URI for the playlist tile.
   * Pass null to clear (fall back to album-art / letter chain). The
   * caller is responsible for taking a persistable URI permission on
   * SAF-picked images before passing the URI through here.
   */
  suspend fun setPlaylistCoverUri(playlistId: Long, uri: String?) {
    db.playlistDao().setCoverUri(playlistId, uri)
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
    // D.9c.1 — read the latest separator selection (a one-shot, not a
    // subscription) so re-scans triggered after the user toggles the
    // setting see the new tokens. Existing-cache invalidation is the
    // user's job: we surface a snackbar telling them to rescan.
    val separators: Set<String> =
      settingsRepository.multiValueSeparators.first().map { it.token }.toSet()

    // D.9d.1 / D.17.3 — when the user has configured one or more music
    // sources AND the mode is FilePicker, restrict the scan to
    // MediaStore rows whose `DATA` path lives under one of the source
    // trees. In System mode we deliberately ignore any persisted SAF
    // tree URIs so toggling System wipes the scope without losing the
    // user's saved folder list.
    val mode = settingsRepository.musicSourceMode.first()
    val scopePrefixes: Set<String> = when (mode) {
      com.eight87.tonearm.ui.settings.MusicSourceMode.FilePicker ->
        settingsRepository.musicSourceUris.first()
          .mapNotNull { raw -> SafScopeMapping.treeUriToPathPrefix(Uri.parse(raw)) }
          .toSet()
      com.eight87.tonearm.ui.settings.MusicSourceMode.System -> emptySet()
    }

    // Snapshot the domain Track list so we keep the per-track album
    // ReplayGain values around long enough to fold them into the
    // derived AlbumEntity list. Track entities themselves only store
    // track-level fields; the album-level fields land on AlbumEntity.
    //
    // D.22.1 — throttle the per-track progress callback to ~5 Hz at
    // the producer. The scanner fires this on every track (a 157-track
    // library easily emits 50+/s on a fast disk), and `MutableStateFlow`
    // is conflated for *consumers* but not for *producers*: every
    // assignment still wakes up `collectAsStateWithLifecycle` and forces
    // Compose to recompute the bar's caption + every track row whose
    // surrounding `LazyColumn` recomposes when the `Scaffold` chrome
    // around the bar shifts height. Throttling here keeps the UI
    // thread free without losing user-visible progress fidelity.
    var lastEmit = 0L
    val tracksDomain = try {
      scanner.scanTracks(separators, scopePrefixes) { scanned, total, title ->
        val now = android.os.SystemClock.uptimeMillis()
        // Always emit the terminal frame (scanned == total) so the bar
        // settles on 100 % before being cleared in the `finally`.
        if (scanned == total || now - lastEmit >= SCAN_PROGRESS_THROTTLE_MS) {
          lastEmit = now
          _scanProgress.value = ScanProgress(scanned, total, title)
        }
      }
    } finally {
      _scanProgress.value = null
    }
    val snapshot = tracksDomain.map { it.toEntity() }
    val snapshotIds = snapshot.map { it.id }.toHashSet()

    val albumGainPairs: List<Pair<TrackEntity, Pair<Float?, Float?>>> =
      tracksDomain.zip(snapshot).map { (t, e) ->
        e to (t.replayGainAlbumDb to t.replayGainAlbumPeak)
      }

    if (initial) {
      val albums = Mapping.foldAlbumReplayGain(
        Mapping.deriveAlbums(snapshot), albumGainPairs,
      )
      val artists = Mapping.deriveArtistsFromDomain(tracksDomain)
      val genres = Mapping.deriveGenresFromDomain(tracksDomain)
      db.libraryDao().replaceAll(snapshot, albums, artists, genres)
      Log.i(TAG, "scan(initial): tracks=${snapshot.size}")
    } else {
      val cachedIds = db.trackDao().allIds().toHashSet()
      val removed = (cachedIds - snapshotIds).toList()
      val upserted = snapshot // simplest correct: REPLACE-on-id covers both new + changed
      val albums = Mapping.foldAlbumReplayGain(
        Mapping.deriveAlbums(snapshot), albumGainPairs,
      )
      val artists = Mapping.deriveArtistsFromDomain(tracksDomain)
      val genres = Mapping.deriveGenresFromDomain(tracksDomain)
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

    /**
     * D.22.1 — minimum spacing between per-track progress emissions on
     * [scanProgress]. 200 ms = ~5 Hz, the slowest cadence that still
     * looks live to a human watching the bar advance. Below this the
     * UI thread spends real time recomposing the caption "Scanning
     * 17 of 157 · …" on every track on a fast disk.
     */
    internal const val SCAN_PROGRESS_THROTTLE_MS = 200L

    /**
     * Build an artist list at query time, with optional collaborator
     * filtering. Visible for tests.
     *
     * The shape is deliberately the same as the cached
     * `observeArtistsWithCounts` query: one row per artist, with album
     * + track counts. We assign synthetic positive ids based on the
     * stable sort order so the UI's `key = it.id` stays stable across
     * Flow emissions.
     */
    internal fun deriveArtistsFromTracks(
      tracks: List<TrackEntity>,
      hideCollaborators: Boolean,
    ): List<Artist> {
      // Build a map artist-name -> (set of album names, track count).
      val acc = LinkedHashMap<String, Pair<HashSet<String>, Int>>()
      for (t in tracks) {
        val primary = (t.albumArtist?.takeIf { it.isNotBlank() }) ?: t.artist
        val names = if (hideCollaborators) {
          listOfNotNull(primary?.takeIf { it.isNotBlank() })
        } else {
          // Include both album-artist and artist when they differ.
          val secondary = t.artist?.takeIf {
            it.isNotBlank() && !it.equals(primary, ignoreCase = true)
          }
          listOfNotNull(primary?.takeIf { it.isNotBlank() }, secondary)
        }
        for (name in names) {
          val (albums, count) = acc[name] ?: (HashSet<String>() to 0)
          val nextAlbums = albums.also { t.album?.let(it::add) }
          acc[name] = nextAlbums to (count + 1)
        }
      }
      // Sort case-insensitively by name; assign deterministic ids by
      // hashing the name into a positive Long (stable across emissions).
      return acc
        .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        .entries
        .map { (name, agg) ->
          val (albums, count) = agg
          Artist(
            id = (name.hashCode().toLong() and 0xFFFFFFFFL),
            name = name,
            albumCount = albums.size,
            trackCount = count,
          )
        }
    }
  }
}
