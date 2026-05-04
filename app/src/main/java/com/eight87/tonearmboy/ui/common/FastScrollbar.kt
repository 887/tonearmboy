package com.eight87.tonearmboy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * R.A.Q — draggable fast-scroll thumb for [LazyListState].
 *
 * Overlays a thin track + thumb on the right edge of the parent
 * `BoxWithConstraints`. Track + thumb sizing is derived from
 * `state.layoutInfo`:
 *  - thumb height ≈ (visible items / total items) of the available
 *    track height, clamped to [thumbMinHeight].
 *  - thumb top offset reflects `firstVisibleItemIndex` proportional
 *    to the scrollable range.
 *
 * Drag the thumb to fast-scroll: each vertical drag delta maps to a
 * fractional scroll position, which is converted into a target
 * `(itemIndex, scrollOffsetPx)` and applied via [LazyListState.scrollToItem].
 *
 * Coexists with the right-edge alphabet rail in the library tabs:
 * the alphabet rail handles "jump to letter"; this thumb handles
 * "drag to position".
 *
 * Hidden when the list is short enough that everything fits in the
 * viewport — no thumb when there's nothing to scroll past.
 */
@Composable
fun FastScrollbar(
  state: LazyListState,
  modifier: Modifier = Modifier,
  thumbWidth: Dp = 6.dp,
  thumbMinHeight: Dp = 48.dp,
  thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
  trackPadding: Dp = 2.dp,
) {
  val totalItems by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
  val visibleItems by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.size } }
  // Use canScroll* (not item count) so the thumb shows up even when a
  // single LazyColumn item has tall internal contents — e.g. the
  // NowPlaying merged surface is one big LazyColumn with three items
  // (now-playing card / transport / queue section), and the queue
  // section's children overflow the viewport. visibleItemsInfo would
  // report 3 of 3, hiding the thumb; canScroll* catches the overflow.
  val canScroll by remember {
    derivedStateOf { state.canScrollForward || state.canScrollBackward }
  }
  if (totalItems == 0 || !canScroll) return

  val firstIndex by remember { derivedStateOf { state.firstVisibleItemIndex } }
  val firstOffset by remember { derivedStateOf { state.firstVisibleItemScrollOffset } }
  val avgItemSizePx by remember {
    derivedStateOf {
      val items = state.layoutInfo.visibleItemsInfo
      if (items.isEmpty()) 1f else items.map { it.size }.average().toFloat()
    }
  }

  ScrollbarThumb(
    modifier = modifier,
    totalItems = totalItems,
    firstIndex = firstIndex,
    firstOffsetPx = firstOffset.toFloat(),
    avgItemSizePx = avgItemSizePx,
    thumbWidth = thumbWidth,
    thumbMinHeight = thumbMinHeight,
    thumbColor = thumbColor,
    trackPadding = trackPadding,
    onScrollToFraction = { fraction ->
      val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
      val targetIndex = (fraction * maxIndex).toInt().coerceIn(0, maxIndex)
      state.scrollToItem(targetIndex)
    },
  )
}

/**
 * R.A.Q — same draggable thumb for [LazyGridState] (the tile-mode
 * tabs in the library use grids). Item sizing is per-row of the
 * grid; the math collapses cleanly because `firstVisibleItemIndex`
 * still moves one row's worth of items at a time.
 */
@Composable
fun FastScrollbar(
  state: LazyGridState,
  modifier: Modifier = Modifier,
  thumbWidth: Dp = 6.dp,
  thumbMinHeight: Dp = 48.dp,
  thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
  trackPadding: Dp = 2.dp,
) {
  val totalItems by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
  val visibleItems by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.size } }
  val canScroll by remember {
    derivedStateOf { state.canScrollForward || state.canScrollBackward }
  }
  if (totalItems == 0 || !canScroll) return

  val firstIndex by remember { derivedStateOf { state.firstVisibleItemIndex } }
  val firstOffset by remember { derivedStateOf { state.firstVisibleItemScrollOffset } }
  val avgItemSizePx by remember {
    derivedStateOf {
      val items = state.layoutInfo.visibleItemsInfo
      if (items.isEmpty()) 1f else items.map { it.size.height }.average().toFloat()
    }
  }

  ScrollbarThumb(
    modifier = modifier,
    totalItems = totalItems,
    firstIndex = firstIndex,
    firstOffsetPx = firstOffset.toFloat(),
    avgItemSizePx = avgItemSizePx,
    thumbWidth = thumbWidth,
    thumbMinHeight = thumbMinHeight,
    thumbColor = thumbColor,
    trackPadding = trackPadding,
    onScrollToFraction = { fraction ->
      val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
      val targetIndex = (fraction * maxIndex).toInt().coerceIn(0, maxIndex)
      state.scrollToItem(targetIndex)
    },
  )
}

