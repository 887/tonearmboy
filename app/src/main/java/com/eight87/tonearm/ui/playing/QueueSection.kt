package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import com.eight87.tonearm.ui.common.DragReorderColumn

/**
 * D.26.2 — queue rendered as a playlist-style timeline. *Every*
 * controller queue item is shown (indices `0..mediaItemCount - 1`),
 * not just the slice after `currentIndex`. The active row is
 * highlighted in place with `primaryContainer` background, a leading
 * "now playing" speaker icon, and a `bodyLarge`/medium-weight title;
 * other rows render the same as before.
 *
 * Tapping any row calls `onJumpTo(realIndex)` — including tapping the
 * active row, which is a self-seek (no-op visually since the
 * controller is already on that index, but harmless). This means the
 * user can scroll back to a previously-played row and skip "two songs
 * back" without leaving the queue surface.
 *
 * Drag-drop reorder excludes the active row: its handle is disabled
 * and dimmed, and the [onMove] callback clamps `from`/`to` to indices
 * that are not the active one. Media3's behaviour around moving the
 * playing item is awkward, so we keep it pinned in place.
 *
 * Visual-to-controller index translation simplifies to 1:1 when the
 * full queue is visible (no more `currentIdx + 1` offset).
 */
@OptIn(UnstableApi::class)
@Composable
fun QueueSection(
  snapshot: QueueSnapshot,
  onJumpTo: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onMove: (from: Int, to: Int) -> Unit,
  modifier: Modifier = Modifier,
  /**
   * D.26.3 — modifier applied to the queue section's outer column so
   * the parent `LazyColumn` keeps a stable content height when the
   * filter narrows to zero matches. The caller (inside a lazy item
   * block) passes `Modifier.fillParentMaxHeight()` so the queue claims
   * at least the viewport's worth of vertical space; without this,
   * a zero-match filter would collapse the section to its empty-state
   * placeholder, shrink the LazyColumn's total content height below
   * the viewport, and force scroll position back to top.
   */
  noMatchFillModifier: Modifier = Modifier,
  /**
   * D.27.4 — parent LazyColumn's viewport height in Dp, supplied by
   * the host's `BoxWithConstraints`. Used to compute the outer
   * column's min-height so even N=1 queues reserve ≥ one viewport
   * worth of scroll room (the user reported "*single-song queue can't
   * scroll down one screenlength*"). Default 0.dp keeps existing
   * tests / call sites working without a viewport hint, in which case
   * the section just sizes itself to N × rowHeight.
   */
  parentViewportHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
  var filter by remember { mutableStateOf("") }
  val filterActive = filter.isNotBlank()

  val items = snapshot.items
  val currentIndex = snapshot.currentIndex

  // D.26.2: render the full queue as a positional timeline. Each
  // visual entry knows its 1:1 controller index so taps / removes /
  // drags forward without offset arithmetic.
  data class QueueEntry(val realIndex: Int, val item: QueueItem, val isActive: Boolean)
  val allEntries: List<QueueEntry> = items.mapIndexed { i, qi ->
    QueueEntry(realIndex = i, item = qi, isActive = i == currentIndex)
  }

  val needle = filter.trim().lowercase()
  val visibleEntries: List<QueueEntry> = if (filterActive) {
    allEntries.filter {
      it.item.title.lowercase().contains(needle) ||
        it.item.artist.lowercase().contains(needle)
    }
  } else allEntries

  // D.27.4 — `imePadding()` keeps the active row visible when the
  // on-screen keyboard pops up for the filter field. The min-height
  // is computed as `max(parentViewportHeight, N × rowHeight)` so even
  // a single-track queue (N=1, ~56dp) reserves at least one viewport
  // worth of scroll room without forcing a 100-track queue to clip
  // back down to viewport.
  val byRows = (allEntries.size * QUEUE_ROW_HEIGHT_DP).dp
  val outerMin = if (parentViewportHeight > byRows) parentViewportHeight else byRows
  Column(
    modifier = modifier
      .fillMaxWidth()
      .imePadding()
      .heightIn(min = outerMin)
      .semantics { testTag = "queue_section" },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    HorizontalDivider()
    Text(
      text = "Queue",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.semantics { testTag = "queue_up_next_header" },
    )

    OutlinedTextField(
      value = filter,
      onValueChange = { filter = it },
      singleLine = true,
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      trailingIcon = {
        if (filterActive) {
          IconButton(
            onClick = { filter = "" },
            modifier = Modifier.semantics { testTag = "queue_filter_clear" },
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Clear filter")
          }
        }
      },
      placeholder = { Text("Filter queue") },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { testTag = "queue_filter_field" },
    )

    if (allEntries.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text("Nothing in the queue", style = MaterialTheme.typography.bodyMedium)
      }
    } else if (visibleEntries.isEmpty()) {
      // D.26.3 — claim the SAME vertical space the unfiltered list
      // would have occupied, so the queue_section item's overall
      // height stays stable when the user types a no-match filter.
      // The earlier fix only used `fillParentMaxHeight()` (passed in
      // as `noMatchFillModifier`), which forced the placeholder to
      // exactly one viewport tall — fine for shallow scroll, broken
      // when the user is scrolled deep into a long queue (N*rowHeight
      // far exceeds the viewport). With `heightIn(min = N*rowHeight)`
      // the placeholder reserves the full unfiltered list height, so
      // the LazyColumn's content height stays stable and scroll
      // position is preserved at any depth. We *don't* compose with
      // `fillParentMaxHeight()` here — that modifier forces an exact
      // size and would clobber the heightIn's larger min constraint.
      // The `noMatchFillModifier` is intentionally ignored in this
      // branch (kept on the API surface for legacy callers / future
      // alternate strategies).
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = (allEntries.size * QUEUE_ROW_HEIGHT_DP).dp)
          .padding(24.dp)
          .semantics { testTag = "queue_no_match_placeholder" },
        // Anchor the message at the top of the reserved space — the
        // box is N×rowHeight tall (potentially several screens) so a
        // centered message would land off-screen for any non-trivial
        // queue size, leaving the user staring at blank space.
        contentAlignment = Alignment.TopCenter,
      ) {
        Text("No tracks match your filter", style = MaterialTheme.typography.bodyMedium)
      }
    } else if (filterActive) {
      // D.26.2 (filtered): drag is disabled because the visible subset
      // doesn't reflect the underlying controller order. Render rows
      // directly in a Column with their pre-computed real indices.
      Column(modifier = Modifier.fillMaxWidth()) {
        visibleEntries.forEach { e ->
          QueueRow(
            item = e.item,
            isActive = e.isActive,
            dragHandleModifier = Modifier,
            dragHandleEnabled = false,
            onJumpTo = { onJumpTo(e.realIndex) },
            onRemove = { onRemove(e.realIndex) },
          )
          HorizontalDivider()
        }
      }
    } else {
      // D.26.2 (unfiltered): drag-reorder the full queue. The active
      // row participates in the visual list (it stays in place) but
      // its drag handle is dimmed + disabled. The `onMove` clamp in
      // [translateDragMove] additionally guards against the diff
      // helper producing a (from, to) pair that touches the active
      // index — even though the disabled handle should make that
      // unreachable in normal use.
      DragReorderColumn(
        items = allEntries,
        itemKey = { "queue_${it.realIndex}_${it.item.mediaId}" },
        rowHeightDp = QUEUE_ROW_HEIGHT_DP,
        testTagPrefix = "queue",
        onReordered = { reordered ->
          val before = allEntries
          val diff = firstDifference(before, reordered) ?: return@DragReorderColumn
          val (fromVisual, toVisual) = diff
          val clamped = clampMoveAwayFromActive(currentIndex, fromVisual, toVisual)
            ?: return@DragReorderColumn
          val (from, to) = clamped
          onMove(from, to)
        },
      ) { entry, handleModifier ->
        QueueRow(
          item = entry.item,
          isActive = entry.isActive,
          dragHandleModifier = handleModifier,
          dragHandleEnabled = !entry.isActive,
          onJumpTo = { onJumpTo(entry.realIndex) },
          onRemove = { onRemove(entry.realIndex) },
        )
      }
    }
  }
}

