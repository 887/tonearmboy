package com.eight87.tonearm.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.playback.replaygain.computeGain
import com.eight87.tonearm.playback.replaygain.linearGainFromDb
import com.eight87.tonearm.ui.settings.CustomBarAction
import com.eight87.tonearm.ui.settings.PlayFromItemDetails
import com.eight87.tonearm.ui.settings.PlayFromLibrary
import com.eight87.tonearm.ui.settings.ReplayGainStrategy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI-facing wrapper around the Media3 [MediaController]. Owns the
 * connection lifecycle, exposes playback state as a Compose-friendly
 * [StateFlow], and routes UI commands (play / pause / seek / load
 * track) to the controller.
 *
 * Per the Phase B design, [PlaybackController] is the *connection*
 * helper; this class is the *UI driver* that subscribes to the
 * connected controller's events and projects them as state.
 */
@UnstableApi
class PlaybackUiController(private val applicationContext: Context) {

  private val _state = MutableStateFlow(PlaybackUiState.Empty)
  val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

  /**
   * D.15.5 — snapshot of the current MediaController queue, recomputed
   * whenever items change or the playing index advances. The queue
   * sheet observes this Flow to render rows + the "now playing" marker.
   */
  private val _queue = MutableStateFlow(QueueSnapshot.Empty)
  val queue: StateFlow<QueueSnapshot> = _queue.asStateFlow()

  private var controller: MediaController? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var positionTickerStarted = false

  /**
   * D.9a.3 — when true, the listener pauses the player at the
   * `MEDIA_ITEM_TRANSITION_REASON_REPEAT` boundary instead of letting
   * the loop continue. Updated from the settings Flow by
   * [setPauseOnRepeat] so we don't pull a Context-bound dependency into
   * this class.
   */
  @Volatile
  private var pauseOnRepeat: Boolean = false

  fun setPauseOnRepeat(enabled: Boolean) {
    pauseOnRepeat = enabled
  }

  /**
   * D.9b.1 / D.9b.2 — current ReplayGain strategy + pre-amp dB,
   * pushed in by the activity's settings observer. Volatile so the
   * Player.Listener can read the latest values without a lock.
   */
  @Volatile
  private var replayGainStrategy: ReplayGainStrategy = ReplayGainStrategy.Off
  @Volatile
  private var replayGainPreampDb: Float = 0f

  /**
   * Library handle used to look up album-level gain + album track
   * count when we recompute the gain on a track transition. Set once
   * by the activity (no DI framework). When null, the controller
   * falls back to track gain from the [MediaItem] metadata.
   */
  @Volatile
  private var library: LibraryRepository? = null

  fun setLibrary(repo: LibraryRepository) {
    library = repo
  }

  /**
   * Update the current ReplayGain settings and re-apply the resulting
   * volume to the active player. Called from the activity's settings
   * observer.
   */
  fun setReplayGain(strategy: ReplayGainStrategy, preampDb: Float) {
    replayGainStrategy = strategy
    replayGainPreampDb = preampDb
    scope.launch { applyReplayGainNow() }
  }

  /**
   * Recompute the current track's gain and write it to
   * [Player.setVolume]. Looks up the active track + album from the
   * library cache; falls back gracefully when the cache is empty
   * (e.g. tests).
   */
  private suspend fun applyReplayGainNow() {
    val ctl = controller ?: return
    val item = ctl.currentMediaItem ?: run {
      ctl.volume = 1f
      return
    }
    val trackId = item.mediaId.toLongOrNull()
    val track = trackId?.let { library?.trackById(it) }
    val trackGainDb = track?.replayGainTrackDb
    val (albumGainDb, _) = if (track != null) {
      library?.albumReplayGain(track.album, track.albumArtist ?: track.artist) ?: (null to null)
    } else {
      null to null
    }
    val coverage = if (track == null) 0f else computeQueueAlbumCoverage(ctl, track)
    val gainDb = computeGain(replayGainStrategy, trackGainDb, albumGainDb, coverage)
    val total = gainDb + replayGainPreampDb
    val volume = linearGainFromDb(total)
    Log.i(
      "tonearm-rg",
      "applyReplayGain strategy=$replayGainStrategy preamp=$replayGainPreampDb " +
        "trackDb=$trackGainDb albumDb=$albumGainDb coverage=$coverage " +
        "totalDb=$total volume=$volume",
    )
    // Player.setVolume runs on the Main looper; we're already there
    // when invoked from the listener.
    ctl.volume = volume
  }

