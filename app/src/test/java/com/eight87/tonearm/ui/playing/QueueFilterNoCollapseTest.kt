package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.playback.PlaybackUiState
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import com.eight87.tonearm.ui.settings.AlbumCoversMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.26.3 / D.26.5 — pin the no-collapse behaviour: scrolling the
 * NowPlaying surface, then typing a no-match filter, must NOT reset
 * the LazyColumn scroll position back to the top.
 *
 * Implementation: when the filter narrows to zero matches, the queue
 * section's no-match placeholder uses `Modifier.fillParentMaxHeight()`
 * (passed in from the parent's `LazyItemScope`), so the LazyColumn's
 * total content height stays at least one viewport tall and the
 * scroll position is preserved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class QueueFilterNoCollapseTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun playing() = PlaybackUiState(
    hasMedia = true,
    title = "Track 0",
    artist = "Artist 0",
    album = "Album 0",
    isPlaying = true,
    positionMs = 5_000,
    durationMs = 120_000,
    hasNext = true,
    hasPrevious = false,
  )

  private fun bigSnapshot(): QueueSnapshot = QueueSnapshot(
    items = (0 until 50).map {
      QueueItem(mediaId = it.toString(), title = "Track $it", artist = "Artist $it")
    },
    currentIndex = 0,
  )

  @Test
  fun no_match_filter_does_not_reset_scroll_to_top() {
    var capturedState: LazyListState? = null
    composeRule.setContent {
      MaterialTheme {
        Surface(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
          val listState = rememberLazyListState()
          SideEffect { capturedState = listState }
          NowPlayingMergedSurface(
            state = playing(),
            queueSnapshot = bigSnapshot(),
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
    composeRule.waitForIdle()

    // Scroll the LazyColumn down to the queue-section item (index 2)
    // and force a non-zero scroll offset. We exercise the
    // `scrollToItem` API directly because Robolectric's input pipeline
    // for LazyColumn drag input is awkward.
    val state = capturedState!!
    runBlocking { state.scrollToItem(index = 2, scrollOffset = 200) }
    composeRule.waitForIdle()
    val before = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    assertTrue(
      "expected to be scrolled past the now-playing card; got firstVisible=${before.first}",
      before.first >= 1,
    )

    // Type a no-match filter into the queue's filter field. With the
    // D.26.3 fix, the no-match placeholder claims at least one
    // viewport's worth of vertical space so the LazyColumn's total
    // content height doesn't drop below the viewport.
    composeRule.onNodeWithTag("queue_filter_field", useUnmergedTree = true)
      .performTextInput("xyzabc")
    composeRule.waitForIdle()

    // Scroll position must NOT have reset to (0, 0).
    val after = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    assertEquals(
      "filter should not change firstVisibleItemIndex when content stays viewport-tall",
      before.first,
      after.first,
    )
  }
}
