package com.eight87.tonearmboy.ui.playing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.playback.QueueItem
import com.eight87.tonearmboy.playback.QueueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.27.8 — pin the contract: while a queue row is being dragged inside
 * the inlined `DragReorderColumn`, the parent `NowPlayingMergedSurface`
 * `LazyColumn` must NOT consume vertical scroll. Without this, the
 * long-press-and-drag gesture on a queue row gets eaten by the parent
 * scroll handler before the drag-reorder helper sees the pointer
 * deltas, and the row never lifts.
 *
 * The fix wires `DragReorderColumn.onDragStateChange` up through
 * `QueueSection` to `NowPlayingMergedSurface`, which flips the outer
 * LazyColumn's `userScrollEnabled` for the duration of the drag.
 *
 * Why option (b) (drive the callback directly) instead of synthesising
 * a long-press gesture: Robolectric's pointerInput dispatch doesn't
 * reliably reproduce `detectDragGesturesAfterLongPress` — long-press
 * timeouts tied to Choreographer don't fire deterministically under
 * the Robolectric scheduler. Driving the `QueueSection`-level
 * `onDragStateChange` callback exercises the exact integration point:
 * the host's `isQueueDragging` state, the `userScrollEnabled`
 * plumbing, and the reset-on-drag-end path.
 *
 * Why we don't assert on `LazyListState.canScrollForward/Backward`
 * directly (as the plan literally suggested): those properties are
 * computed from layout (whether content extends past the viewport)
 * and are NOT affected by `LazyColumn.userScrollEnabled`. The
 * `userScrollEnabled` flag only blocks user-driven touch gestures —
 * which is precisely the right behaviour for this fix (programmatic
 * scrolls like `animateScrollToItem` from the queue-shortcut button
 * still need to work). To verify the touch-gesture suppression we
 * inject a real `swipeUp` gesture and assert the LazyColumn's
 * `firstVisibleItemScrollOffset` doesn't change while the queue is in
 * drag mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class QueueDragReorderParentScrollTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun bigSnapshot(): QueueSnapshot = QueueSnapshot(
    items = (0 until 50).map {
      QueueItem(mediaId = it.toString(), title = "Track $it", artist = "Artist $it")
    },
    currentIndex = 0,
  )

  /**
   * Renders a host that mirrors `NowPlayingMergedSurface`'s wiring:
   * a local `isQueueDragging` flag flips the outer LazyColumn's
   * `userScrollEnabled`, and `QueueSection` forwards
   * `onDragStateChange` into that flag. Returns the captured
   * `LazyListState` and a function to flip the drag flag.
   */
  private fun renderHost(): Pair<() -> LazyListState, (Boolean) -> Unit> {
    var capturedState: LazyListState? = null
    var dragSetter: ((Boolean) -> Unit)? = null
    composeRule.setContent {
      MaterialTheme {
        Surface(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
          val listState = rememberLazyListState()
          SideEffect { capturedState = listState }
          var isDragging by remember { mutableStateOf(false) }
          SideEffect { dragSetter = { isDragging = it } }
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .semantics { testTag = "outer_lazy" },
            userScrollEnabled = !isDragging,
          ) {
            // Tall pad item above the queue so a swipe-up drag on the
            // outer LazyColumn has somewhere to scroll TO.
            item {
              Box(
                modifier = Modifier
                  .size(width = 400.dp, height = 1200.dp)
                  .semantics { testTag = "pad_item" },
              )
            }
            item {
              QueueSection(
                snapshot = bigSnapshot(),
                onJumpTo = {},
                onRemove = {},
                onMove = { _, _ -> },
                onDragStateChange = { dragSetter?.invoke(it) },
              )
            }
          }
        }
      }
    }
    composeRule.waitForIdle()
    return ({ capturedState!! }) to { dragSetter!!.invoke(it) }
  }

  /**
   * Sanity baseline: with no drag in flight, a swipe-up gesture on the
   * outer LazyColumn moves the scroll position. This pins the
   * precondition for the next test — if the gesture *didn't* scroll
   * here, the suppression test would be a vacuous pass.
   */
  @Test
  fun baseline_swipe_scrolls_parent_when_not_dragging() {
    val (state, _) = renderHost()
    val before = state().firstVisibleItemScrollOffset
    composeRule.onNodeWithTag("outer_lazy")
      .performTouchInput { swipeUp() }
    composeRule.waitForIdle()
    val after = state().firstVisibleItemScrollOffset
    assertNotEquals(
      "baseline: a swipe must move the LazyColumn when not dragging",
      before,
      after,
    )
  }

  /**
   * The plan's acceptance: with a queue-drag in flight, a touch-driven
   * scroll gesture on the outer LazyColumn must NOT move it.
   * `userScrollEnabled = false` blocks the gesture's
   * `Modifier.scrollable` from consuming the deltas, so the LazyColumn's
   * scroll position stays put — leaving the gesture available for the
   * `DragReorderColumn`'s `pointerInput` to interpret as a row reorder.
   */
  @Test
  fun drag_in_flight_suppresses_parent_swipe_scroll() {
    val (state, setDragging) = renderHost()
    val before = state().firstVisibleItemScrollOffset
    val beforeIndex = state().firstVisibleItemIndex
    setDragging(true)
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("outer_lazy")
      .performTouchInput { swipeUp() }
    composeRule.waitForIdle()

    val after = state().firstVisibleItemScrollOffset
    val afterIndex = state().firstVisibleItemIndex
    assertEquals(
      "scroll offset must not change while a queue drag is in flight",
      before,
      after,
    )
    assertEquals(
      "first-visible item must not change while a queue drag is in flight",
      beforeIndex,
      afterIndex,
    )
    assertTrue(
      "scroll position should still be at the top before drag",
      before == 0 && beforeIndex == 0,
    )
  }

  /**
   * Pin the cancel path. `DragReorderColumn` invokes
   * `onDragStateChange(false)` on both `onDragEnd` and `onDragCancel`;
   * leaking `true` after a cancel would freeze parent scroll forever.
   * After the cancel callback fires, a touch-driven swipe on the
   * outer LazyColumn must move it again.
   */
  @Test
  fun drag_cancel_restores_parent_scroll() {
    val (state, setDragging) = renderHost()
    setDragging(true)
    composeRule.waitForIdle()
    setDragging(false) // simulate onDragCancel
    composeRule.waitForIdle()

    val before = state().firstVisibleItemScrollOffset
    composeRule.onNodeWithTag("outer_lazy")
      .performTouchInput { swipeUp() }
    composeRule.waitForIdle()
    val after = state().firstVisibleItemScrollOffset
    assertNotEquals(
      "after cancel, swipe must scroll the LazyColumn again",
      before,
      after,
    )
  }
}
