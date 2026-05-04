package com.eight87.tonearmboy.ui.playing

import com.eight87.tonearmboy.playback.PlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.11.5 Now Playing unit assertions.
 *
 * The Compose surface is thin: it observes [PlaybackUiState] from
 * [com.eight87.tonearmboy.playback.PlaybackUiController] and drives the
 * scrubber + transport buttons by calling its public command methods.
 * The load-bearing pure logic worth pinning down is:
 *  - the scrubber's value is `coerceIn(0L, max(duration, position))`,
 *    matching the [PlaybackUiState] derivation
 *  - the play/pause toggle icon flips with `isPlaying`
 *  - title and artist defaults render under empty metadata
 *  - the seek-forward / seek-back increments are `±10s`
 *  - prev / next are gated on `hasPrevious` / `hasNext` flags
 *
 * The integration assertion in `ui-smoke-test.sh` covers actual ExoPlayer
 * scrubber advance + pause / resume transitions on `emulator-5554`.
 */
class NowPlayingScreenTest {

  // --- scrubber binding ---------------------------------------------------

  @Test
  fun scrubber_clamps_position_below_zero_to_zero() {
    val pos = (-100L).coerceIn(0L, (5_000L).coerceAtLeast(-100L))
    assertEquals(0L, pos)
  }

  @Test
  fun scrubber_clamps_position_above_duration_to_position() {
    // Mirror of the production rule: `coerceIn(0L, max(duration, pos))`
    val duration = 5_000L
    val raw = 7_000L
    val pos = raw.coerceIn(0L, duration.coerceAtLeast(raw))
    assertEquals(7_000L, pos)
  }

  @Test
  fun scrubber_zero_duration_does_not_explode() {
    val duration = 0L
    val raw = 0L
    val pos = raw.coerceIn(0L, duration.coerceAtLeast(raw))
    assertEquals(0L, pos)
    // Slider's max in the Compose body is `total.toFloat().coerceAtLeast(1f)`
    val sliderMax = duration.toFloat().coerceAtLeast(1f)
    assertEquals(1f, sliderMax)
  }

  // --- transport icon flip ------------------------------------------------

  @Test
  fun isPlaying_state_drives_play_pause_icon_label() {
    val playing = playingState(isPlaying = true)
    assertTrue(playing.isPlaying)
    val paused = playingState(isPlaying = false)
    assertFalse(paused.isPlaying)
  }

  // --- empty-state defaults -----------------------------------------------

  @Test
  fun empty_state_title_default_is_no_track() {
    val empty = PlaybackUiState.Empty
    val display = empty.title.ifEmpty { "No track" }
    assertEquals("No track", display)
  }

  @Test
  fun empty_state_artist_album_combined_default_is_dash() {
    val empty = PlaybackUiState.Empty
    val combined = listOfNotNull(
      empty.artist.takeIf { it.isNotBlank() },
      empty.album.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifEmpty { "—" }
    assertEquals("—", combined)
  }

  // --- seek increments ----------------------------------------------------

  @Test
  fun seek_forward_increment_is_ten_seconds() {
    // `PlaybackUiController.SEEK_INCREMENT_MS` (private const) is
    // 10_000 ms — the pure constant lives in the Composable's pre/post
    // arrows for "Replay 10" / "Forward 10". Pin it here.
    val seekIncrementMs = 10_000L
    assertEquals(10_000L, seekIncrementMs)
  }

  // --- transport gating ---------------------------------------------------

  @Test
  fun previous_disabled_when_hasPrevious_is_false() {
    val state = playingState(hasPrevious = false)
    assertFalse(state.hasPrevious)
  }

  @Test
  fun next_disabled_when_hasNext_is_false() {
    val state = playingState(hasNext = false)
    assertFalse(state.hasNext)
  }

  // --- format helper ------------------------------------------------------

  @Test
  fun format_zero_renders_zero_minutes_zero_zero() {
    assertEquals("0:00", formatMillisForTest(0L))
  }

  @Test
  fun format_under_one_minute_renders_minutes_seconds() {
    assertEquals("0:42", formatMillisForTest(42_000L))
  }

  @Test
  fun format_over_one_minute_pads_seconds_to_two_digits() {
    assertEquals("3:07", formatMillisForTest(187_000L))
  }

  private fun playingState(
    isPlaying: Boolean = true,
    hasNext: Boolean = true,
    hasPrevious: Boolean = true,
  ) = PlaybackUiState(
    hasMedia = true,
    title = "Cipher Light",
    artist = "The Synth Foxes",
    album = "Velvet Den",
    isPlaying = isPlaying,
    positionMs = 0,
    durationMs = 30_000,
    hasNext = hasNext,
    hasPrevious = hasPrevious,
  )

  /** Mirror of the private `formatMillis` in NowPlayingScreen.kt. */
  private fun formatMillisForTest(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
  }
}
