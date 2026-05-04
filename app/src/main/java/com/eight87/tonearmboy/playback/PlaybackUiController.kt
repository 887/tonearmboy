package com.eight87.tonearmboy.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.eight87.tonearmboy.data.TrackSource
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.CustomBarAction
import com.eight87.tonearmboy.ui.settings.PlayFromItemDetails
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary
import com.eight87.tonearmboy.ui.settings.ReplayGainStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
class PlaybackUiController(private val applicationContext: Context) :
  NowPlayingState,
  TransportCommands,
  QueueCommands,
  ReplayGainCommands {

  // R.C.2 — read-side projection lives in PlaybackStateProjector.
  // Controller delegates the two override flows; the projector
  // reads `controller` + `connectionPhase` lazily via providers.
  private val projector = PlaybackStateProjector(
    controllerProvider = { controller },
    connectionPhaseProvider = { connectionPhase },
  )
  override val state: StateFlow<PlaybackUiState> get() = projector.state

  /**
   * Phase H.4 — re-exposed audio session id from [PlayerHolder]. The
   * settings "System equalizer" row reads this before launching the
   * `ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` intent so the system EQ
   * applies its filter to *our* audio session.
   *
   * Defaults to [androidx.media3.common.C.AUDIO_SESSION_ID_UNSET] until
   * the player attaches an `AudioTrack`. The system EQ activity treats
   * UNSET as "global" / "output mix", which is fine but less precise
   * than addressing our actual session — the row only fires the intent
   * once we have a real id.
   */
  override val audioSessionId: StateFlow<Int> = PlayerHolder.audioSessionId

  // R.C.5 — SleepTimer construction moved to AppGraph; the timer is
  // now built independently and wired via the three small public
  // methods below ([pauseSilently], [addPlayerListener],
  // [removePlayerListener]). The controller is no longer the timer's
  // composition root.

  /**
   * R.C.5 — pause the active MediaController without touching state
   * tracking. Used by [SleepTimer] when its countdown elapses.
   * `withContext(Main)` so the call lands on the Media3 binder
   * thread regardless of which dispatcher the timer fires on.
   */
  fun pauseSilently() {
    scope.launch { controller?.pause() }
  }

  /**
   * R.C.5 — register an external [Player.Listener]. Used by
   * [SleepTimer]'s "wait for end of track" mode to detect
   * `MEDIA_ITEM_TRANSITION_REASON_AUTO`. No-op when the controller
   * isn't bound; the listener is held by the caller and re-registers
   * on next [connect].
   */
  fun addPlayerListener(l: Player.Listener) {
    controller?.addListener(l)
  }

  /** R.C.5 — symmetric removal for [addPlayerListener]. */
  fun removePlayerListener(l: Player.Listener) {
    controller?.removeListener(l)
  }

  /**
   * D.22.3 — coarse handshake state with the [PlaybackService].
   * `Connecting` until [connect] resolves; `Connected` once the
   * Media3 `MediaController.Builder.buildAsync` future delivers and
   * we've attached our listener. Read by `pushState` so every
   * emitted [PlaybackUiState] carries the current phase.
   *
   * The flag flips on `release` back to `Connecting` so a caller
   * that re-`connect`s (e.g. after a process restart) gets the same
   * "show the spinner until we know" semantics on round two.
   */
  @Volatile
  private var connectionPhase: ConnectionPhase = ConnectionPhase.Connecting

  /**
   * D.15.5 — snapshot of the current MediaController queue, recomputed
   * whenever items change or the playing index advances. The queue
   * sheet observes this Flow to render rows + the "now playing" marker.
   * Owned by [projector] (R.C.2).
   */
  override val queue: StateFlow<QueueSnapshot> get() = projector.queue

  private var controller: MediaController? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var positionTickerStarted = false

  /**
   * D.9a.3 — when true, the listener pauses the player at the
   * `MEDIA_ITEM_TRANSITION_REASON_REPEAT` boundary instead of letting
   * the loop continue. Owned by the holder so [MediaTransportCommands]
   * can write it (via [TransportCommands.setPauseOnRepeat]) and the
   * listener block below can read it.
   */
  private val pauseOnRepeatHolder = PauseOnRepeatHolder()

  // R.C.3 — TransportCommands + QueueCommands impls. Controller
  // forwards each facet method via single-line override below;
  // each impl reads the live `controller` via the same provider
  // the projector uses, and pushes through the same projector.
  private val transportImpl: MediaTransportCommands = MediaTransportCommands(
    controllerProvider = { controller },
    projector = projector,
    pauseOnRepeatHolder = pauseOnRepeatHolder,
  )
  private val queueImpl: MediaQueueCommands = MediaQueueCommands(
    controllerProvider = { controller },
    projector = projector,
  )

  // R.C.4 — ReplayGain owns its own settings + the volume math +
  // the queue-coverage scan + the library handle. Controller
  // forwards `setReplayGain` and asks for an immediate `applyNow`
  // from the listener / connect handshake; LibraryRepository is
  // no longer reachable from this class.
  private val replayGainController = ReplayGainController(
    scope = scope,
    controllerProvider = { controller },
  )

  /** Hand the ReplayGain controller a library handle so it can look up album-level gain. */
  fun setLibrary(repo: TrackSource) {
    replayGainController.setLibrary(repo)
  }

  override fun setReplayGain(strategy: ReplayGainStrategy, preampDb: Float) =
    replayGainController.setReplayGain(strategy, preampDb)

  // R.C.6 — Player.Listener extracted to PlaybackPlayerListener; the
  // controller just owns the instance + attaches/detaches it during
  // connect / release.
  private val listener: Player.Listener = PlaybackPlayerListener(
    scope = scope,
    projector = projector,
    replayGainController = replayGainController,
    pauseOnRepeatHolder = pauseOnRepeatHolder,
    controllerProvider = { controller },
  )

  /**
   * D.22.2 — suspend until the first state emission whose
   * `connectionPhase == Connected` lands. Used by `MainActivity` to
   * bound the splash-screen hold so a cold-start landing on Now
   * Playing doesn't flash a blank Compose frame between the splash
   * dismissing and the controller binding.
   *
   * Idempotent + safe to call before [connect]: it simply parks on
   * the StateFlow until any consumer (or [connect] itself) flips the
   * phase. Callers wrap this in `withTimeoutOrNull(...)` so a stuck
   * service binding doesn't pin the splash forever.
   */
  suspend fun awaitConnected() {
    if (state.value.connectionPhase == ConnectionPhase.Connected) return
    state.first { it.connectionPhase == ConnectionPhase.Connected }
  }

  /** Connect to the running [PlaybackService]. Idempotent. */
  suspend fun connect() = withContext(Dispatchers.Main) {
    if (controller != null) return@withContext
    val c = PlaybackController.connect(applicationContext).await()
    controller = c
    c.addListener(listener)
    // D.22.3: flip Connecting → Connected once Media3 has actually
    // bound. NowPlayingScreen reads this to choose between the
    // "Connecting…" spinner, the "Nothing playing" empty card, and the
    // full transport surface. Set BEFORE pushState so the same UI
    // frame that learns there's a controller also sees the phase flip.
    connectionPhase = ConnectionPhase.Connected
    // Apply the persisted ReplayGain settings to the freshly connected
    // controller before any track plays.
    scope.launch { replayGainController.applyReplayGainNow() }
    // R.C.2 — initial connect needs both state + queue rendered.
    projector.pushAll()
    if (!positionTickerStarted) {
      positionTickerStarted = true
      scope.launch {
        // 250 ms tick is plenty for a scrubber and avoids excess work
        // when the screen is off (Media3 listeners drive the rest).
        while (true) {
          delay(POSITION_TICK_MS)
          val ctl = controller ?: continue
          // R.C.2 — position-only push: the 250 ms ticker no longer
          // recomputes the queue snapshot; queue rebuild fires only
          // from listener events that actually change media items.
          if (ctl.isPlaying) projector.pushPlaybackState()
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
    connectionPhase = ConnectionPhase.Connecting
    projector.reset()
  }

  fun shutdown() {
    release()
    scope.cancel()
  }

  // -- Commands --------------------------------------------------------------

  // R.C.3 — TransportCommands + QueueCommands forward to the
  // extracted impls. Each method body is a single-line delegation;
  // the actual MediaController interaction lives in
  // MediaTransportCommands / MediaQueueCommands.

  override fun togglePlayPause() = transportImpl.togglePlayPause()
  override fun seekTo(positionMs: Long) = transportImpl.seekTo(positionMs)
  override fun seekBackward() = transportImpl.seekBackward()
  override fun seekForward() = transportImpl.seekForward()
  override fun seekToPrevious() = transportImpl.seekToPrevious()
  override fun seekToNext() = transportImpl.seekToNext()
  override fun stop() = transportImpl.stop()
  override fun toggleShuffle() = transportImpl.toggleShuffle()
  override fun cycleRepeatMode() = transportImpl.cycleRepeatMode()
  override fun setPauseOnRepeat(enabled: Boolean) = transportImpl.setPauseOnRepeat(enabled)
  override fun playTrack(track: Track) = transportImpl.playTrack(track)
  override fun playQueue(tracks: List<Track>, index: Int) =
    transportImpl.playQueue(tracks, index)
  override fun playFromLibrary(
    surroundingList: List<Track>,
    tappedIndex: Int,
    strategy: PlayFromLibrary,
    allSongs: List<Track>,
  ) = transportImpl.playFromLibrary(surroundingList, tappedIndex, strategy, allSongs)
  override fun playFromDetail(
    surroundingList: List<Track>,
    tappedIndex: Int,
    strategy: PlayFromItemDetails,
  ) = transportImpl.playFromDetail(surroundingList, tappedIndex, strategy)
  override fun performCustomBarAction(action: CustomBarAction) =
    transportImpl.performCustomBarAction(action)
  override fun performCustomNotificationAction(
    action: com.eight87.tonearmboy.ui.settings.CustomNotificationAction,
  ) = transportImpl.performCustomNotificationAction(action)

  override fun addToQueue(track: Track) = queueImpl.addToQueue(track)
  override fun seekToQueueIndex(index: Int) = queueImpl.seekToQueueIndex(index)
  override fun removeQueueItem(index: Int) = queueImpl.removeQueueItem(index)
  override fun removeQueueItemsByMediaIds(deletedMediaIds: Set<String>): Int =
    queueImpl.removeQueueItemsByMediaIds(deletedMediaIds)
  override fun moveQueueItem(from: Int, to: Int) = queueImpl.moveQueueItem(from, to)

  // -- Internals -------------------------------------------------------------

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
    const val EXTRA_MEDIA_STORE_ALBUM_ID = "tonearmboy.mediaStoreAlbumId"
  }
}

// R.C.6 — top-level helpers + data classes moved out:
//   - PlaybackUiState / ConnectionPhase / QueueItem / QueueSnapshot →
//     `PlaybackTypes.kt`
//   - shouldPauseOnRepeatBoundary / queueIndicesToRemove /
//     computePlayFromLibraryQueue / computePlayFromDetailQueue →
//     `PlaybackQueueHelpers.kt`
//   - `Track.toMediaItem()` → `MediaItemFactory.kt`
