package com.eight87.tonearm.playback.replaygain

import org.junit.Assert.assertEquals
import org.junit.Test

class LinearGainTest {

  @Test
  fun zero_db_is_unity_gain() {
    assertEquals(1f, linearGainFromDb(0f), 1e-4f)
  }

  @Test
  fun minus_six_db_is_half_amplitude_approximately() {
    // -6 dB ≈ 0.5012; the standard "half amplitude" conversion.
    assertEquals(0.5012f, linearGainFromDb(-6f), 1e-3f)
  }

  @Test
  fun minus_twenty_db_is_one_tenth() {
    assertEquals(0.1f, linearGainFromDb(-20f), 1e-3f)
  }

  @Test
  fun positive_db_clamps_to_unity() {
    // Player.volume cannot exceed 1.0; we clamp at the upper end.
    assertEquals(1f, linearGainFromDb(6f), 1e-4f)
    assertEquals(1f, linearGainFromDb(15f), 1e-4f)
  }

  @Test
  fun very_negative_db_is_floored_at_zero() {
    // -INFINITY -> 0; -120 dB is effectively silent.
    assertEquals(1e-6f, linearGainFromDb(-120f), 1e-5f)
  }
}