/**
 * Shared thumb-rendering + drag-handling. Kept private because the
 * two public overloads are the supported entry points; this layer
 * just removes duplication between them.
 */
@Composable
private fun ScrollbarThumb(
  modifier: Modifier,
  totalItems: Int,
  firstIndex: Int,
  firstOffsetPx: Float,
  avgItemSizePx: Float,
  thumbWidth: Dp,
  thumbMinHeight: Dp,
  thumbColor: Color,
  trackPadding: Dp,
  onScrollToFraction: suspend (Float) -> Unit,
) {
  val scope = rememberCoroutineScope()
  // Active drag state. While dragging we track the thumb's top in
  // pixels independent of the LazyState — fractional scroll goes
  // out, the LazyState catches up, and on next recomposition the
  // derived `thumbTopPx` from state matches what we set here.
  var dragTopPxOverride by remember { mutableStateOf<Float?>(null) }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxHeight()
      .width(thumbWidth + trackPadding * 2)
      .semantics { testTag = "fast_scrollbar" },
  ) {
    val trackHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
    val totalContentPx = avgItemSizePx * totalItems
    val viewportPx = trackHeightPx
    val maxScrollPx = (totalContentPx - viewportPx).coerceAtLeast(1f)
    val thumbHeightPx = (viewportPx / totalContentPx * trackHeightPx)
      .coerceAtLeast(with(androidx.compose.ui.platform.LocalDensity.current) { thumbMinHeight.toPx() })
    val maxThumbTopPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    val derivedTopPx = run {
      val currentScroll = firstIndex * avgItemSizePx + firstOffsetPx
      (currentScroll / maxScrollPx).coerceIn(0f, 1f) * maxThumbTopPx
    }
    val thumbTopPx = dragTopPxOverride ?: derivedTopPx
    val thumbHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) { thumbHeightPx.toDp() }
    val thumbTopDp = with(androidx.compose.ui.platform.LocalDensity.current) { thumbTopPx.toDp() }

    androidx.compose.foundation.layout.Box(
      modifier = Modifier
        .offset(x = trackPadding, y = thumbTopDp)
        .width(thumbWidth)
        .height(thumbHeightDp)
        .clip(RoundedCornerShape(thumbWidth / 2))
        .background(thumbColor)
        .semantics { testTag = "fast_scrollbar_thumb" }
        .pointerInput(maxThumbTopPx, totalItems) {
          detectVerticalDragGestures(
            onDragStart = { dragTopPxOverride = thumbTopPx },
            onDragEnd = { dragTopPxOverride = null },
            onDragCancel = { dragTopPxOverride = null },
            onVerticalDrag = { _, deltaY ->
              val current = dragTopPxOverride ?: thumbTopPx
              val next = (current + deltaY).coerceIn(0f, maxThumbTopPx)
              dragTopPxOverride = next
              val fraction = if (maxThumbTopPx > 0f) next / maxThumbTopPx else 0f
              scope.launch { onScrollToFraction(fraction) }
            },
          )
        }
        .pointerInput(maxThumbTopPx, totalItems) {
          // Tap on the thumb itself doesn't scroll — but a tap on the
          // surrounding track region (caught by the track's pointer
          // input below) does. Empty handler here keeps the drag
          // gesture from being intercepted by track-tap detection.
          detectTapGestures()
        },
    )
  }
}
