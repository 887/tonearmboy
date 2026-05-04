package com.eight87.tonearmboy.ui.playing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.26.2 — pin the visual-to-controller offset math now that the queue
 * renders the full timeline 1:1. Every visual position equals its
 * controller index. The active row stays in place and is highlighted
 * rather than skipped, so the old `+ currentIndex + 1` offset is gone.
 */
class QueueIndexTranslationTest {

  @Test fun visual_zero_with_active_at_two_maps_to_zero() {
    assertEquals(0, translateVisualToReal(currentIndex = 2, visual = 0))
  }

  @Test fun visual_two_with_active_at_two_maps_to_two() {
    assertEquals(2, translateVisualToReal(currentIndex = 2, visual = 2))
  }

  @Test fun translation_independent_of_currentIndex() {
    // For any (currentIndex, visual) the translation is the identity.
    val realIndices = (0..2).map { translateVisualToReal(currentIndex = 0, visual = it) }
    assertEquals(listOf(0, 1, 2), realIndices)
  }

  @Test fun current_index_zero_visual_zero_maps_to_zero() {
    assertEquals(0, translateVisualToReal(currentIndex = 0, visual = 0))
  }

  @Test fun visual_n_walks_in_lockstep_with_real_n() {
    val expected = listOf(0, 1, 2, 3, 4)
    val actual = (0..4).map { translateVisualToReal(currentIndex = 0, visual = it) }
    assertEquals(expected, actual)
  }
}
