package com.eight87.tonearm.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase F.4 — verify [queueIndicesToRemove] picks the right rows + in
 * the right order.
 *
 * The actual `MediaController.removeMediaItem` work is a thin loop in
 * [PlaybackUiController.removeQueueItemsByMediaIds] over this
 * function's output; standing up a live `MediaController` in
 * Robolectric is more cost than coverage here, so we lock down the
 * pure logic and accept that the controller wiring is exercised by
 * the headless-AVD smoke flow.
 */
class PlaybackQueueDeleteTest {

  @Test
  fun returns_empty_when_nothing_matches() {
    val indices = queueIndicesToRemove(
      queueMediaIds = listOf("1", "2", "3"),
      deletedMediaIds = setOf("999"),
    )
    assertTrue(indices.isEmpty())
  }

  @Test
  fun returns_empty_when_inputs_empty() {
    assertTrue(queueIndicesToRemove(emptyList(), setOf("1")).isEmpty())
    assertTrue(queueIndicesToRemove(listOf("1"), emptySet()).isEmpty())
  }

  @Test
  fun returns_descending_indices_so_removal_does_not_shift() {
    val indices = queueIndicesToRemove(
      queueMediaIds = listOf("a", "b", "c", "d", "e"),
      deletedMediaIds = setOf("a", "c", "e"),
    )
    // Order matters — caller iterates and calls removeMediaItem
    // sequentially without re-walking; descending guarantees no
    // index re-mapping.
    assertEquals(listOf(4, 2, 0), indices)
  }

  @Test
  fun handles_currently_playing_track_removal() {
    // Whether the removed entry is the playing one is not the helper's
    // concern; Media3's MediaController auto-advances after
    // removeMediaItem(currentIndex). The helper only has to surface
    // the index. A queue of [a,b,c] with current=1 deleting "b"
    // returns [1].
    val indices = queueIndicesToRemove(
      queueMediaIds = listOf("a", "b", "c"),
      deletedMediaIds = setOf("b"),
    )
    assertEquals(listOf(1), indices)
  }

  @Test
  fun handles_full_queue_clear() {
    val indices = queueIndicesToRemove(
      queueMediaIds = listOf("1", "2", "3"),
      deletedMediaIds = setOf("1", "2", "3"),
    )
    assertEquals(listOf(2, 1, 0), indices)
  }

  @Test
  fun ignores_deleted_ids_not_in_queue() {
    val indices = queueIndicesToRemove(
      queueMediaIds = listOf("a", "b"),
      deletedMediaIds = setOf("a", "ghost"),
    )
    assertEquals(listOf(0), indices)
  }
}
