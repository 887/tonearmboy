package com.eight87.tonearm.ui.playing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.21.3 / D.21.6 — pin the visual-to-controller index translation
 * the queue sheet does when forwarding a drag-reorder to
 * `MediaController.moveMediaItem(from, to)`. Borrows from the
 * D.18.4 `DragDropReorderTest` pattern: the visual swap logic lives
 * in the shared `DragReorderColumn` (already tested by D.18.4); the
 * queue's job on top of that is to map visual positions in the
 * "up-next" list (which skips the active track) back to controller-
 * queue positions, so the move targets the right Media3 slot.
 */
class QueueDragDropTest {

  @Test fun visual_index_below_active_maps_one_to_one() {
    // Queue: [T0, T1, T2(active), T3, T4]
    // Up-next: [T0, T1, T3, T4]
    // Drop at visual position 0 → controller position 0.
    assertEquals(0, computeDestRealIndex(activeIndex = 2, toVisual = 0))
    assertEquals(1, computeDestRealIndex(activeIndex = 2, toVisual = 1))
  }

  @Test fun visual_index_at_or_above_active_bumps_by_one() {
    // Visual 2 sits in the up-next list right after the active track.
    // In controller terms that's slot 3 (active occupies slot 2).
    assertEquals(3, computeDestRealIndex(activeIndex = 2, toVisual = 2))
    assertEquals(4, computeDestRealIndex(activeIndex = 2, toVisual = 3))
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
