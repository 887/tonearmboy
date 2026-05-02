package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.playback.PlaybackUiState
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import com.eight87.tonearm.ui.settings.AlbumCoversMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.24.2 / D.24.6 — pin the merged NowPlaying + queue surface:
 *  - the LazyColumn renders the now-playing card, the transport row,
 *    and the queue section in a single scroll surface
 *  - the queue section ("Up next" header + filter + upcoming items)
 *    sits directly below the transport, no modal sheet
 *  - [QUEUE_LIST_INDEX] points at the queue-section item so the top-
 *    app-bar queue button can `animateScrollToItem` to it
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h3000dp")
class NowPlayingMergedQueueTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun playing() = PlaybackUiState(
    hasMedia = true,
    title = "Cipher Light",
    artist = "The Synth Foxes",
    album = "Velvet Den",
    isPlaying = true,
    positionMs = 5_000,
    durationMs = 120_000,
    hasNext = true,
    hasPrevious = true,
  )

  private fun snapshot() = QueueSnapshot(
    items = listOf(
      QueueItem(mediaId = "1", title = "Cipher Light", artist = "The Synth Foxes"),
      QueueItem(mediaId = "2", title = "Velvet Den", artist = "The Synth Foxes"),
      QueueItem(mediaId = "3", title = "Field Recording", artist = "Field Recordings Trio"),
      QueueItem(mediaId = "4", title = "Quiet Hours", artist = "Field Recordings Trio"),
    ),
    currentIndex = 0,
  )

  private fun render() {
    composeRule.setContent {
      MaterialTheme {
        Surface(modifier = Modifier.size(width = 400.dp, height = 4000.dp)) {
          val listState = rememberLazyListState()
          NowPlayingMergedSurface(
            state = playing(),
            queueSnapshot = snapshot(),
            listState = listState,
            albumCoversMode = AlbumCoversMode.Off,
            onSeek = {},
            onTogglePlayPause = {},
            onSeekBackward = {},
            onSeekForward = {},
            onSeekToPrevious = {},
            onSeekToNext = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onJumpToQueueIndex = {},
            onRemoveQueueItem = {},
            onMoveQueueItem = { _, _ -> },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
    // The LazyColumn is the surface's only child; without
    // `waitForIdle()` the Robolectric main looper sometimes skips the
    // post-measure prefetch pass that brings items 1 and 2 into
    // composition. Drain it so the assertions see the full LazyColumn.
    composeRule.waitForIdle()
  }

  @Test
  fun lazy_column_holds_now_playing_card_transport_row_and_queue_section() {
    render()
    composeRule.onNodeWithTag("now_playing_card", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("now_playing_transport_row", useUnmergedTree = true)
      .assertExists()
    composeRule.onNodeWithTag("queue_section", useUnmergedTree = true).assertExists()
  }

  @Test
  fun queue_section_renders_up_next_header_and_filter_and_rows() {
    render()
    composeRule.onNodeWithTag("queue_up_next_header", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithText("Up next").assertExists()
    composeRule.onNodeWithTag("queue_filter_field", useUnmergedTree = true).assertExists()
    // Queue snapshot has 4 items, currentIndex=0; up-next is items 1..3.
    composeRule.onAllNodesWithTag("queue_row", useUnmergedTree = true)
      .assertCountEquals(3)
  }

  @Test
  fun transport_and_queue_share_the_same_scroll_surface() {
    // Pin: there is no `ModalBottomSheet` testTag in the merged surface.
    // The queue lives inline; tapping the queue-shortcut icon scrolls
    // within the same LazyColumn rather than opening a separate sheet.
    render()
    composeRule.onNodeWithTag("queue_sheet", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun queue_list_index_points_at_queue_section_item() {
    // The top-app-bar queue button uses `LazyListState.animateScrollToItem(QUEUE_LIST_INDEX)`
    // — pin the constant at 2 (item 0 = card, 1 = transport, 2 = queue
    // section). Drift here would silently break the "scroll to queue"
    // affordance.
    assertEquals(2, QUEUE_LIST_INDEX)
  }
}