  /**
   * Estimate how much of the playing track's album is queued.
   * Smart-mode trips into album behaviour at >= 75% (see
   * `SMART_THRESHOLD`). We compare the count of queued items that
   * share the playing track's `(album, albumArtist|artist)` key
   * against the library's full count of tracks for that album.
   */
  private suspend fun computeQueueAlbumCoverage(
    ctl: MediaController,
    playing: Track,
  ): Float {
    val albumName = playing.album ?: return 0f
    val albumArtistKey = playing.albumArtist ?: playing.artist
    var queuedFromAlbum = 0
    val lib = library
    val n = ctl.mediaItemCount
    for (i in 0 until n) {
      val mi = ctl.getMediaItemAt(i)
      val mid = mi.mediaId.toLongOrNull() ?: continue
      // Cheap path: the MediaMetadata carries albumTitle / albumArtist
      // for items already in the queue, so we don't have to round-trip
      // every queue item through the DB.
      val md = mi.mediaMetadata
      val miAlbum = md.albumTitle?.toString()
      val miAlbumArtist = md.albumArtist?.toString() ?: md.artist?.toString()
      if (miAlbum == albumName && miAlbumArtist == albumArtistKey) queuedFromAlbum++
      else if (miAlbum == null && lib != null) {
        // Fall back to a DB lookup when the queue item came from a
        // surface that didn't tag the metadata.
        val t = lib.trackById(mid) ?: continue
        if (t.album == albumName && (t.albumArtist ?: t.artist) == albumArtistKey) queuedFromAlbum++
      }
    }
    val totalForAlbum = lib?.trackCountForAlbum(albumName, albumArtistKey) ?: queuedFromAlbum
    return if (totalForAlbum <= 0) 0f else queuedFromAlbum.toFloat() / totalForAlbum.toFloat()
  }

