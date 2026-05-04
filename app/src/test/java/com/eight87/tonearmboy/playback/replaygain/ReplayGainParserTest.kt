package com.eight87.tonearmboy.playback.replaygain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainParserTest {

  @Test
  fun parses_negative_db_with_dB_suffix() {
    assertEquals(-6.34f, ReplayGainParser.parseGainDb("-6.34 dB")!!, 1e-3f)
  }

  @Test
  fun parses_explicit_positive_sign() {
    assertEquals(1.2f, ReplayGainParser.parseGainDb("+1.2 dB")!!, 1e-3f)
    assertEquals(1.2f, ReplayGainParser.parseGainDb("+1.2")!!, 1e-3f)
  }

  @Test
  fun parses_zero_with_no_decimal() {
    assertEquals(0f, ReplayGainParser.parseGainDb("0")!!, 1e-3f)
    assertEquals(0f, ReplayGainParser.parseGainDb("0 dB")!!, 1e-3f)
  }

  @Test
  fun strips_leading_and_trailing_whitespace() {
    assertEquals(-6.34f, ReplayGainParser.parseGainDb("  -6.34 dB  ")!!, 1e-3f)
  }

  @Test
  fun handles_comma_decimal_separator() {
    assertEquals(-6.34f, ReplayGainParser.parseGainDb("-6,34 dB")!!, 1e-3f)
  }

  @Test
  fun returns_null_for_blank_or_null() {
    assertNull(ReplayGainParser.parseGainDb(null))
    assertNull(ReplayGainParser.parseGainDb(""))
    assertNull(ReplayGainParser.parseGainDb("   "))
  }

  @Test
  fun returns_null_for_garbage() {
    assertNull(ReplayGainParser.parseGainDb("hello dB"))
    assertNull(ReplayGainParser.parseGainDb("--1.2"))
  }

  @Test
  fun parses_peaks_without_db_suffix() {
    assertEquals(0.987654f, ReplayGainParser.parsePeak("0.987654")!!, 1e-5f)
    assertEquals(1.012345f, ReplayGainParser.parsePeak("1.012345")!!, 1e-5f)
  }

  @Test
  fun peak_handles_comma_decimal() {
    assertEquals(0.5f, ReplayGainParser.parsePeak("0,5")!!, 1e-5f)
  }
}
