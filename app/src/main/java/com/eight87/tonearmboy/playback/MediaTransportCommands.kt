package com.eight87.tonearmboy.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.CustomBarAction
import com.eight87.tonearmboy.ui.settings.CustomNotificationAction
import com.eight87.tonearmboy.ui.settings.PlayFromItemDetails
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary

/**
 * R.C.3 — concrete [TransportCommands] wrapping a `MediaController`.
 *
 * Receives the live controller via [controllerProvider] (so it
 * follows the connection lifecycle without holding a stale handle),
 * and the [PlaybackStateProjector] to push state right after each
 * mutation so the UI reflects the change without waiting for the
 * Media3 listener round-trip.
 *
 * Extracted from `PlaybackUiController` so the god class shrinks
 * (R.C.6) and so transport behaviour is independently testable.
 */
@UnstableApi
internal class MediaTransportCommands(
  private val controllerProvider: () -> MediaController?,
  private val projector: PlaybackStateProjector,
  private val pauseOnRepeatHolder: PauseOnRepeatHolder,
) : TransportCommands {

  override fun togglePlayPause() {
    val ctl = controllerProvider() ?: return
    if (ctl.isPlaying) ctl.pause() else ctl.play()
    projector.pushPlaybackState()
  }

  override fun seekTo(positionMs: Long) {
    controllerProvider()?.seekTo(positionMs)
    projector.pushPlaybackState()
  }

  override fun seekBackward() {
    val ctl = controllerProvider() ?: return
    ctl.seekTo((ctl.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L))
    projector.pushPlaybackState()
  }

  override fun seekForward() {
    val ctl = controllerProvider() ?: return
    val target = ctl.currentPosition + SEEK_INCREMENT_MS
    val cap = ctl.duration.takeIf { it > 0 } ?: target
    ctl.seekTo(target.coerceAtMost(cap))
    projector.pushPlaybackState()
  }

  override fun seekToPrevious() {
    controllerProvider()?.seekToPreviousMediaItem()
    projector.pushPlaybackState()
  }

  override fun seekToNext() {
    controllerProvider()?.seekToNextMediaItem()
    projector.pushPlaybackState()
  }

  override fun stop() {
    val ctl = controllerProvider() ?: return
    ctl.stop()
    ctl.clearMediaItems()
    projector.pushPlaybackState()
  }

  override fun toggleShuffle() {
    val ctl = controllerProvider() ?: return
    ctl.shuffleModeEnabled = !ctl.shuffleModeEnabled
    projector.pushPlaybackState()
  }

  override fun cycleRepeatMode() {
    val ctl = controllerProvider() ?: return
    ctl.repeatMode = nextRepeatMode(ctl.repeatMode)
    projector.pushPlaybackState()
  }

  override fun setPauseOnRepeat(enabled: Boolean) {
    pauseOnRepeatHolder.value = enabled
  }

  override fun playTrack(track: Track) {
    val ctl = controllerProvider() ?: return
    ctl.setMediaItem(track.toMediaItem())
    ctl.prepare()
    ctl.play()
    projector.pushAll()
  }

  override fun playQueue(tracks: List<Track>, index: Int) {
    val ctl = controllerProvider() ?: return
    if (tracks.isEmpty()) return
    val items = tracks.map { it.toMediaItem() }
    ctl.setMediaItems(items, index, 0L)
    ctl.prepare()
    ctl.play()
    projector.pushAll()
  }

  override fun playFromLibrary(
    surroundingList: List<Track>,
    tappedIndex: Int,
    strategy: PlayFromLibrary,
    allSongs: List<Track>,
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

  override fun playFromDetail(
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

  override fun performCustomBarAction(action: CustomBarAction) {
    val ctl = controllerProvider() ?: return
    when (action) {
      CustomBarAction.SkipNext -> ctl.seekToNextMediaItem()
      CustomBarAction.ShuffleToggle -> ctl.shuffleModeEnabled = !ctl.shuffleModeEnabled
      CustomBarAction.RepeatToggle -> ctl.repeatMode = nextRepeatMode(ctl.repeatMode)
      CustomBarAction.None -> Unit
    }
    projector.pushPlaybackState()
  }

  override fun performCustomNotificationAction(action: CustomNotificationAction) {
    val ctl = controllerProvider() ?: return
    when (action) {
      CustomNotificationAction.RepeatMode -> ctl.repeatMode = nextRepeatMode(ctl.repeatMode)
      CustomNotificationAction.Shuffle -> ctl.shuffleModeEnabled = !ctl.shuffleModeEnabled
      CustomNotificationAction.None -> Unit
    }
    projector.pushPlaybackState()
  }

  companion object {
    internal const val SEEK_INCREMENT_MS = 10_000L
  }
}

/**
 * R.C.3 — small holder for the pause-on-repeat flag. The flag is
 * read by `PlaybackUiController`'s `Player.Listener` (not by the
 * transport impl), so we share it through this tiny mutable cell
 * instead of pushing a callback through the constructor.
 */
@UnstableApi
internal class PauseOnRepeatHolder {
  @Volatile
  var value: Boolean = false
}

/** Repeat-mode state machine. OFF → ALL → ONE → OFF. */
internal fun nextRepeatMode(current: Int): Int = when (current) {
  Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
  Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
  Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
  else -> Player.REPEAT_MODE_OFF
}
