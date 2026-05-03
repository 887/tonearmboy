package com.eight87.tonearm.data

import android.net.Uri
import com.eight87.tonearm.data.db.CustomTabEntity
import com.eight87.tonearm.data.model.Album
import com.eight87.tonearm.data.model.Artist
import com.eight87.tonearm.data.model.Genre
import com.eight87.tonearm.data.model.Playlist
import com.eight87.tonearm.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * R.A.1 — eight narrow data-layer interfaces carved out of
 * [LibraryRepository]. Each interface bundles the read / write surface
 * a single category of UI consumer needs, so screens can take only the
 * slice they depend on (ISP) and the composition root maps interface
 * to concrete impl (DIP).
 *
 * The concrete [LibraryRepository] implements all eight; behaviour is
 * unchanged. The split is purely about narrowing the surface visible to
 * each call site.
 */

/**
 * Observe the cached track table and run track-level filter / search
 * queries against it. Detail screens, custom-tab content, and the
 * search screen consume this slice.
 */
interface TrackSource {
  fun observeTracks(): Flow<List<Track>>
  fun tracksMatching(criteria: FilterCriteria): Flow<List<Track>>
  fun search(query: String): Flow<List<Track>>
  suspend fun trackById(id: Long): Track?

  /**
   * Album-key album-level ReplayGain lookup. Lives on [TrackSource]
   * because the only consumer ([com.eight87.tonearm.playback.PlaybackUiController]
   * already takes a track-flow handle, and album-level RG is logically
   * a per-track-derived value at point of consumption.
   */
  suspend fun albumReplayGain(albumName: String?, albumArtist: String?): Pair<Float?, Float?>
  suspend fun trackCountForAlbum(albumName: String?, albumArtist: String?): Int
}

/**
 * Observe the cached album rollup. Album tab + album detail consume
 * this; the album-level filter Flow joins against tracks internally.
 */
interface AlbumSource {
  fun observeAlbums(): Flow<List<Album>>
  fun albumsMatching(criteria: FilterCriteria): Flow<List<Album>>
}

/**
 * Observe the derived artist rollup with optional collaborator
 * filtering. Artist tab + artist detail consume this slice.
 */
interface ArtistSource {
  fun observeArtists(): Flow<List<Artist>>
  fun observeArtists(hideCollaboratorsFlow: Flow<Boolean>): Flow<List<Artist>>
  fun artistsMatching(criteria: FilterCriteria): Flow<List<Artist>>
}

/**
 * Observe the cached genre rollup. Genre tab + genre detail consume
 * this.
 */
interface GenreSource {
  fun observeGenres(): Flow<List<Genre>>
  fun genresMatching(criteria: FilterCriteria): Flow<List<Genre>>
}

/**
 * Playlist read + CRUD + reorder + cover. The playlists tile screen,
 * playlist detail screen, and the add-to-playlist sheet consume this.
 */
interface PlaylistStore {
  fun observePlaylists(): Flow<List<Playlist>>
  fun observePlaylistTracks(playlistId: Long): Flow<List<Track>>
  fun observePlaylistFirstAlbumArt(playlistId: Long): Flow<Long?>
  suspend fun createPlaylist(name: String, nowSeconds: Long = System.currentTimeMillis() / 1000): Long
  suspend fun deletePlaylist(playlistId: Long)
  suspend fun renamePlaylist(playlistId: Long, name: String)
  suspend fun setPlaylistCoverUri(playlistId: Long, uri: String?)
  suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long)
  suspend fun removeTrackFromPlaylist(playlistId: Long, position: Int)
  suspend fun reorderPlaylist(playlistId: Long, orderedTrackIds: List<Long>)
}

/** Custom-tab CRUD + reorder. Library tabs settings + custom-tab content consume this. */
interface CustomTabStore {
  fun customTabs(): Flow<List<CustomTabEntity>>
  suspend fun upsertCustomTab(tab: CustomTabEntity): Long
  suspend fun deleteCustomTab(id: Long)
  suspend fun reorderCustomTabs(orderedIds: List<Long>)
}

/**
 * Trigger a rescan and observe progress. The settings library screen,
 * the rescan worker, and the deletion flow consume this.
 */
interface LibraryScanner {
  val scanProgress: StateFlow<LibraryRepository.ScanProgress?>
  suspend fun rescanNow()

  /**
   * Phase F.4 — invalidate the cache after the system has deleted the
   * underlying audio files. Lives on the scanner because it triggers
   * a debounced rescan as part of its work.
   */
  suspend fun onTracksDeleted(uris: List<Uri>)
}

/**
 * `Flow<Unit>` of content-observer change events from MediaStore. Each
 * tick means "MediaStore reports the audio collection mutated; the
 * cache is now potentially stale".
 *
 * Currently latent — the only internal consumer is the debounced
 * rescan loop inside [LibraryRepository]. Surfaced as a narrow
 * interface so future external consumers (e.g. an in-foreground
 * rescan-button badge) can subscribe without depending on the whole
 * repository.
 */
interface MediaChangeSource {
  val mediaChanges: Flow<Unit>
}
