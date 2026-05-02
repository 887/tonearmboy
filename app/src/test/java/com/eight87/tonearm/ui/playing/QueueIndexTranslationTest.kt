package com.eight87.tonearm.ui.playing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.24.5 / D.24.6 — pin the visual-to-controller offset math for the
 * inlined queue. The visual "up next" list starts at controller index
 * `currentIndex + 1`, so every visual position `v` maps to
 * `currentIndex + 1 + v`. Three scenarios:
 *
 *  - empty filter (the simple case)
 *  - non-empty filter (the math is identical — the filter is render-
 *    only, drag-reorder is disabled while it's active, and the X /
 *    tap operations carry their own `realIndex` from the visual
 *    entry rather than going through the position math)
 *  - currently-playing-is-first (currentIndex == 0)
 */
class QueueIndexTranslationTest {

  // -- empty filter ----------------------------------------------------

  @Test fun unfiltered_visual_zero_with_active_at_two_maps_to_three() {
    assertEquals(3, translateVisualToReal(currentIndex = 2, visual = 0))
  }

  @Test fun unfiltered_visual_two_with_active_at_two_maps_to_five() {
    assertEquals(5, translateVisualToReal(currentIndex = 2, visual = 2))
  }

  // -- non-empty filter ------------------------------------------------
  //
  // While the filter is non-empty, the queue section disables drag
  // handles and renders rows with their underlying `realIndex` baked
  // in. So the index translation isn't exercised — but if a filtered
  // row's X button or tap is invoked, the controller index used is
  // the entry's pre-computed `realIndex`, which is exactly what
  // `translateVisualToReal(currentIdx, visualOriginal)` produced
  // before the filter masked the row out. Pin that the underlying
  // translation is unchanged regardless of filter state.

  @Test fun filtered_view_does_not_change_underlying_index_translation() {
    // Imagine queue [A(active), B, C, D]. Up-next visual: [B, C, D] →
    // realIndex 1, 2, 3 by translateVisualToReal(0, v) for v in 0..2.
    val active = 0
    val realIndices = (0..2).map { translateVisualToReal(active, it) }
    assertEquals(listOf(1, 2, 3), realIndices)
  }

  // -- currently-playing-is-first --------------------------------------

  @Test fun current_index_zero_visual_zero_maps_to_one() {
    // The most common case: tap a track in the library, the controller
    // sets it as `currentIndex = 0`, the user opens NowPlaying, the
    // up-next list starts at controller index 1.
    assertEquals(1, translateVisualToReal(currentIndex = 0, visual = 0))
  }

  @Test fun current_index_zero_visual_n_walks_in_lockstep() {
    val expected = listOf(1, 2, 3, 4, 5)
    val actual = (0..4).map { translateVisualToReal(currentIndex = 0, visual = it) }
    assertEquals(expected, actual)
  }

  // -- remove / jump operations also use translateVisualToReal --------

  @Test fun remove_at_visual_zero_targets_real_currentIndex_plus_one() {
    // The X button on a queue row calls
    // `mediaController.removeMediaItem(visual + currentIdx + 1)`.
    val real = translateVisualToReal(currentIndex = 4, visual = 0)
    assertEquals(5, real)
  }

  @Test fun jump_to_visual_n_targets_real_currentIndex_plus_one_plus_n() {
    // The row tap calls `mediaController.seekToQueueIndex(visual + currentIdx + 1)`.
    val real = translateVisualToReal(currentIndex = 4, visual = 3)
    assertEquals(8, real)
  }

  // -- drag-reorder uses translateVisualToReal twice ------------------

  @Test fun drag_reorder_translates_both_endpoints() {
    // Drag visual-3 to visual-1 in a queue with active at index 4
    // → controller move(8, 6).
    val from = translateVisualToReal(currentIndex = 4, visual = 3)
    val to = translateVisualToReal(currentIndex = 4, visual = 1)
    assertEquals(8, from)
    assertEquals(6, to)
  }
}
