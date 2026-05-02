package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import com.eight87.tonearm.ui.common.DragReorderColumn

/**
 * D.24.3 / D.24.4 — queue rendered inline as a section of
 * [NowPlayingScreen]'s scrolling surface (no modal sheet, no duplicate
 * shuffle / repeat / active-track header). The now-playing card at the
 * top of NowPlaying *is* the active-track header now; this section
 * starts directly with the "Up next" label, the filter field, and the
 * upcoming-track list.
 *
 * The "up next" list = everything in the queue strictly after
 * `currentMediaItemIndex`. We deliberately don't render past tracks —
 * scrubbing back to a previous queue position is a `seekToPrevious()`
 * affordance, not a list-item.
 *
 * [QueueSection] renders the items into a parent `LazyColumn` via
 * `items { ... }`-style append; we expose it as a plain `@Composable`
 * that draws into a `Column` slot of [NowPlayingScreen]. The drag
 * handles forward to the shared [DragReorderColumn], and the visual-
 * to-controller index translation lives in [translateVisualToReal].
 *
 * Operations and their index translations:
 *  - tap a row     → `seekToQueueIndex(visual + currentIdx + 1)`
 *  - X-remove      → `removeMediaItem(visual + currentIdx + 1)`
 *  - drag-reorder  → `moveMediaItem(fromVisual + currentIdx + 1,
 *                                   toVisual   + currentIdx + 1)`
 *  - filter        → render-only; while non-empty drag handles dim to
 *                    alpha 0.3 and `enabled=false` so the user can't
 *                    drag inside a filtered subset
 */
@OptIn(UnstableApi::class)
@Composable
fun QueueSection(
  snapshot: QueueSnapshot,
  onJumpTo: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onMove: (from: Int, to: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var filter by remember { mutableStateOf("") }
  val filterActive = filter.isNotBlank()

  val items = snapshot.items
  val currentIndex = snapshot.currentIndex

  // D.24.5: "up next" is strictly the slice *after* currentIndex. The
  // visual-to-controller translation is `visual + currentIndex + 1`.
  data class UpNext(val realIndex: Int, val item: QueueItem)
  val upNextAll: List<UpNext> = if (currentIndex >= 0) {
    items.drop(currentIndex + 1).mapIndexed { offset, it ->
      UpNext(realIndex = currentIndex + 1 + offset, item = it)
    }
  } else emptyList()

  val needle = filter.trim().lowercase()
  val upNextVisible: List<UpNext> = if (filterActive) {
    upNextAll.filter {
      it.item.title.lowercase().contains(needle) ||
        it.item.artist.lowercase().contains(needle)
    }
  } else upNextAll

  Column(
    modifier = modifier
      .fillMaxWidth()
      .semantics { testTag = "queue_section" },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    HorizontalDivider()
    Text(
      text = "Up next",
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

    if (upNextAll.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text("Nothing queued after this track", style = MaterialTheme.typography.bodyMedium)
      }
    } else if (upNextVisible.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text("No tracks match your filter", style = MaterialTheme.typography.bodyMedium)
      }
    } else if (filterActive) {
      // D.24.5: filter is render-only. Drags are disabled because the
      // visible subset doesn't represent the underlying controller
      // ordering. Render rows directly in a Column so every match
      // lays out unconditionally.
      Column(modifier = Modifier.fillMaxWidth()) {
        upNextVisible.forEach { e ->
          QueueRow(
            item = e.item,
            dragHandleModifier = Modifier,
            dragHandleEnabled = false,
            onJumpTo = { onJumpTo(e.realIndex) },
            onRemove = { onRemove(e.realIndex) },
          )
          HorizontalDivider()
        }
      }
    } else {
      // D.24.5: drag-reorder via the shared `DragReorderColumn`. The
      // helper's `onReordered` returns the new working list — diff
      // against the input to compute the (from, to) move and forward
      // it to the controller. The visual list starts at
      // `currentIndex + 1`, so visual indices need to be bumped by
      // `currentIndex + 1` to reach controller-queue indices.
      DragReorderColumn(
        items = upNextAll,
        itemKey = { "queue_${it.realIndex}_${it.item.mediaId}" },
        rowHeightDp = QUEUE_ROW_HEIGHT_DP,
        testTagPrefix = "queue",
        onReordered = { reordered ->
          val before = upNextAll
          val diff = firstDifference(before, reordered) ?: return@DragReorderColumn
          val (fromVisual, toVisual) = diff
          val fromReal = translateVisualToReal(currentIndex, fromVisual)
          val toReal = translateVisualToReal(currentIndex, toVisual)
          onMove(fromReal, toReal)
        },
      ) { entry, handleModifier ->
        QueueRow(
          item = entry.item,
          dragHandleModifier = handleModifier,
          dragHandleEnabled = true,
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
  dragHandleModifier: Modifier,
  dragHandleEnabled: Boolean,
  onJumpTo: () -> Unit,
  onRemove: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onJumpTo)
      .background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 4.dp, vertical = 8.dp)
      .semantics { testTag = "queue_row" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.title.ifEmpty { "Unknown" },
        style = MaterialTheme.typography.bodyLarge,
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
 * D.24.5 helper — translate a visual up-next position into a controller-
 * queue index. The visual list starts at `currentIndex + 1` (the active
 * track is the now-playing card, never in the list), so any visual
 * position `v` maps to `currentIndex + 1 + v`. When the queue has no
 * active track (`currentIndex == -1`, only valid for an empty queue),
 * we bottom out at `0`.
 */
internal fun translateVisualToReal(currentIndex: Int, visual: Int): Int {
  if (currentIndex < 0) return visual
  return currentIndex + 1 + visual
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
