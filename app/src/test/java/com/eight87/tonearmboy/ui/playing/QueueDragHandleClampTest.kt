package com.eight87.tonearmboy.ui.playing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.eight87.tonearmboy.playback.QueueItem
import com.eight87.tonearmboy.playback.QueueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.26.2 / D.26.5 — pin the drag-handle clamp on the active row:
 *  - the active row's drag handle renders disabled + dimmed (testTag
 *    `queue_drag_handle_disabled`)
 *  - `clampMoveAwayFromActive` rejects moves where `from` equals
 *    `currentIndex`
 *  - `clampMoveAwayFromActive` shifts `to` by ±1 when the destination
 *    is the active row, preserving the user's drag direction
 *  - `clampMoveAwayFromActive` returns null when `from == clampedTo`
 *    (the move collapses to a no-op).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueDragHandleClampTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun snapshot(currentIndex: Int = 2) = QueueSnapshot(
    items = listOf(
      QueueItem(mediaId = "0", title = "T0", artist = "A"),
      QueueItem(mediaId = "1", title = "T1", artist = "A"),
      QueueItem(mediaId = "2", title = "Active", artist = "A"),
      QueueItem(mediaId = "3", title = "T3", artist = "A"),
      QueueItem(mediaId = "4", title = "T4", artist = "A"),
    ),
    currentIndex = currentIndex,
  )

  @Test
  fun active_row_drag_handle_renders_disabled_and_dimmed() {
    composeRule.setContent {
      MaterialTheme {
        QueueSection(
          snapshot = snapshot(currentIndex = 2),
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
        )
      }
    }
    composeRule.onAllNodesWithTag("queue_drag_handle_disabled", useUnmergedTree = true)
      .assertCountEquals(1)
    composeRule.onAllNodesWithTag("queue_drag_handle", useUnmergedTree = true)
      .assertCountEquals(4)
  }

  // -- clampMoveAwayFromActive --------------------------------------

  @Test
  fun clamp_passes_through_moves_with_neither_endpoint_active() {
    assertEquals(0 to 1, clampMoveAwayFromActive(currentIndex = 2, from = 0, to = 1))
    assertEquals(4 to 3, clampMoveAwayFromActive(currentIndex = 2, from = 4, to = 3))
  }

  @Test
  fun clamp_drops_move_when_from_is_currentIndex() {
    assertNull(clampMoveAwayFromActive(currentIndex = 2, from = 2, to = 0))
    assertNull(clampMoveAwayFromActive(currentIndex = 2, from = 2, to = 4))
  }

  @Test
  fun clamp_shifts_to_destination_above_active_when_dragging_down_into_it() {
    // from=0 to=2 with active=2 → drop the row at currentIndex - 1 = 1.
    assertEquals(0 to 1, clampMoveAwayFromActive(currentIndex = 2, from = 0, to = 2))
  }

  @Test
  fun clamp_shifts_to_destination_below_active_when_dragging_up_into_it() {
    // from=4 to=2 with active=2 → drop the row at currentIndex + 1 = 3.
    assertEquals(4 to 3, clampMoveAwayFromActive(currentIndex = 2, from = 4, to = 2))
  }

  @Test
  fun clamp_returns_null_when_from_equals_clamped_to() {
    // from=1 to=2 with active=2 → clamped to=1, equals from → null.
    assertNull(clampMoveAwayFromActive(currentIndex = 2, from = 1, to = 2))
    // from=3 to=2 with active=2 → clamped to=3, equals from → null.
    assertNull(clampMoveAwayFromActive(currentIndex = 2, from = 3, to = 2))
  }
}