@Composable
private fun QueueRow(
  item: QueueItem,
  isActive: Boolean,
  dragHandleModifier: Modifier,
  dragHandleEnabled: Boolean,
  onJumpTo: () -> Unit,
  onRemove: () -> Unit,
) {
  val rowBackground = if (isActive) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surface
  }
  val rowTag = if (isActive) "queue_row_active" else "queue_row"
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onJumpTo)
      .background(rowBackground)
      .padding(horizontal = 4.dp, vertical = 8.dp)
      .semantics { testTag = rowTag },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (isActive) {
      Icon(
        imageVector = Icons.Filled.GraphicEq,
        contentDescription = "Now playing",
        modifier = Modifier
          .size(20.dp)
          .padding(end = 4.dp)
          .semantics { testTag = "queue_active_indicator" },
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.title.ifEmpty { "Unknown" },
        style = if (isActive) {
          MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        } else {
          MaterialTheme.typography.bodyMedium
        },
        maxLines = 1,
      )
      Text(
        text = item.artist.ifEmpty { "Unknown" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
    IconButton(
      onClick = onRemove,
      modifier = Modifier.semantics { testTag = "queue_remove" },
    ) { Icon(Icons.Filled.Close, contentDescription = "Remove from queue") }
    Box(
      modifier = Modifier
        .size(40.dp)
        .alpha(if (dragHandleEnabled) 1f else 0.3f)
        .then(if (dragHandleEnabled) dragHandleModifier else Modifier)
        .semantics {
          testTag = if (dragHandleEnabled) "queue_drag_handle" else "queue_drag_handle_disabled"
        },
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.DragHandle, contentDescription = "Reorder")
    }
  }
}