  private val listener = object : Player.Listener {
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      // D.9a.3: when the loop boundary on REPEAT_MODE_ONE fires, Media3
      // emits `MEDIA_ITEM_TRANSITION_REASON_REPEAT`. If the user has
      // pause-on-repeat enabled, snap back to position 0 and pause; the
      // user can resume with the play button. We deliberately seek to 0
      // first to avoid a one-frame artifact where the new track starts
      // at the previous position.
      if (shouldPauseOnRepeatBoundary(reason, pauseOnRepeat)) {
        controller?.let {
          it.seekTo(0L)
          it.playWhenReady = false
        }
      }
      // D.9b.1: re-apply ReplayGain whenever the playing item changes.
      scope.launch { applyReplayGainNow() }
      pushState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      pushState()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      pushState()
    }

    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int,
    ) {
      pushState()
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
      pushState()
    }
  }

  /** Connect to the running [PlaybackService]. Idempotent. */
  suspend fun connect() = withContext(Dispatchers.Main) {
    if (controller != null) return@withContext
    val c = PlaybackController.connect(applicationContext).await()
    controller = c
    c.addListener(listener)
    // Apply the persisted ReplayGain settings to the freshly connected
    // controller before any track plays.
    scope.launch { applyReplayGainNow() }
    pushState()
    if (!positionTickerStarted) {
      positionTickerStarted = true
      scope.launch {
        // 250 ms tick is plenty for a scrubber and avoids excess work
        // when the screen is off (Media3 listeners drive the rest).
        while (true) {
          delay(POSITION_TICK_MS)
          val ctl = controller ?: continue
          if (ctl.isPlaying) pushState()
        }
      }
    }
  }

  /** Release the controller. Called from `DisposableEffect`'s onDispose. */
  fun release() {
    controller?.let {
      it.removeListener(listener)
      it.release()
    }
    controller = null
    _state.value = PlaybackUiState.Empty
  }

  fun shutdown() {
    release()
    scope.cancel()
  }

  // -- Commands --------------------------------------------------------------

  fun playTrack(track: Track) {
    val ctl = controller ?: return
    ctl.setMediaItem(track.toMediaItem())
    ctl.prepare()
    ctl.play()
    pushState()
  }

  /** Replace the queue with [tracks] and start at [index]. */
  fun playQueue(tracks: List<Track>, index: Int = 0) {
    val ctl = controller ?: return
    if (tracks.isEmpty()) return
    val items = tracks.map { it.toMediaItem() }
    ctl.setMediaItems(items, index, 0L)
    ctl.prepare()
    ctl.play()
    pushState()
  }

  fun togglePlayPause() {
    val ctl = controller ?: return
    if (ctl.isPlaying) ctl.pause() else ctl.play()
    pushState()
  }

  fun seekToNext() {
    controller?.seekToNextMediaItem()
    pushState()
  }

  fun seekToPrevious() {
    controller?.seekToPreviousMediaItem()
    pushState()
  }

  fun seekTo(positionMs: Long) {
    controller?.seekTo(positionMs)
    pushState()
  }

  fun seekBackward() {
    val ctl = controller ?: return
    ctl.seekTo((ctl.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0))
    pushState()
  }

  fun seekForward() {
    val ctl = controller ?: return
    val target = ctl.currentPosition + SEEK_INCREMENT_MS
    val cap = ctl.duration.takeIf { it > 0 } ?: target
    ctl.seekTo(target.coerceAtMost(cap))
    pushState()
  }

  fun stop() {
    controller?.stop()
    controller?.clearMediaItems()
    pushState()
  }

  /**
   * D.15.6.1 — append a track to the end of the queue. If the queue is
   * empty, also `prepare()` and start playing so the user immediately
   * hears the result instead of staring at a silent player.
   */
  fun addToQueue(track: Track) {
    val ctl = controller ?: return
    val item = track.toMediaItem()
    if (ctl.mediaItemCount == 0) {
      ctl.setMediaItem(item)
      ctl.prepare()
      ctl.play()
    } else {
      ctl.addMediaItem(item)
    }
    pushState()
  }

  /** D.15.5 — jump to [index] in the current queue and play. */
  fun seekToQueueIndex(index: Int) {
    val ctl = controller ?: return
    if (index < 0 || index >= ctl.mediaItemCount) return
    ctl.seekTo(index, 0L)
    ctl.play()
    pushState()
  }

  /** D.15.5 — remove the queue entry at [index]. */
  fun removeQueueItem(index: Int) {
    val ctl = controller ?: return
    if (index < 0 || index >= ctl.mediaItemCount) return
    ctl.removeMediaItem(index)
    pushState()
  }

  /** D.15.5 — move queue item from [from] to [to]. */
  fun moveQueueItem(from: Int, to: Int) {
    val ctl = controller ?: return
    val n = ctl.mediaItemCount
    if (from < 0 || from >= n || to < 0 || to >= n || from == to) return
    ctl.moveMediaItem(from, to)
    pushState()
  }

  /**
   * D.9a.4 — build the queue for a tap on a track inside a flat library
   * list view (Songs tab, Genres detail, custom tabs).
   *
   *  - [PlayFromLibrary.AllSongs]: queue = the entire surrounding list,
   *    start at [tappedIndex];
   *  - [PlayFromLibrary.ItemOnly]: queue = just the tapped track;
   *  - [PlayFromLibrary.CurrentFilter]: queue = the surrounding list
   *    (which already represents the active filter / tab content),
   *    start at [tappedIndex].
   *
   * `AllSongs` and `CurrentFilter` are equivalent on the Songs tab
   * (where the surrounding list IS the entire library); they diverge
   * inside Genres detail, custom tabs, and other filtered surfaces. The
   * UI passes [allSongs] separately so this controller does not need a
   * library-repository handle.
   */
  fun playFromLibrary(
    surroundingList: List<Track>,
    tappedIndex: Int,
    strategy: PlayFromLibrary,
    allSongs: List<Track> = surroundingList,
  ) {
    val (queue, startIndex) = computePlayFromLibraryQueue(
      surroundingList = surroundingList,
      tappedIndex = tappedIndex,
      strategy = strategy,
      allSongs = allSongs,
    )
    if (queue.isEmpty()) return
    playQueue(queue, startIndex)
  }

  /**
   * D.9a.5 — build the queue for a tap on a track inside a detail
   * surface (album / artist / playlist).
   *
   *  - [PlayFromItemDetails.ShownItem]: queue = the detail view's track
   *    list, start at [tappedIndex] (default Auxio behaviour);
   *  - [PlayFromItemDetails.Album]: queue = all tracks on the same
   *    album as the tapped track;
   *  - [PlayFromItemDetails.Artist]: queue = all tracks credited to
   *    the same artist as the tapped track.
   *
   * The Album / Artist branches scope the queue to the relevant subset
   * of the surrounding list. If the surrounding list happens to already
   * be a single album / single artist (the common case from inside
   * AlbumDetailScreen / ArtistDetailScreen), the result is identical to
   * `ShownItem`. The branch matters for surfaces that mix tracks from
   * multiple albums / artists into one list (e.g. a playlist).
   */
  fun playFromDetail(
    surroundingList: List<Track>,
    tappedIndex: Int,
    strategy: PlayFromItemDetails,
  ) {
    val (queue, startIndex) = computePlayFromDetailQueue(
      surroundingList = surroundingList,
      tappedIndex = tappedIndex,
      strategy = strategy,
    )
    if (queue.isEmpty()) return
    playQueue(queue, startIndex)
  }

  /**
   * D.9a.1 — invoked from the mini-player play button's long-press
   * handler. Picker maps to a one-shot transport command.
   */
  fun performCustomBarAction(action: CustomBarAction) {
    val ctl = controller ?: return
    when (action) {
      CustomBarAction.SkipNext -> ctl.seekToNextMediaItem()
      CustomBarAction.ShuffleToggle -> ctl.shuffleModeEnabled = !ctl.shuffleModeEnabled
      CustomBarAction.RepeatToggle -> ctl.repeatMode = nextRepeatMode(ctl.repeatMode)
      CustomBarAction.None -> Unit
    }
    pushState()
  }

  /**
   * D.9a.2 — invoked from the notification's secondary action button
   * (forwarded by `PlaybackService`'s session callback) when the user
   * taps it. The toggle semantics are: Repeat advances OFF -> ALL ->
   * ONE -> OFF; Shuffle flips on / off; None is a no-op.
   */
  fun performCustomNotificationAction(action: com.eight87.tonearm.ui.settings.CustomNotificationAction) {
    val ctl = controller ?: return
    when (action) {
      com.eight87.tonearm.ui.settings.CustomNotificationAction.RepeatMode ->
        ctl.repeatMode = nextRepeatMode(ctl.repeatMode)
      com.eight87.tonearm.ui.settings.CustomNotificationAction.Shuffle ->
        ctl.shuffleModeEnabled = !ctl.shuffleModeEnabled
      com.eight87.tonearm.ui.settings.CustomNotificationAction.None -> Unit
    }
    pushState()
  }

  private fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
  }

  // -- Internals -------------------------------------------------------------

  private fun pushState() {
    val ctl = controller
    if (ctl == null || ctl.mediaItemCount == 0) {
      _state.value = PlaybackUiState.Empty
      _queue.value = QueueSnapshot.Empty
      return
    }
    val item = ctl.currentMediaItem
    val md = item?.mediaMetadata
    val mediaStoreAlbumId = (md?.extras?.getLong(EXTRA_MEDIA_STORE_ALBUM_ID, -1L))
      ?.takeIf { it >= 0 }
    _state.value = PlaybackUiState(
      hasMedia = true,
      title = md?.title?.toString().orEmpty(),
      artist = md?.artist?.toString().orEmpty(),
      album = md?.albumTitle?.toString().orEmpty(),
      mediaStoreAlbumId = mediaStoreAlbumId,
      isPlaying = ctl.isPlaying,
      positionMs = ctl.currentPosition.coerceAtLeast(0),
      durationMs = ctl.duration.takeIf { it > 0 } ?: 0,
      hasNext = ctl.hasNextMediaItem(),
      hasPrevious = ctl.hasPreviousMediaItem(),
    )
    val items = ArrayList<QueueItem>(ctl.mediaItemCount)
    for (i in 0 until ctl.mediaItemCount) {
      val mi = ctl.getMediaItemAt(i)
      val mmd = mi.mediaMetadata
      items += QueueItem(
        mediaId = mi.mediaId,
        title = mmd.title?.toString().orEmpty(),
        artist = mmd.artist?.toString().orEmpty(),
      )
    }
    _queue.value = QueueSnapshot(items = items, currentIndex = ctl.currentMediaItemIndex)
  }

  companion object {
    private const val SEEK_INCREMENT_MS = 10_000L
    private const val POSITION_TICK_MS = 250L

    /**
     * D.15.7 — extras key carrying the MediaStore album id through to
     * the [MediaItem.mediaMetadata]. The Now Playing screen reads this
     * to drive the same `CoverArt` composable the library tabs use,
     * giving real album art on tracks that have it instead of the
     * MusicNote placeholder.
     */
    const val EXTRA_MEDIA_STORE_ALBUM_ID = "tonearm.mediaStoreAlbumId"
  }
}

