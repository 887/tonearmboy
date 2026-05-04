package com.eight87.tonearmboy.ui.playing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.eight87.tonearmboy.playback.QueueItem
import com.eight87.tonearmboy.playback.QueueSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.24.5 — pin the quick-filter behaviour against the inlined
 * [QueueSection]:
 *  - typing into the field filters the up-next list to substring
 *    matches on title + artist (case-insensitive)
 *  - while the filter is non-empty, drag handles render in the
 *    "disabled" testTag so the user can't drag inside a filtered subset
 *  - the filter is render-only — the underlying queue snapshot is
 *    unchanged
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueFilterTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun snapshot() = QueueSnapshot(
    items = listOf(
      QueueItem(mediaId = "1", title = "Active Track", artist = "Active Artist"),
      QueueItem(mediaId = "2", title = "Velvet Den", artist = "The Synth Foxes"),
      QueueItem(mediaId = "3", title = "Cipher Light", artist = "The Synth Foxes"),
      QueueItem(mediaId = "4", title = "Field Recording", artist = "Velvet Producer"),
    ),
    currentIndex = 0,
  )

  private fun render() {
    composeRule.setContent {
      MaterialTheme {
        QueueSection(
          snapshot = snapshot(),
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
        )
      }
    }
  }

  @Test
  fun unfiltered_renders_all_queue_rows_with_active_drag_handles() {
    render()
    // D.26.2: full timeline. 4 total items, 1 active + 3 non-active.
    // Each non-active row gets `queue_drag_handle`; the active row gets
    // `queue_drag_handle_disabled` (handle pinned + dimmed).
    composeRule.onAllNodesWithTag("queue_row_active", useUnmergedTree = true)
      .assertCountEquals(1)
    composeRule.onAllNodesWithTag("queue_row", useUnmergedTree = true)
      .assertCountEquals(3)
    composeRule.onAllNodesWithTag("queue_drag_handle", useUnmergedTree = true)
      .assertCountEquals(3)
    composeRule.onAllNodesWithTag("queue_drag_handle_disabled", useUnmergedTree = true)
      .assertCountEquals(1)
  }

  @Test
  fun filter_substring_matches_title_and_artist_case_insensitively() {
    render()
    composeRule.onNodeWithTag("queue_filter_field", useUnmergedTree = true)
      .performTextInput("velvet")
    composeRule.waitForIdle()
    // "Velvet Den" matches by title; "Field Recording" by Velvet Producer
    // artist. "Cipher Light" / "The Synth Foxes" doesn't contain "velvet"
    // anywhere, so it drops out. The active row "Active Track" / "Active
    // Artist" also drops out — filter is render-only and doesn't pin
    // the active row.
    composeRule.onNodeWithText("Velvet Den").assertExists()
    composeRule.onNodeWithText("Field Recording").assertExists()
    composeRule.onAllNodesWithTag("queue_row", useUnmergedTree = true)
      .assertCountEquals(2)
    composeRule.onAllNodesWithTag("queue_row_active", useUnmergedTree = true)
      .assertCountEquals(0)
  }

  @Test
  fun filter_dims_drag_handles_and_disables_reorder() {
    render()
    composeRule.onNodeWithTag("queue_filter_field", useUnmergedTree = true)
      .performTextInput("velvet")
    composeRule.waitForIdle()
    composeRule.onAllNodesWithTag("queue_drag_handle_disabled", useUnmergedTree = true)
      .assertCountEquals(2)
    composeRule.onAllNodesWithTag("queue_drag_handle", useUnmergedTree = true)
      .assertCountEquals(0)
  }

  @Test
  fun empty_filter_match_renders_no_match_state() {
    render()
    composeRule.onNodeWithTag("queue_filter_field", useUnmergedTree = true)
      .performTextInput("xyzabc")
    composeRule.waitForIdle()
    composeRule.onNodeWithText("No tracks match your filter").assertIsDisplayed()
    composeRule.onAllNodesWithTag("queue_row", useUnmergedTree = true)
      .assertCountEquals(0)
  }
}
