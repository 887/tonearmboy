package com.eight87.tonearm.ui.playing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.26.2 / D.26.5 — pin the queue-as-playlist behaviour:
 *  - the row at `currentIndex` carries the `queue_row_active` tag plus
 *    a leading `queue_active_indicator` icon (highlighted background +
 *    bodyLarge title weight are visual; covered by the screenshot)
 *  - tapping a non-current row calls `onJumpTo(thatIndex)` with the
 *    1:1 controller index
 *  - tapping the active row also fires `onJumpTo(currentIndex)` (a
 *    self-seek; no visible disruption since the controller is already
 *    on that index)
 *  - the active row's drag handle renders in the `_disabled` state +
 *    dimmed alpha so the user can't drag it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueActiveRowHighlightTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun snapshot(currentIndex: Int = 1) = QueueSnapshot(
    items = listOf(
      QueueItem(mediaId = "1", title = "Past Track", artist = "Artist A"),
      QueueItem(mediaId = "2", title = "Active Track", artist = "Artist B"),
      QueueItem(mediaId = "3", title = "Next Track", artist = "Artist C"),
      QueueItem(mediaId = "4", title = "Later Track", artist = "Artist D"),
    ),
    currentIndex = currentIndex,
  )

  private fun renderWithCallbacks(
    snapshot: QueueSnapshot,
    onJumpTo: (Int) -> Unit = {},
    onRemove: (Int) -> Unit = {},
    onMove: (Int, Int) -> Unit = { _, _ -> },
  ) {
    composeRule.setContent {
      MaterialTheme {
        QueueSection(
          snapshot = snapshot,
          onJumpTo = onJumpTo,
          onRemove = onRemove,
          onMove = onMove,
        )
      }
    }
  }

  @Test
  fun active_row_carries_active_tag_and_indicator_icon() {
    renderWithCallbacks(snapshot(currentIndex = 1))
    composeRule.onAllNodesWithTag("queue_row_active", useUnmergedTree = true)
      .assertCountEquals(1)
    composeRule.onAllNodesWithTag("queue_active_indicator", useUnmergedTree = true)
      .assertCountEquals(1)
    // Three other items render with the plain queue_row tag.
    composeRule.onAllNodesWithTag("queue_row", useUnmergedTree = true)
      .assertCountEquals(3)
  }

  @Test
  fun tapping_a_non_current_row_calls_onJumpTo_with_real_index() {
    val jumps = mutableListOf<Int>()
    renderWithCallbacks(snapshot(currentIndex = 1), onJumpTo = { jumps += it })
    // The first non-active row in the visual list corresponds to
    // controller index 0 (the past track). Tap it and assert the
    // 1:1 controller index reaches the callback.
    composeRule.onAllNodesWithTag("queue_row", useUnmergedTree = true)[0].performClick()
    assertEquals(listOf(0), jumps)
  }

  @Test
  fun tapping_a_later_row_passes_its_real_index() {
    val jumps = mutableListOf<Int>()
    renderWithCallbacks(snapshot(currentIndex = 1), onJumpTo = { jumps += it })
    // After the active row (index 1) the next two non-active rows are
    // controller indices 2 and 3.
    composeRule.onAllNodesWithTag("queue_row", useUnmergedTree = true)[1].performClick()
    assertEquals(listOf(2), jumps)
  }

  @Test
  fun tapping_the_active_row_invokes_onJumpTo_with_currentIndex() {
    val jumps = mutableListOf<Int>()
    renderWithCallbacks(snapshot(currentIndex = 1), onJumpTo = { jumps += it })
    composeRule.onNodeWithTag("queue_row_active", useUnmergedTree = true).performClick()
    // Self-seek: PlaybackUiController's seekToQueueIndex is a no-op
    // visually but the callback still fires with the active index so
    // any future "snap to start of current track" feature can hang off
    // this surface without rewiring the row click.
    assertEquals(listOf(1), jumps)
  }

  @Test
  fun active_row_drag_handle_is_disabled() {
    renderWithCallbacks(snapshot(currentIndex = 1))
    composeRule.onAllNodesWithTag("queue_drag_handle_disabled", useUnmergedTree = true)
      .assertCountEquals(1)
    composeRule.onAllNodesWithTag("queue_drag_handle", useUnmergedTree = true)
      .assertCountEquals(3)
  }
}