/**
 * D.26.2 — translate a visual queue position into a controller index.
 * Now that the queue list renders every item 1:1, the mapping is the
 * identity (when [currentIndex] >= 0). When the queue has no active
 * item we still bottom out at `visual` so the math stays defined.
 */
internal fun translateVisualToReal(currentIndex: Int, visual: Int): Int = visual

/**
 * D.26.2 — clamp a drag-reorder pair so neither endpoint touches the
 * currently-playing index. Returns null if the move is purely
 * within / against the active row (drop the move silently — the
 * disabled drag handle should make this unreachable in normal use).
 *
 * Rules:
 *  - if [from] == [currentIndex] the active row was somehow dragged →
 *    drop the move;
 *  - if [to] == [currentIndex] we shift the destination by one in
 *    whichever direction preserves the user's intent (move past the
 *    active row rather than displacing it);
 *  - if from == to after clamping, drop the move.
 */
internal fun clampMoveAwayFromActive(
  currentIndex: Int,
  from: Int,
  to: Int,
): Pair<Int, Int>? {
  if (currentIndex < 0) return from to to
  if (from == currentIndex) return null
  val clampedTo = if (to == currentIndex) {
    if (from < currentIndex) currentIndex - 1 else currentIndex + 1
  } else to
  if (from == clampedTo) return null
  return from to clampedTo
}

/**
 * D.21.3 helper — exposed at file scope for unit tests.
 *
 * The drag helper produces lists that differ by exactly one
 * `removeAt(from)` + `add(to, moved)` — i.e. all items except one
 * shift in lockstep, and one item jumps to a new index. Returns the
 * `(from, to)` *visual* positions, which callers translate into
 * controller-queue indices via [translateVisualToReal].
 */
internal fun <T> firstDifference(before: List<T>, after: List<T>): Pair<Int, Int>? {
  if (before.size != after.size) return null
  val firstDiff = before.indices.firstOrNull { before[it] != after[it] } ?: return null

  // Hypothesis A: `before[firstDiff]` was moved down.
  val movedDown = before[firstDiff]
  val downTo = after.indexOf(movedDown)
  if (downTo > firstDiff) {
    val rebuilt = before.toMutableList().apply {
      removeAt(firstDiff)
      add(downTo, movedDown)
    }
    if (rebuilt == after) return firstDiff to downTo
  }

  // Hypothesis B: `after[firstDiff]` was moved up (came from a later
  // position in `before`).
  val movedUp = after[firstDiff]
  val upFrom = before.indexOf(movedUp)
  if (upFrom > firstDiff) {
    val rebuilt = before.toMutableList().apply {
      removeAt(upFrom)
      add(firstDiff, movedUp)
    }
    if (rebuilt == after) return upFrom to firstDiff
  }

  return null
}

private const val QUEUE_ROW_HEIGHT_DP = 56
