package com.eight87.tonearmboy.ui.playing

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
import com.eight87.tonearmboy.playback.PlaybackUiState
import com.eight87.tonearmboy.playback.QueueItem
import com.eight87.tonearmboy.playback.QueueSnapshot
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
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

    // Scroll the LazyColumn DEEP into the queue-section item (index 2).
    // With 50 rows × 56 dp = 2800 dp of queue content, a scrollOffset
    // of 2000 px puts us well past the viewport top inside the queue's
    // middle — the user's actual scenario of scrolling far enough down
    // to see the filter sitting above many remaining queue rows.
    //
    // The first revision of D.26.3 used `fillParentMaxHeight()` only,
    // which kept the no-match placeholder ≤ one viewport tall. That
    // hid the bug for shallow offsets (≈ 200 px) but failed in the
    // user-reported case where scrollOffset >> viewport. The current
    // fix adds `heightIn(min = N × rowHeight)` to the placeholder so
    // the queue section's total height stays ≥ the unfiltered list
    // height, preserving scroll position regardless of depth.
    val state = capturedState!!
    runBlocking { state.scrollToItem(index = 2, scrollOffset = 2000) }
    composeRule.waitForIdle()
    val before = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    assertTrue(
      "expected to be scrolled past the now-playing card; got firstVisible=${before.first}",
      before.first >= 1,
    )
    assertTrue(
      "expected deep scroll offset to repro the original bug; got ${before.second}",
      before.second >= 1500,
    )

    // Type a no-match filter into the queue's filter field. With the
    // D.26.3 fix, the no-match placeholder claims at least N×rowHeight
    // of vertical space (here ≈ 2800 dp), keeping the LazyColumn's
    // total content height stable so the deep scroll position holds.
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

    // Robolectric's LazyColumn doesn't enforce the same scroll
    // clamp-on-shrink that real Android does, so the assertion above
    // can pass even when the bug is present. The structural invariant
    // is that the no-match placeholder reserves at least N×rowHeight
    // of vertical space (so the queue section's measured height stays
    // ≥ what the unfiltered list would have occupied). With 50 rows
    // × 56 dp = 2800 dp expected; we assert ≥ 2700 dp to leave a small
    // tolerance for padding rounding.
    val placeholderHeightDp = composeRule
      .onNodeWithTag("queue_no_match_placeholder", useUnmergedTree = true)
      .fetchSemanticsNode()
      .size
      .height
      .let { px ->
        // Robolectric reports size in pixels at the configured density.
        // qualifiers="w400dp-h800dp" yields density=1.0 by default,
        // so px == dp here.
        px
      }
    assertTrue(
      "no-match placeholder must reserve ≥ N×rowHeight; got ${placeholderHeightDp}px " +
        "(expected ≥ 2700 for 50 rows × 56 dp)",
      placeholderHeightDp >= 2700,
    )
  }
}
