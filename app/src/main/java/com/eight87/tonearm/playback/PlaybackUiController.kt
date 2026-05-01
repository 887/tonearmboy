package com.eight87.tonearm.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.eight87.tonearm.data.model.Track
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

  private val listener = object : Player.Listener {
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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

private fun Track.toMediaItem(): MediaItem {
  val metadata = MediaMetadata.Builder()
    .setTitle(title)
    .setArtist(artist)
    .setAlbumTitle(album)
    .setAlbumArtist(albumArtist)
    .build()
  return MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(Uri.parse("file://${data}"))
    .setMediaMetadata(metadata)
    .build()
}
