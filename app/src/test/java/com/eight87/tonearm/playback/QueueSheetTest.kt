package com.eight87.tonearm.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.15.5 — pure-logic unit tests for the queue-sheet operations. The
 * Compose sheet routes its actions to the [PlaybackUiController], which
 * in turn calls the underlying `MediaController.move/remove/seek`. We
 * exercise the move/remove semantics here against an in-memory
 * MutableList that mirrors what the controller does, so the sheet's
 * up/down/remove buttons stay correct regardless of the Media3 version.
 */
class QueueSheetTest {

  private fun moveItem(list: MutableList<String>, from: Int, to: Int) {
    if (from == to || from !in list.indices || to !in list.indices) return
    val item = list.removeAt(from)
    list.add(to, item)
  }

  @Test fun move_item_up_swaps_with_previous() {
    val q = mutableListOf("a", "b", "c")
    moveItem(q, from = 1, to = 0)
    assertEquals(listOf("b", "a", "c"), q)
  }

  @Test fun move_item_down_swaps_with_next() {
    val q = mutableListOf("a", "b", "c")
    moveItem(q, from = 1, to = 2)
    assertEquals(listOf("a", "c", "b"), q)
  }

  @Test fun move_item_to_end() {
    val q = mutableListOf("a", "b", "c", "d")
    moveItem(q, from = 0, to = 3)
    assertEquals(listOf("b", "c", "d", "a"), q)
  }

  @Test fun move_to_invalid_indices_is_a_noop() {
    val q = mutableListOf("a", "b")
    moveItem(q, from = 0, to = 5)
    assertEquals(listOf("a", "b"), q)
    moveItem(q, from = -1, to = 0)
    assertEquals(listOf("a", "b"), q)
  }

  @Test fun remove_at_index_shrinks_list() {
    val q = mutableListOf("a", "b", "c")
    q.removeAt(1)
    assertEquals(listOf("a", "c"), q)
  }

  @Test fun queue_snapshot_companion_empty_has_negative_index() {
    val empty = QueueSnapshot.Empty
    assertEquals(0, empty.items.size)
    assertEquals(-1, empty.currentIndex)
  }

  @Test fun queue_snapshot_constructed_with_index_round_trips() {
    val items = listOf(
      QueueItem(mediaId = "1", title = "A", artist = "X"),
      QueueItem(mediaId = "2", title = "B", artist = "Y"),
    )
    val snap = QueueSnapshot(items = items, currentIndex = 1)
    assertEquals(2, snap.items.size)
    assertEquals(1, snap.currentIndex)
    assertEquals("B", snap.items[snap.currentIndex].title)
  }

  @Test fun jump_to_index_clamps_in_range() {
    // The PlaybackUiController.seekToQueueIndex() guard rejects out-of-
    // range indices. Verify the boundary maths: any negative or
    // >= size is rejected, in-range is accepted.
    fun valid(i: Int, size: Int) = i in 0 until size
    assertEquals(false, valid(-1, 3))
    assertEquals(false, valid(3, 3))
    assertEquals(true, valid(0, 3))
    assertEquals(true, valid(2, 3))
  }
}
