package com.eight87.tonearmboy.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phase H.3 — Sleep timer.
 *
 * Schedules a deadline that pauses playback when it elapses. State is
 * exposed as a [StateFlow] so the settings dialog can render the live
 * remaining-time countdown without polling.
 *
 * Two modes:
 *  - **Immediate** (default): when the deadline elapses, pause the
 *    [PlaybackUiController] right away.
 *  - **Wait for end of song** (`waitForEndOfTrack = true`): when the
 *    deadline elapses, arm a `Player.Listener` that pauses on the
 *    next `MEDIA_ITEM_TRANSITION_REASON_AUTO` boundary so the
 *    currently playing track finishes first.
 *
 * Cancel is idempotent — calling [cancel] from any state returns to
 * [SleepTimerState.Idle]. Starting a new timer cancels any in-flight one.
 *
 * The timer runs on [scope] with [timeSource] as the clock; tests inject
 * a virtual-time scheduler scope and a controllable time source.
 */
@UnstableApi
class SleepTimer(
  private val scope: CoroutineScope,
  private val pauseAction: () -> Unit,
  private val addPlayerListener: ((Player.Listener) -> Unit)? = null,
  private val removePlayerListener: ((Player.Listener) -> Unit)? = null,
  private val timeSource: () -> Long = { System.currentTimeMillis() },
  private val tickIntervalMs: Long = TICK_INTERVAL_MS,
  /**
   * Volume getter/setter for the fade-out ramp before the timer fires.
   * Both default to no-ops so the existing test suite (which doesn't
   * exercise fade-out) still constructs the timer cleanly.
   */
  private val getVolume: () -> Float = { 1f },
  private val setVolume: (Float) -> Unit = {},
  /** Callback fired once the timer has paused playback — used by the
   *  app to arm shake-to-resume detection. */
  private val onFired: () -> Unit = {},
) {
  private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Idle)
  val state: StateFlow<SleepTimerState> = _state.asStateFlow()

  private var job: Job? = null
  private var trackBoundaryListener: Player.Listener? = null

  /**
   * Start a timer for [durationMs]. Replaces any in-flight timer with
   * the new deadline; calling start while a timer is running effectively
   * resets it.
   */
  fun start(
    durationMs: Long,
    waitForEndOfTrack: Boolean = false,
    fadeOutMs: Long = 0L,
  ) {
    if (durationMs <= 0L) return
    cancel()
    val expiresAt = timeSource() + durationMs
    // Cap fade window at the timer duration so a 5s timer with 30s
    // fade doesn't try to ramp through negative remaining.
    val effectiveFadeMs = fadeOutMs.coerceIn(0L, durationMs)
    val volumeAtArm = if (effectiveFadeMs > 0L) getVolume().coerceIn(0f, 1f) else 1f
    _state.value = SleepTimerState.Running(
      remainingMs = durationMs,
      expiresAt = expiresAt,
      waitForEndOfTrack = waitForEndOfTrack,
    )
    job = scope.launch {
      var fadeStarted = false
      try {
        while (isActive) {
          val now = timeSource()
          val remaining = expiresAt - now
          if (remaining <= 0L) break
          // Volume ramp once we're inside the fade window. Linear ramp
          // from `volumeAtArm` → 0 across `effectiveFadeMs`. The ramp
          // runs on the same tick cadence as the countdown — coarser
          // than a Player.volume Animator but it tracks the same clock
          // the UI is reading so the user-visible "remaining" stays in
          // step with the audio attenuation they hear.
          if (effectiveFadeMs > 0L && remaining <= effectiveFadeMs) {
            fadeStarted = true
            val ratio = (remaining.toFloat() / effectiveFadeMs.toFloat()).coerceIn(0f, 1f)
            setVolume(volumeAtArm * ratio)
          }
          _state.value = SleepTimerState.Running(
            remainingMs = remaining,
            expiresAt = expiresAt,
            waitForEndOfTrack = waitForEndOfTrack,
          )
          delay(minOf(tickIntervalMs, remaining))
        }
        if (!isActive) return@launch
        onDeadlineElapsed(waitForEndOfTrack, fadeStarted, volumeAtArm)
      } finally {
        // If the timer is cancelled mid-fade, restore the original
        // volume so the user doesn't end up with a muted player.
        if (fadeStarted && _state.value !is SleepTimerState.Running) {
          setVolume(volumeAtArm)
        }
      }
    }
  }

  /**
   * Cancel an in-flight timer. No-op when [SleepTimerState.Idle]; safe
   * to call from any thread.
   */
  fun cancel() {
    job?.cancel()
    job = null
    detachBoundaryListener()
    _state.value = SleepTimerState.Idle
  }

  private fun onDeadlineElapsed(
    waitForEndOfTrack: Boolean,
    fadeStarted: Boolean,
    volumeAtArm: Float,
  ) {
    if (!waitForEndOfTrack || addPlayerListener == null) {
      pauseAction()
      // After the fade ramped volume to 0 + pause, restore the
      // pre-arm volume so the next play() starts at the expected
      // level rather than silent.
      if (fadeStarted) setVolume(volumeAtArm)
      _state.value = SleepTimerState.Idle
      onFired()
      return
    }
    // Defer the pause to the next track boundary. Surface the wait
    // state so the UI can show "waiting for end of song" if it wants.
    _state.value = SleepTimerState.WaitingForTrackEnd
    val listener = object : Player.Listener {
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Only fire on a natural advance — explicit user seeks /
        // playlist swaps are not "the song finished".
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
          pauseAction()
          if (fadeStarted) setVolume(volumeAtArm)
          detachBoundaryListener()
          _state.value = SleepTimerState.Idle
          onFired()
        }
      }
    }
    trackBoundaryListener = listener
    addPlayerListener.invoke(listener)
  }

  private fun detachBoundaryListener() {
    val listener = trackBoundaryListener ?: return
    removePlayerListener?.invoke(listener)
    trackBoundaryListener = null
  }

  companion object {
    /** Default time between countdown emissions while running. */
    internal const val TICK_INTERVAL_MS: Long = 1_000L

    /** Preset durations exposed by the settings dialog (minutes). */
    val PRESET_MINUTES: List<Int> = listOf(15, 30, 45, 60, 90)
  }
}

/**
 * Sealed states of the sleep timer.
 */
sealed class SleepTimerState {
  /** No timer scheduled. */
  data object Idle : SleepTimerState()

  /**
   * Countdown active. [remainingMs] is the live remaining duration;
   * [expiresAt] is the absolute time-source value at which the timer
   * fires (kept around so the UI can render an absolute "ends at" time
   * in addition to the running countdown).
   */
  data class Running(
    val remainingMs: Long,
    val expiresAt: Long,
    val waitForEndOfTrack: Boolean,
  ) : SleepTimerState()

  /**
   * Deadline elapsed but the user asked to wait until the current
   * track finishes; the listener is armed and we'll flip back to
   * [Idle] on the next `MEDIA_ITEM_TRANSITION_REASON_AUTO`.
   */
  data object WaitingForTrackEnd : SleepTimerState()
}
