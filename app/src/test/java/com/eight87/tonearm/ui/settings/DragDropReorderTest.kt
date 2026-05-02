package com.eight87.tonearm.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.18.6 — exercise the lift / move / drop bookkeeping the
 * `DragReorderColumn` does internally. The composable's drag state is
 * a small list-shuffle; we test the list-shuffle in isolation here so
 * a regression in the visual rendering can't hide a regression in the
 * order computation.
 */
class DragDropReorderTest {

  /**
   * Mirror of the swap step inside [DragReorderColumn]'s onDrag — the
   * dragged item is removed from its current position and re-inserted
   * at the target index. Returns the new list and the new dragged
   * index (which is the target).
   */
  private fun <T> moveTo(items: List<T>, currentIndex: Int, targetIndex: Int): Pair<List<T>, Int> {
    val safe = targetIndex.coerceIn(0, items.size - 1)
    val swapped = items.toMutableList()
    val moved = swapped.removeAt(currentIndex)
    swapped.add(safe, moved)
    return swapped to safe
  }

  @Test fun drag_down_one_slot_swaps_with_neighbour() {
    val (after, idx) = moveTo(listOf("A", "B", "C", "D"), currentIndex = 1, targetIndex = 2)
    assertEquals(listOf("A", "C", "B", "D"), after)
    assertEquals(2, idx)
  }

  @Test fun drag_up_one_slot_swaps_with_predecessor() {
    val (after, idx) = moveTo(listOf("A", "B", "C", "D"), currentIndex = 2, targetIndex = 1)
    assertEquals(listOf("A", "C", "B", "D"), after)
    assertEquals(1, idx)
  }

  @Test fun drag_to_top_moves_item_to_index_0() {
    val (after, idx) = moveTo(listOf("A", "B", "C", "D"), currentIndex = 3, targetIndex = 0)
    assertEquals(listOf("D", "A", "B", "C"), after)
    assertEquals(0, idx)
  }

  @Test fun drag_to_bottom_moves_item_to_last_index() {
    val (after, idx) = moveTo(listOf("A", "B", "C", "D"), currentIndex = 0, targetIndex = 3)
    assertEquals(listOf("B", "C", "D", "A"), after)
    assertEquals(3, idx)
  }

  @Test fun drag_target_clamps_to_list_bounds() {
    val (after, _) = moveTo(listOf("A", "B", "C"), currentIndex = 1, targetIndex = 99)
    assertEquals(listOf("A", "C", "B"), after)
  }

  @Test fun multi_step_drag_chain_preserves_order_invariants() {
    var list = listOf("A", "B", "C", "D", "E")
    var idx = 0
    val steps = listOf(1, 3, 2, 4, 0)
    for (target in steps) {
      val (after, newIdx) = moveTo(list, idx, target)
      list = after
      idx = newIdx
    }
    // After the chain the moved item ("A") ends up at index 0 (last
    // step) — the list is a permutation of the original five letters.
    assertEquals(setOf("A", "B", "C", "D", "E"), list.toSet())
    assertEquals(5, list.size)
    assertEquals("A", list[0])
  }
}
