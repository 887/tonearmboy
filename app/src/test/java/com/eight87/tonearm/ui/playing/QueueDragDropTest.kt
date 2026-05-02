package com.eight87.tonearm.ui.playing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.24.5 — pin the visual-to-controller index translation that the
 * inlined queue does when forwarding a drag-reorder to
 * `MediaController.moveMediaItem(from, to)`.
 *
 * After D.24, the visual "up next" list no longer skips the active
 * track at an arbitrary inner position — the active track is the
 * NowPlaying card above the LazyColumn's queue section, and the up-
 * next list starts at controller index `currentIndex + 1`. The
 * translation is a uniform `+ currentIndex + 1` for every visual slot.
 */
class QueueDragDropTest {

  @Test fun visual_zero_maps_to_one_after_active() {
    // Queue: [T0(active), T1, T2, T3]  →  up-next visual: [T1, T2, T3]
    // Visual 0 → controller 1.
    assertEquals(1, translateVisualToReal(currentIndex = 0, visual = 0))
  }

  @Test fun visual_indices_walk_in_lockstep_with_currentIndex_plus_one() {
    // Queue: [T0, T1, T2(active), T3, T4]  →  up-next visual: [T3, T4]
    // Visual 0 → controller 3, visual 1 → controller 4.
    assertEquals(3, translateVisualToReal(currentIndex = 2, visual = 0))
    assertEquals(4, translateVisualToReal(currentIndex = 2, visual = 1))
  }

  @Test fun empty_queue_no_active_track_lands_at_visual() {
    // currentIndex == -1 means the controller has no current item; we
    // bottom out the offset at 0 so the math stays defined for tests
    // / edge cases.
    assertEquals(0, translateVisualToReal(currentIndex = -1, visual = 0))
    assertEquals(2, translateVisualToReal(currentIndex = -1, visual = 2))
  }

  @Test fun firstDifference_detects_swap() {
    val before = listOf("A", "B", "C", "D")
    val after = listOf("A", "C", "B", "D")
    val diff = firstDifference(before, after)
    // The first divergence is at index 1 ("B" vs "C"). The moved
    // entry is "B"; its post-swap index is 2.
    assertEquals(1 to 2, diff)
  }

  @Test fun firstDifference_returns_null_when_lists_equal() {
    val l = listOf("A", "B", "C")
    assertEquals(null, firstDifference(l, l))
  }

  @Test fun firstDifference_handles_long_chains() {
    // Drag-reorder of "C" to position 0 in [A, B, C, D, E] yields
    // [C, A, B, D, E]. The moved item is "C" (orig index 2),
    // landing at index 0.
    val before = listOf("A", "B", "C", "D", "E")
    val after = listOf("C", "A", "B", "D", "E")
    assertEquals(2 to 0, firstDifference(before, after))
  }
}
