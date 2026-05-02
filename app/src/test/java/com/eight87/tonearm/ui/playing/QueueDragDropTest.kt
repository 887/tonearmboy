package com.eight87.tonearm.ui.playing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D.26.2 — pin the visual-to-controller index translation and the
 * active-row clamp the queue does when forwarding a drag-reorder to
 * `MediaController.moveMediaItem(from, to)`.
 *
 * After D.26.2, the visual queue list renders the full controller
 * queue 1:1. Drag-reorder excludes the active row: its handle is
 * disabled, and `clampMoveAwayFromActive` additionally guards against
 * any (from, to) pair that touches `currentIndex`.
 */
class QueueDragDropTest {

  @Test fun visual_zero_maps_to_zero_identity_translation() {
    assertEquals(0, translateVisualToReal(currentIndex = 0, visual = 0))
  }

  @Test fun translation_is_identity_for_any_currentIndex() {
    assertEquals(0, translateVisualToReal(currentIndex = 2, visual = 0))
    assertEquals(1, translateVisualToReal(currentIndex = 2, visual = 1))
  }

  @Test fun empty_queue_no_active_track_lands_at_visual() {
    // currentIndex == -1 means the controller has no current item; the
    // identity translation still holds.
    assertEquals(0, translateVisualToReal(currentIndex = -1, visual = 0))
    assertEquals(2, translateVisualToReal(currentIndex = -1, visual = 2))
  }

  @Test fun firstDifference_detects_swap() {
    val before = listOf("A", "B", "C", "D")
    val after = listOf("A", "C", "B", "D")
    val diff = firstDifference(before, after)
    assertEquals(1 to 2, diff)
  }

  @Test fun firstDifference_returns_null_when_lists_equal() {
    val l = listOf("A", "B", "C")
    assertEquals(null, firstDifference(l, l))
  }

  @Test fun firstDifference_handles_long_chains() {
    val before = listOf("A", "B", "C", "D", "E")
    val after = listOf("C", "A", "B", "D", "E")
    assertEquals(2 to 0, firstDifference(before, after))
  }

  // -- clampMoveAwayFromActive --------------------------------------

  @Test fun clamp_passes_through_moves_that_dont_touch_active() {
    assertEquals(0 to 3, clampMoveAwayFromActive(currentIndex = 5, from = 0, to = 3))
    assertEquals(7 to 6, clampMoveAwayFromActive(currentIndex = 5, from = 7, to = 6))
  }

  @Test fun clamp_drops_move_when_from_is_active() {
    assertNull(clampMoveAwayFromActive(currentIndex = 4, from = 4, to = 1))
  }

  @Test fun clamp_shifts_to_when_destination_is_active_dragging_down() {
    // Moving a row from above the active position to land on the
    // active position should drop just before it (currentIndex - 1).
    assertEquals(0 to 3, clampMoveAwayFromActive(currentIndex = 4, from = 0, to = 4))
  }

  @Test fun clamp_shifts_to_when_destination_is_active_dragging_up() {
    // Moving a row from below the active position to land on the
    // active position should drop just after it (currentIndex + 1).
    assertEquals(8 to 5, clampMoveAwayFromActive(currentIndex = 4, from = 8, to = 4))
  }

  @Test fun clamp_returns_null_when_from_equals_clamped_to() {
    // from == clampedTo after the destination shift means the move
    // collapses to a no-op.
    assertEquals(null, clampMoveAwayFromActive(currentIndex = 4, from = 3, to = 4))
  }

  @Test fun clamp_passes_through_when_no_active_track() {
    // currentIndex == -1 → no active row → no clamp needed.
    assertEquals(0 to 2, clampMoveAwayFromActive(currentIndex = -1, from = 0, to = 2))
  }
}
