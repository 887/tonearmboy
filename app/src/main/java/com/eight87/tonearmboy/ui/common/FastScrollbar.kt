package com.eight87.tonearmboy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * R.A.Q — draggable fast-scroll thumb for [LazyListState].
 *
 * Overlays a slim thumb on the right edge of the parent
 * `BoxWithConstraints`, sized proportionally to viewport / total
 * content. Wider invisible hit-target wraps the visual thumb so a
 * fingertip can grab anywhere in [hitTargetWidth] pixels.
 *
 * Drag the thumb to fast-scroll: each vertical drag delta is
 * accumulated against the **start-of-drag** thumb position (captured
 * via `rememberUpdatedState`, so the lambda never sees a stale
 * value), giving a smooth drag-from-here gesture without jumping.
 *
 * Optional [sectionLabelFor] is a lookup from the current
 * `firstVisibleItemIndex` to a short label (typically a section
 * letter). When supplied AND the user is actively dragging, a
 * bubble renders to the LEFT of the thumb showing that label —
 * Android-style fast-scroller affordance, replaces the always-on
 * alphabet rail.
 *
 * Hidden when the surface isn't scrollable.
 */
@Composable
fun FastScrollbar(
  state: LazyListState,
  modifier: Modifier = Modifier,
  thumbWidth: Dp = 8.dp,
  thumbMinHeight: Dp = 56.dp,
  thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
  hitTargetWidth: Dp = 40.dp,
  sectionLabelFor: ((Int) -> String?)? = null,
) {
  val totalItems by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
  val visibleItems by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.size } }
  // Use canScroll* (not item count) so the thumb shows up even when
  // a single LazyColumn item has tall internal contents — the
  // NowPlaying merged surface is one LazyColumn with three items
  // (now-playing card / transport / queue section), and the queue
  // section's children overflow the viewport.
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
    hitTargetWidth = hitTargetWidth,
    sectionLabelFor = sectionLabelFor,
    onScrollToFraction = { fraction ->
      val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
      val targetIndex = (fraction * maxIndex).toInt().coerceIn(0, maxIndex)
      state.scrollToItem(targetIndex)
    },
  )
}

/**
 * R.A.Q — same draggable thumb for [LazyGridState] (the tile-mode
 * library tabs use grids).
 */
@Composable
fun FastScrollbar(
  state: LazyGridState,
  modifier: Modifier = Modifier,
  thumbWidth: Dp = 8.dp,
  thumbMinHeight: Dp = 56.dp,
  thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
  hitTargetWidth: Dp = 40.dp,
  sectionLabelFor: ((Int) -> String?)? = null,
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
    hitTargetWidth = hitTargetWidth,
    sectionLabelFor = sectionLabelFor,
    onScrollToFraction = { fraction ->
      val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
      val targetIndex = (fraction * maxIndex).toInt().coerceIn(0, maxIndex)
      state.scrollToItem(targetIndex)
    },
  )
}

/**
 * Shared thumb-rendering + drag-handling. Two public overloads
 * (LazyListState / LazyGridState) funnel here.
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
  hitTargetWidth: Dp,
  sectionLabelFor: ((Int) -> String?)?,
  onScrollToFraction: suspend (Float) -> Unit,
) {
  val scope = rememberCoroutineScope()

  // While dragging we override the derived thumb position. The
  // drag-start position needs to be the *current* thumb position,
  // which can have shifted since the pointerInput block was last
  // (re)created — `rememberUpdatedState` keeps a fresh handle.
  var dragTopPxOverride by remember { mutableStateOf<Float?>(null) }
  var isDragging by remember { mutableStateOf(false) }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxHeight()
      .width(hitTargetWidth)
      .semantics { testTag = "fast_scrollbar" },
  ) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trackHeightPx = with(density) { maxHeight.toPx() }
    val totalContentPx = avgItemSizePx * totalItems
    val viewportPx = trackHeightPx
    val maxScrollPx = (totalContentPx - viewportPx).coerceAtLeast(1f)
    val thumbHeightPx = (viewportPx / totalContentPx * trackHeightPx)
      .coerceAtLeast(with(density) { thumbMinHeight.toPx() })
    val maxThumbTopPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    val derivedTopPx = run {
      val currentScroll = firstIndex * avgItemSizePx + firstOffsetPx
      (currentScroll / maxScrollPx).coerceIn(0f, 1f) * maxThumbTopPx
    }
    val thumbTopPx = dragTopPxOverride ?: derivedTopPx
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
    val thumbTopDp = with(density) { thumbTopPx.toDp() }

    // R.A.Q — keep an always-fresh handle to the current thumb top
    // so the drag-start lambda doesn't capture a stale value (which
    // caused the thumb to jump back to wherever it sat when
    // pointerInput last initialised — typically 0).
    val currentThumbTopPx by rememberUpdatedState(thumbTopPx)
    val currentMaxThumbTopPx by rememberUpdatedState(maxThumbTopPx)

    Box(
      modifier = Modifier
        .offset(y = thumbTopDp)
        .fillMaxWidth()
        .height(thumbHeightDp)
        .semantics { testTag = "fast_scrollbar_thumb" }
        .pointerInput(Unit) {
          detectVerticalDragGestures(
            onDragStart = {
              dragTopPxOverride = currentThumbTopPx
              isDragging = true
            },
            onDragEnd = {
              dragTopPxOverride = null
              isDragging = false
            },
            onDragCancel = {
              dragTopPxOverride = null
              isDragging = false
            },
            onVerticalDrag = { _, deltaY ->
              val current = dragTopPxOverride ?: currentThumbTopPx
              val maxTop = currentMaxThumbTopPx
              val next = (current + deltaY).coerceIn(0f, maxTop)
              dragTopPxOverride = next
              val fraction = if (maxTop > 0f) next / maxTop else 0f
              scope.launch { onScrollToFraction(fraction) }
            },
          )
        },
      contentAlignment = Alignment.CenterEnd,
    ) {
      // Visual thumb — slim pill on the right edge of the hit target.
      Box(
        modifier = Modifier
          .width(thumbWidth)
          .fillMaxHeight()
          .clip(RoundedCornerShape(thumbWidth / 2))
          .background(thumbColor),
      )
    }

    // Section-letter bubble — appears to the LEFT of the thumb
    // while dragging, showing the current section letter.
    val label = sectionLabelFor?.invoke(firstIndex)
    if (isDragging && label != null) {
      Box(
        modifier = Modifier
          .offset(
            x = -hitTargetWidth - 8.dp,
            y = thumbTopDp + (thumbHeightDp - 56.dp) / 2,
          )
          .size(56.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.secondaryContainer)
          .semantics { testTag = "fast_scrollbar_bubble" },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(8.dp),
        )
      }
    }
  }
}
