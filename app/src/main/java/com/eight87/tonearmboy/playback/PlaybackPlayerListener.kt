package com.eight87.tonearmboy.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * R.C.6 — `Player.Listener` extracted from `PlaybackUiController`.
 *
 * Bridges Media3 callbacks → projector pushes + ReplayGain re-apply
 * + the D.9a.3 pause-on-repeat boundary handler. Built in the
 * controller's primary constructor so it can close over the live
 * `controller` (for the seek-to-0 + playWhenReady tweak the
 * REPEAT_ONE branch needs) and dispatch on the same scope.
 *
 * Push routing per callback:
 *  - `onMediaItemTransition` / `onTimelineChanged` →
 *    [PlaybackStateProjector.pushAll] (queue snapshot must follow)
 *  - everything else → [PlaybackStateProjector.pushPlaybackState]
 *    (state-only — the cheap path)
 */
@UnstableApi
internal class PlaybackPlayerListener(
  private val scope: CoroutineScope,
  private val projector: PlaybackStateProjector,
  private val replayGainController: ReplayGainController,
  private val pauseOnRepeatHolder: PauseOnRepeatHolder,
  private val controllerProvider: () -> MediaController?,
) : Player.Listener {

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    // D.9a.3: when the loop boundary on REPEAT_MODE_ONE fires, Media3
    // emits `MEDIA_ITEM_TRANSITION_REASON_REPEAT`. If the user has
    // pause-on-repeat enabled, snap back to position 0 and pause; the
    // user can resume with the play button. We deliberately seek to 0
    // first to avoid a one-frame artifact where the new track starts
    // at the previous position.
    if (shouldPauseOnRepeatBoundary(reason, pauseOnRepeatHolder.value)) {
      controllerProvider()?.let {
        it.seekTo(0L)
        it.playWhenReady = false
      }
    }
    // D.9b.1: re-apply ReplayGain whenever the playing item changes.
    scope.launch { replayGainController.applyReplayGainNow() }
    // R.C.2 — currentMediaItemIndex shifts on transition; queue
    // snapshot's currentIndex must follow.
    projector.pushAll()
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) = projector.pushPlaybackState()

  override fun onPlaybackStateChanged(playbackState: Int) = projector.pushPlaybackState()

  override fun onPositionDiscontinuity(
    oldPosition: Player.PositionInfo,
    newPosition: Player.PositionInfo,
    reason: Int,
  ) = projector.pushPlaybackState()

  override fun onTimelineChanged(timeline: Timeline, reason: Int) {
    // R.C.2 — timeline change == queue items added/removed/reordered.
    projector.pushAll()
  }

  // D.21.4: shuffle / repeat toggles in the queue header + NowPlaying
  // transport row read these straight off PlaybackUiState. Mirror the
  // controller's events into pushPlaybackState so the toggles light
  // up synchronously with system / Bluetooth-driven changes.
  override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
    projector.pushPlaybackState()

  override fun onRepeatModeChanged(repeatMode: Int) = projector.pushPlaybackState()
}
