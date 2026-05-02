package com.eight87.tonearm.ui.playing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.media3.common.Player
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.21.2 — pin the queue layout: pinned active-track header at top,
 * "Up next" label below, then the upcoming-track list. The active
 * track is NOT in the up-next list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueHeaderTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun snapshot(): QueueSnapshot = QueueSnapshot(
    items = listOf(
      QueueItem(mediaId = "1", title = "Past Track", artist = "Past Artist"),
      QueueItem(mediaId = "2", title = "Active Track", artist = "Active Artist"),
      QueueItem(mediaId = "3", title = "Future One", artist = "Future Artist"),
      QueueItem(mediaId = "4", title = "Future Two", artist = "Other Artist"),
    ),
    currentIndex = 1,
  )

  @Test
  fun active_track_renders_as_pinned_header_with_seek() {
    composeRule.setContent {
      MaterialTheme {
        QueueSheetContent(
          snapshot = snapshot(),
          shuffleEnabled = false,
          repeatMode = Player.REPEAT_MODE_OFF,
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
          onSeek = {},
          onToggleShuffle = {},
          onCycleRepeat = {},
          positionMs = 5_000,
          durationMs = 60_000,
        )
      }
    }
    composeRule.onNodeWithTag("queue_active_header", useUnmergedTree = true).assertIsDisplayed()
    composeRule.onNodeWithText("Active Track").assertIsDisplayed()
    composeRule.onNodeWithTag("queue_active_seek", useUnmergedTree = true).assertExists()
  }

  @Test
  fun up_next_section_label_renders_above_drag_column() {
    composeRule.setContent {
      MaterialTheme {
        QueueSheetContent(
          snapshot = snapshot(),
          shuffleEnabled = false,
          repeatMode = Player.REPEAT_MODE_OFF,
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
          onSeek = {},
          onToggleShuffle = {},
          onCycleRepeat = {},
          positionMs = 0,
          durationMs = 60_000,
        )
      }
    }
    composeRule.onNodeWithText("Up next").assertIsDisplayed()
    // The up-next column renders 3 of 4 queue rows — the active track
    // is pinned in the header and not part of the drag column.
    composeRule.onNodeWithTag("queue_drag_column", useUnmergedTree = true).assertExists()
    composeRule.onAllNodesWithTag("queue_row", useUnmergedTree = true)
      .assertCountEquals(3) // Past + Future One + Future Two
  }
}
