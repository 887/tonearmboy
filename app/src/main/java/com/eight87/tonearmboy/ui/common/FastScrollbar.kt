package com.eight87.tonearmboy.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
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

  // Cache per-index measured size. The avg-of-currently-visible math
  // oscillates wildly on merged surfaces where one item dwarfs the
  // others (the now-playing card + transport + queue case): when only
  // the queue item is visible the average snaps up to the queue's
  // height, then back down when the cover re-enters viewport. Caching
  // each item's size on first measurement and using the cumulative
  // offset keeps the thumb position stable.
  val sizesByIndex = remember { mutableStateMapOf<Int, Int>() }
  LaunchedEffect(state) {
    snapshotFlow { state.layoutInfo.visibleItemsInfo }.collect { visible ->
      visible.forEach { sizesByIndex[it.index] = it.size }
    }
  }

  val firstIndex by remember { derivedStateOf { state.firstVisibleItemIndex } }
  val firstOffset by remember { derivedStateOf { state.firstVisibleItemScrollOffset } }
  val viewportPx by remember {
    derivedStateOf { state.layoutInfo.viewportSize.height.toFloat() }
  }
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
    sizesByIndex = sizesByIndex,
    explicitViewportPx = viewportPx,
    isScrollInProgress = state.isScrollInProgress,
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
    isScrollInProgress = state.isScrollInProgress,
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

/** Linger window after a scroll stops — bubble fades out [LINGER_MS] later. */
private const val LINGER_MS = 600L

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
  sizesByIndex: Map<Int, Int> = emptyMap(),
  explicitViewportPx: Float? = null,
  isScrollInProgress: Boolean = false,
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

  // Linger pattern (mirrors shutterboy's `YearScrubber.kt`): the
  // section bubble is visible while the user actively scrolls or
  // drags, and stays visible for [LINGER_MS] after that input stops
  // before fading out — gives the eye time to read the letter without
  // strobing the bubble on every quick flick. When neither input is
  // active for the linger window, `lingering` flips false and
  // AnimatedVisibility fades the bubble out.
  var lingering by remember { mutableStateOf(false) }
  LaunchedEffect(isScrollInProgress, isDragging) {
    if (isScrollInProgress || isDragging) {
      lingering = true
    } else {
      delay(LINGER_MS)
      lingering = false
    }
  }
  val bubbleVisible = isScrollInProgress || isDragging || lingering

  BoxWithConstraints(
    modifier = modifier
      .fillMaxHeight()
      .width(hitTargetWidth)
      .semantics { testTag = "fast_scrollbar" },
  ) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trackHeightPx = with(density) { maxHeight.toPx() }
    // Stable cumulative-size math: items we've measured contribute
    // their actual height; items we haven't yet seen estimate via the
    // current avg. Both totalContentPx and currentScroll use the same
    // table so they stay coherent as items enter/leave the viewport.
    val itemSizeOf: (Int) -> Float = { idx ->
      sizesByIndex[idx]?.toFloat() ?: avgItemSizePx
    }
    val totalContentPx = (0 until totalItems).sumOf { itemSizeOf(it).toDouble() }.toFloat()
    val viewportPx = (explicitViewportPx ?: trackHeightPx).coerceAtLeast(1f)
    val maxScrollPx = (totalContentPx - viewportPx).coerceAtLeast(1f)
    val thumbHeightPx = (viewportPx / totalContentPx.coerceAtLeast(1f) * trackHeightPx)
      .coerceAtLeast(with(density) { thumbMinHeight.toPx() })
    val maxThumbTopPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    val derivedTopPx = run {
      val priorPx = (0 until firstIndex).sumOf { itemSizeOf(it).toDouble() }.toFloat()
      val currentScroll = priorPx + firstOffsetPx
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

    // Section-letter bubble — appears to the LEFT of the thumb during
    // active scroll, drag, or while lingering after either stops.
    // AnimatedVisibility handles the fade so the bubble doesn't strobe
    // when the user does quick repeated flicks.
    val label = sectionLabelFor?.invoke(firstIndex)
    AnimatedVisibility(
      visible = bubbleVisible && label != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.offset(
        x = -hitTargetWidth - 8.dp,
        y = thumbTopDp + (thumbHeightDp - 56.dp) / 2,
      ),
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.secondaryContainer)
          .semantics { testTag = "fast_scrollbar_bubble" },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label.orEmpty(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(8.dp),
        )
      }
    }
  }
}
