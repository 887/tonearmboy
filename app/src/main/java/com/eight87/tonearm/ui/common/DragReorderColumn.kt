package com.eight87.tonearm.ui.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * D.18.4 / D.21.3 — long-press to lift, drag to reorder, release to
 * drop. Originally shipped as a private helper inside
 * `LibraryTabsDialog.kt`; promoted to a shared composable in D.21.3 so
 * the queue sheet can reuse it without duplicating the swap logic.
 *
 * Items render in a `Column` (no lazy virtualization — bounded row
 * counts only). The drag handle is the `Modifier` passed into
 * [rowContent]; rows attach it to whatever leading or trailing icon
 * acts as the lift target. Long-press on that icon flips the row into
 * dragging mode and the drag-Y translation re-orders the list in
 * place; release commits via [onReordered].
 *
 * Vertical-only drag, fixed row height. Computes the target index by
 * snapping the drag delta to whole row-heights. Items animate to
 * their new position via offset modifiers; we don't use a lazy list
 * here because the dialog's row count is bounded (5 built-ins + a
 * realistic upper bound of maybe a dozen custom tabs, or a queue
 * that's already bounded by the number of tracks the user picked).
 *
 * The pattern is the same one documented in `android-skills` under
 * "compose-drag-and-drop" — the third-party library
 * `sh.calvin.reorderable` would be functionally equivalent but adds a
 * dependency for ~150 lines of code we can write inline.
 */
@Composable
fun <T : Any> DragReorderColumn(
  items: List<T>,
  itemKey: (T) -> String,
  rowHeightDp: Int,
  testTagPrefix: String,
  onReordered: (List<T>) -> Unit,
  rowContent: @Composable (T, Modifier) -> Unit,
) {
  val density = LocalDensity.current
  val rowPx = with(density) { rowHeightDp.dp.toPx() }
  // Local working copy so drag updates animate before the parent
  // notifies. We sync to the supplied [items] when the parent
  // pushes a new list.
  var working by remember(items) { mutableStateOf(items) }
  LaunchedEffect(items) { working = items }

  var draggingIndex by remember { mutableStateOf(-1) }
  var dragOffsetPx by remember { mutableStateOf(0f) }

  Column(modifier = Modifier
    .fillMaxWidth()
    .semantics { testTag = "${testTagPrefix}_drag_column" }) {
    working.forEachIndexed { index, item ->
      val isDragging = index == draggingIndex
      val translateY = if (isDragging) dragOffsetPx else 0f
      val key = itemKey(item)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(rowHeightDp.dp)
          .zIndex(if (isDragging) 1f else 0f)
          .offset { IntOffset(0, translateY.roundToInt()) }
      ) {
        val handleModifier = Modifier.pointerInput(key, working.size) {
          detectDragGesturesAfterLongPress(
            onDragStart = {
              draggingIndex = index
              dragOffsetPx = 0f
            },
            onDrag = { _, drag ->
              dragOffsetPx += drag.y
              // Snap to a target index if the drag passes the
              // threshold of half a row.
              val current = draggingIndex
              if (current >= 0) {
                val targetDelta = (dragOffsetPx / rowPx).roundToInt()
                val target = (current + targetDelta).coerceIn(0, working.size - 1)
                if (target != current) {
                  val swapped = working.toMutableList()
                  val moved = swapped.removeAt(current)
                  swapped.add(target, moved)
                  working = swapped
                  draggingIndex = target
                  // Normalize the running offset so the visual
                  // doesn't jump after the swap.
                  dragOffsetPx -= (target - current) * rowPx
                }
              }
            },
            onDragEnd = {
              draggingIndex = -1
              dragOffsetPx = 0f
              if (working != items) onReordered(working)
            },
            onDragCancel = {
              draggingIndex = -1
              dragOffsetPx = 0f
              working = items
            },
          )
        }
        rowContent(item, handleModifier)
      }
    }
  }
}
