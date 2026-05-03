package com.eight87.tonearm.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.27.10 — pin the cross-boundary drag contract.
 *
 * User-reported bug: dragging a queue row across an item boundary
 * cancelled the drag mid-gesture. Root cause was that the original
 * `DragReorderColumn` keyed each row's `Modifier.pointerInput(itemKey,
 * working.size)` by item, but the `forEachIndexed` body was NOT wrapped
 * in `key(itemId) { … }`. Without that wrap, composables are keyed by
 * iteration POSITION; when `working` reorders mid-drag the Box at
 * position N now renders a different item, the `pointerInput` key
 * changes from `itemKey(A)` to `itemKey(B)`, the running coroutine is
 * cancelled, and the drag dies the moment it crosses any boundary.
 *
 * The fix:
 *  1. Wrap the per-item body in `key(itemId)` so composable identity
 *     follows the item, not the position.
 *  2. Drop `working.size` from the `pointerInput` keys — only itemId
 *     matters, and including a size that never changes during a single
 *     drag is just a tripwire.
 *  3. Resolve the start index dynamically in `onDragStart` via
 *     `working.indexOf(item)` so a *fresh* long-press after a previous
 *     reorder doesn't read a stale captured `index`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class DragReorderCrossBoundaryTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun drag_across_boundary_swaps_items_and_emits_reorder() {
    val items = listOf("A", "B", "C", "D", "E")
    val rowHeightDp = 56
    var lastReordered: List<String>? = null

    composeRule.setContent {
      MaterialTheme {
        Surface(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
          DragReorderColumn(
            items = items,
            itemKey = { it },
            rowHeightDp = rowHeightDp,
            testTagPrefix = "test",
            onReordered = { lastReordered = it },
          ) { item, handleModifier ->
            Box(modifier = Modifier.fillMaxWidth()) {
              Text(text = item)
              Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Reorder $item",
                modifier = handleModifier
                  .size(40.dp)
                  .semantics { testTag = "drag_handle_$item" },
              )
            }
          }
        }
      }
    }
    composeRule.waitForIdle()

    // Drag item C (index 2) UP across item B (index 1) — needs to
    // travel slightly more than half a row to flip past one boundary.
    // Use 1.5× row height so the snap math is unambiguous.
    val rowPx = with(composeRule.density) { rowHeightDp.dp.toPx() }
    val deltaPx = -(1.5f * rowPx)

    composeRule.onNodeWithTag("drag_handle_C", useUnmergedTree = true).performTouchInput {
      down(center)
      // > LongPressTimeoutMillis (500ms) so the drag-after-long-press
      // detector lifts the row before any movement.
      advanceEventTime(700)
      // Move up across the B/C boundary in two steps so the
      // pointer-input pipeline sees discrete deltas, not one giant
      // jump (which would still work but is less faithful to the
      // user's continuous-drag scenario).
      moveBy(Offset(0f, deltaPx / 2f))
      advanceEventTime(50)
      moveBy(Offset(0f, deltaPx / 2f))
      advanceEventTime(50)
      up()
    }
    composeRule.waitForIdle()

    // After dragging C up by 1.5 rows it should land at index 1
    // (between A and B) — i.e. final order [A, C, B, D, E]. Without
    // the `key(itemId)` fix the pointerInput coroutine cancels the
    // moment the swap happens (when targetDelta=-1 fires inside
    // onDrag), `onReordered` never gets called, and `lastReordered`
    // stays null.
    assertNotNull(
      "onReordered must fire — without `key(itemId)` the drag dies " +
        "the moment it crosses the first boundary and never reaches onDragEnd",
      lastReordered,
    )
    assertEquals(
      "C should land at index 1 after a 1.5-row upward drag",
      listOf("A", "C", "B", "D", "E"),
      lastReordered,
    )
  }
}