/** Snapshot of the parts of [MediaController] state the UI cares about. */
data class PlaybackUiState(
  val hasMedia: Boolean,
  val title: String,
  val artist: String,
  val album: String,
  /**
   * D.15.7 — MediaStore album id of the playing track, when the source
   * surface attached one. Used by `NowPlayingScreen` to drive `CoverArt`.
   */
  val mediaStoreAlbumId: Long? = null,
  val isPlaying: Boolean,
  val positionMs: Long,
  val durationMs: Long,
  val hasNext: Boolean,
  val hasPrevious: Boolean,
) {
  companion object {
    val Empty = PlaybackUiState(
      hasMedia = false,
      title = "",
      artist = "",
      album = "",
      mediaStoreAlbumId = null,
      isPlaying = false,
      positionMs = 0,
      durationMs = 0,
      hasNext = false,
      hasPrevious = false,
    )
  }
}

/**
 * D.15.5 — one queue entry, denormalized from a Media3 [MediaItem]
 * into the fields the queue sheet renders. [mediaId] is the same
 * `track.id.toString()` we feed into the controller, so callers can
 * round-trip back to the Track domain object via the library cache
 * if they need to.
 */
data class QueueItem(
  val mediaId: String,
  val title: String,
  val artist: String,
)

data class QueueSnapshot(
  val items: List<QueueItem>,
  val currentIndex: Int,
) {
  companion object {
    val Empty = QueueSnapshot(emptyList(), -1)
  }
}

