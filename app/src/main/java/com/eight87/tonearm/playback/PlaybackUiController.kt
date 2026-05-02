package com.eight87.tonearm.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.ui.settings.CustomBarAction
import com.eight87.tonearm.ui.settings.PlayFromItemDetails
import com.eight87.tonearm.ui.settings.PlayFromLibrary
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
  }

  /** Connect to the running [PlaybackService]. Idempotent. */
  suspend fun connect() = withContext(Dispatchers.Main) {
    if (controller != null) return@withContext
    val c = PlaybackController.connect(applicationContext).await()
    controller = c
    c.addListener(listener)
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
      return
    }
    val item = ctl.currentMediaItem
    val md = item?.mediaMetadata
    _state.value = PlaybackUiState(
      hasMedia = true,
      title = md?.title?.toString().orEmpty(),
      artist = md?.artist?.toString().orEmpty(),
      album = md?.albumTitle?.toString().orEmpty(),
      isPlaying = ctl.isPlaying,
      positionMs = ctl.currentPosition.coerceAtLeast(0),
      durationMs = ctl.duration.takeIf { it > 0 } ?: 0,
      hasNext = ctl.hasNextMediaItem(),
      hasPrevious = ctl.hasPreviousMediaItem(),
    )
  }

  companion object {
    private const val SEEK_INCREMENT_MS = 10_000L
    private const val POSITION_TICK_MS = 250L
  }
}

/** Snapshot of the parts of [MediaController] state the UI cares about. */
data class PlaybackUiState(
  val hasMedia: Boolean,
  val title: String,
  val artist: String,
  val album: String,
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
      isPlaying = false,
      positionMs = 0,
      durationMs = 0,
      hasNext = false,
      hasPrevious = false,
    )
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
  val metadata = MediaMetadata.Builder()
    .setTitle(title)
    .setArtist(artist)
    .setAlbumTitle(album)
    .setAlbumArtist(albumArtist)
    .setArtworkUri(fileUri)
    .build()
  return MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(fileUri)
    .setMediaMetadata(metadata)
    .build()
}
