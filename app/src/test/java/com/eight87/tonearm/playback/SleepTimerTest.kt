package com.eight87.tonearm.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase H.7.1 — `SleepTimer` lifecycle. Uses the kotlinx-coroutines-test
 * virtual-time scheduler so we don't sit on real wall-clock delays.
 */
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class SleepTimerTest {

  @Test
  fun pause_fires_after_duration_elapses() = runTest {
    var paused = 0
    val timer = SleepTimer(
      scope = this,
      pauseAction = { paused++ },
      timeSource = { testScheduler.currentTime },
      tickIntervalMs = 1_000L,
    )

    timer.start(durationMs = 60_000L)
    assertTrue(timer.state.value is SleepTimerState.Running)

    testScheduler.advanceTimeBy(59_999L)
    testScheduler.runCurrent()
    assertEquals(0, paused)

    testScheduler.advanceTimeBy(2L)
    testScheduler.runCurrent()
    testScheduler.advanceUntilIdle()

    assertEquals("pauseAction must fire exactly once", 1, paused)
    assertSame(SleepTimerState.Idle, timer.state.value)
  }

  @Test
  fun cancel_prevents_pause() = runTest {
    var paused = 0
    val timer = SleepTimer(
      scope = this,
      pauseAction = { paused++ },
      timeSource = { testScheduler.currentTime },
      tickIntervalMs = 1_000L,
    )

    timer.start(durationMs = 60_000L)
    testScheduler.advanceTimeBy(30_000L)
    testScheduler.runCurrent()
    timer.cancel()
    assertSame(SleepTimerState.Idle, timer.state.value)

    // Run past the original deadline.
    testScheduler.advanceTimeBy(60_000L)
    testScheduler.runCurrent()
    testScheduler.advanceUntilIdle()

    assertEquals("cancelled timer must never fire", 0, paused)
  }

  @Test
  fun start_replaces_existing_timer() = runTest {
    var paused = 0
    val timer = SleepTimer(
      scope = this,
      pauseAction = { paused++ },
      timeSource = { testScheduler.currentTime },
      tickIntervalMs = 1_000L,
    )

    timer.start(durationMs = 60_000L)
    testScheduler.advanceTimeBy(10_000L)
    testScheduler.runCurrent()
    // Reset to a fresh 30-second timer.
    timer.start(durationMs = 30_000L)
    testScheduler.advanceTimeBy(20_000L)
    testScheduler.runCurrent()
    assertEquals("first timer must be replaced", 0, paused)
    testScheduler.advanceTimeBy(11_000L)
    testScheduler.runCurrent()
    testScheduler.advanceUntilIdle()
    assertEquals(1, paused)
  }

  @Test
  fun wait_for_end_of_song_defers_pause_to_next_track_boundary() = runTest {
    var paused = 0
    val listeners = mutableListOf<Player.Listener>()
    val timer = SleepTimer(
      scope = this,
      pauseAction = { paused++ },
      addPlayerListener = { listeners += it },
      removePlayerListener = { listeners -= it },
      timeSource = { testScheduler.currentTime },
      tickIntervalMs = 1_000L,
    )

    timer.start(durationMs = 60_000L, waitForEndOfTrack = true)
    testScheduler.advanceTimeBy(61_000L)
    testScheduler.runCurrent()
    testScheduler.advanceUntilIdle()

    // Deadline elapsed; timer should be in the WaitingForTrackEnd state.
    assertEquals("must defer pause", 0, paused)
    assertSame(SleepTimerState.WaitingForTrackEnd, timer.state.value)
    assertEquals("listener must be armed", 1, listeners.size)

    // Simulate the player advancing to the next item naturally.
    val captured = listeners.first()
    captured.onMediaItemTransition(
      /* mediaItem = */ null,
      Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
    )
    testScheduler.runCurrent()

    assertEquals("pause must fire on AUTO transition", 1, paused)
    assertSame(SleepTimerState.Idle, timer.state.value)
    assertTrue("listener must be removed", listeners.isEmpty())
  }

  @Test
  fun wait_for_end_of_song_ignores_user_seek_transitions() = runTest {
    var paused = 0
    val listeners = mutableListOf<Player.Listener>()
    val timer = SleepTimer(
      scope = this,
      pauseAction = { paused++ },
      addPlayerListener = { listeners += it },
      removePlayerListener = { listeners -= it },
      timeSource = { testScheduler.currentTime },
      tickIntervalMs = 1_000L,
    )

    timer.start(durationMs = 5_000L, waitForEndOfTrack = true)
    testScheduler.advanceTimeBy(6_000L)
    testScheduler.runCurrent()
    testScheduler.advanceUntilIdle()

    val captured = listeners.first()
    captured.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
    testScheduler.runCurrent()

    assertEquals("user-driven SEEK must not fire pause", 0, paused)
    assertSame(SleepTimerState.WaitingForTrackEnd, timer.state.value)
  }

  @Test
  fun zero_or_negative_duration_is_a_noop() = runTest {
    var paused = 0
    val timer = SleepTimer(
      scope = this,
      pauseAction = { paused++ },
      timeSource = { testScheduler.currentTime },
      tickIntervalMs = 1_000L,
    )
    timer.start(durationMs = 0L)
    timer.start(durationMs = -1_000L)
    testScheduler.advanceTimeBy(60_000L)
    testScheduler.runCurrent()
    testScheduler.advanceUntilIdle()
    assertEquals(0, paused)
    assertSame(SleepTimerState.Idle, timer.state.value)
  }

  @Test
  fun cancel_idle_is_idempotent() = runTest {
    val timer = SleepTimer(
      scope = this,
      pauseAction = { },
      timeSource = { testScheduler.currentTime },
      tickIntervalMs = 1_000L,
    )
    timer.cancel()
    timer.cancel()
    assertSame(SleepTimerState.Idle, timer.state.value)
  }
}