/**
 * Pure decision helper for D.9a.3 — split out so the matrix is unit
 * testable without spinning a full ExoPlayer + MediaController.
 */
internal fun shouldPauseOnRepeatBoundary(reason: Int, pauseOnRepeat: Boolean): Boolean =
  pauseOnRepeat && reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT

/**
 * Pure helper exposing the queue-building logic for D.9a.4. Splitting
 * it out keeps the strategy testable without spinning a real
 * `MediaController` (which can only run inside the Robolectric / device
 * activity).
 */
internal fun computePlayFromLibraryQueue(
  surroundingList: List<Track>,
  tappedIndex: Int,
  strategy: PlayFromLibrary,
  allSongs: List<Track> = surroundingList,
): Pair<List<Track>, Int> {
  if (tappedIndex !in surroundingList.indices) return emptyList<Track>() to 0
  val tapped = surroundingList[tappedIndex]
  return when (strategy) {
    PlayFromLibrary.AllSongs -> {
      val idx = allSongs.indexOfFirst { it.id == tapped.id }.let { if (it < 0) 0 else it }
      allSongs to idx
    }
    PlayFromLibrary.ItemOnly -> listOf(tapped) to 0
    PlayFromLibrary.CurrentFilter -> surroundingList to tappedIndex
  }
}

/**
 * Pure helper exposing the queue-building logic for D.9a.5.
 */
internal fun computePlayFromDetailQueue(
  surroundingList: List<Track>,
  tappedIndex: Int,
  strategy: PlayFromItemDetails,
): Pair<List<Track>, Int> {
  if (tappedIndex !in surroundingList.indices) return emptyList<Track>() to 0
  val tapped = surroundingList[tappedIndex]
  return when (strategy) {
    PlayFromItemDetails.ShownItem -> surroundingList to tappedIndex
    PlayFromItemDetails.Album -> {
      val albumKey = tapped.album
      val filtered = if (albumKey.isNullOrBlank()) listOf(tapped)
      else surroundingList.filter { it.album == albumKey }
      val idx = filtered.indexOfFirst { it.id == tapped.id }.let { if (it < 0) 0 else it }
      filtered to idx
    }
    PlayFromItemDetails.Artist -> {
      val artistKey = tapped.albumArtist?.takeIf { it.isNotBlank() } ?: tapped.artist
      val filtered = if (artistKey.isNullOrBlank()) listOf(tapped)
      else surroundingList.filter { (it.albumArtist ?: it.artist) == artistKey }
      val idx = filtered.indexOfFirst { it.id == tapped.id }.let { if (it < 0) 0 else it }
      filtered to idx
    }
  }
}

private fun Track.toMediaItem(): MediaItem {
  // Phase E.1 / E.2: feed the MediaSession enough metadata for the
  // System UI notification + lock-screen surface to render properly.
  // We point artworkUri at the file URI of the audio file itself —
  // Media3's `DataSourceBitmapLoader` will fall back to extracting the
  // embedded ID3v2 / FLAC picture frame when the URI resolves to an
  // audio file. Tracks without embedded art simply render no large
  // icon, which is the same fallback the platform notification uses.
  val fileUri = Uri.parse("file://${data}")
  // D.15.7 — also stash the MediaStore album id so the in-app
  // NowPlaying surface can drive the same legacy-albumart Coil
  // request the library tabs do (cheaper + already cached).
  val extras = mediaStoreAlbumId?.let { id ->
    android.os.Bundle().apply {
      putLong(PlaybackUiController.EXTRA_MEDIA_STORE_ALBUM_ID, id)
    }
  }
  val metadata = MediaMetadata.Builder()
    .setTitle(title)
    .setArtist(artist)
    .setAlbumTitle(album)
    .setAlbumArtist(albumArtist)
    .setArtworkUri(fileUri)
    .also { if (extras != null) it.setExtras(extras) }
    .build()
  return MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(fileUri)
    .setMediaMetadata(metadata)
    .build()
}
