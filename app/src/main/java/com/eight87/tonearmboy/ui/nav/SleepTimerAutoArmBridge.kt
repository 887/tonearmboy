package com.eight87.tonearmboy.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.playback.NowPlayingState
import com.eight87.tonearmboy.playback.SleepTimer
import com.eight87.tonearmboy.playback.SleepTimerState
import com.eight87.tonearmboy.ui.settings.PlaybackSettings
import java.util.Calendar

/**
 * Listens to playback state + sleep-timer auto-arm window settings.
 * When playback transitions to "playing" AND no timer is currently
 * running AND the current wall-clock time falls inside the
 * user-configured window, arms the sleep timer with the user's
 * default duration. Mirrors whisperboy's G.6 auto-arm shape.
 *
 * The window crosses midnight when `start > end` (e.g. 22:00–02:00),
 * mirroring the typical "fall asleep at night" use case.
 */
@OptIn(UnstableApi::class)
@Composable
fun SleepTimerAutoArmBridge(
  playback: NowPlayingState,
  sleepTimer: SleepTimer,
  settings: PlaybackSettings,
) {
  val playbackState by playback.state.collectAsStateWithLifecycle()
  val sleepState by sleepTimer.state.collectAsStateWithLifecycle()
  val startMinute by settings.sleepAutoArmStartMinute.flow
    .collectAsStateWithLifecycle(initialValue = -1)
  val endMinute by settings.sleepAutoArmEndMinute.flow
    .collectAsStateWithLifecycle(initialValue = -1)
  val defaultMinutes by settings.sleepDefaultMinutes.flow
    .collectAsStateWithLifecycle(initialValue = 30)

  LaunchedEffect(
    playbackState.isPlaying,
    sleepState is SleepTimerState.Idle,
    startMinute,
    endMinute,
  ) {
    if (!playbackState.isPlaying) return@LaunchedEffect
    if (sleepState !is SleepTimerState.Idle) return@LaunchedEffect
    if (startMinute !in 0..1439 || endMinute !in 0..1439) return@LaunchedEffect
    if (!isWithinWindow(nowMinuteOfDay(), startMinute, endMinute)) return@LaunchedEffect
    sleepTimer.start(defaultMinutes * 60_000L)
  }
}

internal fun nowMinuteOfDay(): Int {
  val c = Calendar.getInstance()
  return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

internal fun isWithinWindow(now: Int, start: Int, end: Int): Boolean =
  if (start <= end) now in start..end else now >= start || now <= end
