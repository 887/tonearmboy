package com.eight87.tonearmboy.playback.replaygain

import com.eight87.tonearmboy.ui.settings.ReplayGainStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGainStrategyTest {

  @Test
  fun off_returns_zero_regardless_of_input() {
    assertEquals(0f, computeGain(ReplayGainStrategy.Off, -6f, -3f, 1f), 1e-3f)
    assertEquals(0f, computeGain(ReplayGainStrategy.Off, null, null, 0f), 1e-3f)
  }

  @Test
  fun track_uses_track_gain_when_present() {
    assertEquals(-6f, computeGain(ReplayGainStrategy.Track, -6f, -3f, 1f), 1e-3f)
  }

  @Test
  fun track_falls_back_to_zero_when_track_gain_missing() {
    // Per the design comment in computeGain: the strategy is opt-in
    // normalization, missing data must never amplify or change volume.
    assertEquals(0f, computeGain(ReplayGainStrategy.Track, null, -3f, 0f), 1e-3f)
  }

  @Test
  fun album_uses_album_gain_when_present() {
    assertEquals(-3f, computeGain(ReplayGainStrategy.Album, -6f, -3f, 1f), 1e-3f)
  }

  @Test
  fun smart_picks_album_when_full_album_queued() {
    // 75% threshold (SMART_THRESHOLD): exactly at threshold, picks album.
    assertEquals(-3f, computeGain(ReplayGainStrategy.Smart, -6f, -3f, 0.75f), 1e-3f)
    assertEquals(-3f, computeGain(ReplayGainStrategy.Smart, -6f, -3f, 1f), 1e-3f)
  }

  @Test
  fun smart_picks_track_when_below_threshold() {
    assertEquals(-6f, computeGain(ReplayGainStrategy.Smart, -6f, -3f, 0.5f), 1e-3f)
    assertEquals(-6f, computeGain(ReplayGainStrategy.Smart, -6f, -3f, 0f), 1e-3f)
  }

  @Test
  fun smart_falls_back_to_track_when_album_missing_and_full_queue() {
    assertEquals(-6f, computeGain(ReplayGainStrategy.Smart, -6f, null, 1f), 1e-3f)
  }

  @Test
  fun smart_falls_back_to_album_when_track_missing_and_partial_queue() {
    assertEquals(-3f, computeGain(ReplayGainStrategy.Smart, null, -3f, 0.1f), 1e-3f)
  }
}
