package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import com.eight87.tonearm.ui.common.DragReorderColumn
import com.eight87.tonearm.ui.library.CoverArt
import com.eight87.tonearm.ui.settings.AlbumCoversMode

/**
 * D.15.5 / D.21.2-D.21.5 — modal bottom sheet for the playback queue.
 *
 * Layout (top to bottom):
 *  - pinned active-track header: cover thumb + title + artist + slim
 *    seek slider showing the current playback position
 *  - shuffle + repeat-mode IconToggleButtons
 *  - quick-filter `OutlinedTextField` (substring match on title +
 *    artist, case-insensitive, render-only)
 *  - "Up next" section divider + label
 *  - drag-and-drop `LazyColumn`-equivalent listing of upcoming tracks,
 *    skipping the active one. Reorder is committed via `onMove`. Drag
 *    handles dim and disable while a filter is active so the user
 *    doesn't try to drag a row outside the filtered subset.
 *
 * The active track is never part of the draggable list — it stays
 * pinned at the top so that "now playing" is always visible.
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun QueueSheet(
  snapshot: QueueSnapshot,
  shuffleEnabled: Boolean,
  repeatMode: Int,
  onDismiss: () -> Unit,
  onJumpTo: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onMove: (from: Int, to: Int) -> Unit,
  onSeek: (Long) -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  positionMs: Long,
  durationMs: Long,
  albumCoversMode: AlbumCoversMode = AlbumCoversMode.Balanced,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "queue_sheet" },
  ) {
    QueueSheetContent(
      snapshot = snapshot,
      shuffleEnabled = shuffleEnabled,
      repeatMode = repeatMode,
      onJumpTo = onJumpTo,
      onRemove = onRemove,
      onMove = onMove,
      onSeek = onSeek,
      onToggleShuffle = onToggleShuffle,
      onCycleRepeat = onCycleRepeat,
      positionMs = positionMs,
      durationMs = durationMs,
      albumCoversMode = albumCoversMode,
    )
  }
}

/**
 * Body of the queue sheet, factored out of the [ModalBottomSheet]
 * wrapper so Robolectric tests can render it directly. Driving a
 * `ModalBottomSheet` in a Robolectric test environment is awkward —
 * the sheet renders in a separate window whose layout doesn't always
 * resolve cleanly, which masks click-through to interior controls.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun QueueSheetContent(
  snapshot: QueueSnapshot,
  shuffleEnabled: Boolean,
  repeatMode: Int,
  onJumpTo: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onMove: (from: Int, to: Int) -> Unit,
  onSeek: (Long) -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  positionMs: Long,
  durationMs: Long,
  albumCoversMode: AlbumCoversMode = AlbumCoversMode.Balanced,
) {
    var filter by remember { mutableStateOf("") }
    val filterActive = filter.isNotBlank()

    val items = snapshot.items
    val currentIndex = snapshot.currentIndex
    val activeItem: QueueItem? =
      if (currentIndex in items.indices) items[currentIndex] else null

    // D.21.2: build the "up next" list = everything in the queue minus
    // the active track. Pair each entry with its real queue index so
    // moves / jumps / removes still target the correct controller
    // position even after we've rendered a filtered subset.
    data class UpNext(val realIndex: Int, val item: QueueItem)
    val upNextAll: List<UpNext> = items.mapIndexedNotNull { idx, it ->
      if (idx == currentIndex) null else UpNext(idx, it)
    }
    val needle = filter.trim().lowercase()
    val upNextVisible: List<UpNext> = if (filterActive) {
      upNextAll.filter {
        it.item.title.lowercase().contains(needle) ||
          it.item.artist.lowercase().contains(needle)
      }
    } else upNextAll

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 240.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // -- D.21.2: pinned active-track header ----------------------------
      if (activeItem != null) {
        ActiveTrackHeader(
          item = activeItem,
          positionMs = positionMs,
          durationMs = durationMs,
          albumCoversMode = albumCoversMode,
          onSeek = onSeek,
        )
      } else {
        Text("Queue is empty", style = MaterialTheme.typography.bodyMedium)
      }

      // -- D.21.4: shuffle + repeat-mode toggles -------------------------
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "queue_toggles_row" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ShuffleToggle(
          enabled = shuffleEnabled,
          onToggle = onToggleShuffle,
          testTag = "queue_shuffle_toggle",
        )
        RepeatToggle(
          mode = repeatMode,
          onClick = onCycleRepeat,
          testTag = "queue_repeat_toggle",
        )
      }

      // -- D.21.5: quick filter ------------------------------------------
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

      HorizontalDivider()
      Text(
        text = "Up next",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        // D.21.5: filter is render-only. Drags are disabled because the
        // visible subset doesn't represent the underlying controller
        // ordering. Render rows directly in a Column so every match
        // lays out unconditionally — LazyColumn inside a sheet without
        // a bounded height would only compose the first viewport.
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
        // D.21.3: drag-reorder via the shared `DragReorderColumn`. The
        // helper's `onReordered` returns the new working list — diff
        // against the input to compute the (from, to) move and forward
        // it to the controller.
        DragReorderColumn(
          items = upNextAll,
          itemKey = { "queue_${it.realIndex}_${it.item.mediaId}" },
          rowHeightDp = QUEUE_ROW_HEIGHT_DP,
          testTagPrefix = "queue",
          onReordered = { reordered ->
            // The list contains UpNext entries with stable realIndex
            // values from before the reorder. Find the entry whose
            // visual position changed, and translate that into a
            // controller-level move.
            val before = upNextAll
            val diff = firstDifference(before, reordered) ?: return@DragReorderColumn
            val (fromVisual, toVisual) = diff
            val moved = before[fromVisual]
            // Compute the destination's controller index by inserting
            // the moved entry at `toVisual` in the up-next list and
            // reading off the queue indices either side.
            val destReal = computeDestRealIndex(
              activeIndex = currentIndex,
              toVisual = toVisual,
            )
            onMove(moved.realIndex, destReal)
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

@OptIn(UnstableApi::class)
@Composable
private fun ActiveTrackHeader(
  item: QueueItem,
  positionMs: Long,
  durationMs: Long,
  albumCoversMode: AlbumCoversMode,
  onSeek: (Long) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = "queue_active_header" },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CoverArt(
      albumId = item.mediaStoreAlbumId,
      size = 56.dp,
      mode = albumCoversMode,
      contentDescription = null,
      modifier = Modifier
        .size(56.dp)
        .clip(RoundedCornerShape(6.dp)),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.title.ifEmpty { "Unknown" },
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
      )
      Text(
        text = item.artist.ifEmpty { "Unknown artist" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
  // D.21.2: small seek slider directly under the active-track row,
  // tied to the same MediaController state the NowPlaying scrubber
  // reads. Releases commit via onSeek (drags update local state in
  // the slider until release, identical to the NowPlaying scrubber).
  val total = durationMs.coerceAtLeast(0L).toFloat().coerceAtLeast(1f)
  var dragValue by remember(positionMs) { mutableStateOf<Float?>(null) }
  val sliderValue = (dragValue ?: positionMs.toFloat()).coerceIn(0f, total)
  Slider(
    value = sliderValue,
    onValueChange = { dragValue = it },
    onValueChangeFinished = {
      dragValue?.let { onSeek(it.toLong()) }
      dragValue = null
    },
    valueRange = 0f..total,
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = "queue_active_seek" },
  )
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
    // D.21.3: drag handle. While the filter is active the handle's
    // pointerInput is replaced with a no-op Modifier and the visual
    // is dimmed so the user gets a "this is disabled" cue without
    // needing a tooltip.
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

@Composable
private fun ShuffleToggle(
  enabled: Boolean,
  onToggle: () -> Unit,
  testTag: String,
) {
  // `Modifier.testTag` (vs. `semantics { testTag = ... }`) lands the
  // tag on the IconToggleButton's own semantics node so test
  // `performClick` activates the button's click handler. The pure
  // semantics-block form lands the tag on a separate node above the
  // toggle, which won't receive clicks.
  IconToggleButton(
    checked = enabled,
    onCheckedChange = { onToggle() },
    modifier = Modifier.testTag(testTag),
  ) {
    Icon(
      imageVector = if (enabled) Icons.Filled.ShuffleOn else Icons.Filled.Shuffle,
      contentDescription = if (enabled) "Shuffle on" else "Shuffle off",
    )
  }
}

@Composable
private fun RepeatToggle(
  mode: Int,
  onClick: () -> Unit,
  testTag: String,
) {
  // RepeatToggle is a tri-state cycle, not a binary toggle. We model it
  // as an `IconToggleButton` with `checked = (mode != REPEAT_MODE_OFF)`
  // so the highlight ring lights up while the icon swaps to convey the
  // ALL vs ONE distinction.
  val checked = mode != Player.REPEAT_MODE_OFF
  IconToggleButton(
    checked = checked,
    onCheckedChange = { onClick() },
    modifier = Modifier.testTag(testTag),
  ) {
    val (icon, desc) = when (mode) {
      Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOneOn to "Repeat one"
      Player.REPEAT_MODE_ALL -> Icons.Filled.RepeatOn to "Repeat all"
      else -> Icons.Filled.Repeat to "Repeat off"
    }
    Icon(imageVector = icon, contentDescription = desc)
  }
}

/**
 * D.21.3 helper — exposed at file scope so `QueueDragDropTest` can
 * exercise the index-translation logic without spinning a real
 * `DragReorderColumn`. The visual list (`upNextBefore`) skips the
 * active track, so the visual indices need to be translated back to
 * controller-queue indices for `MediaController.moveMediaItem`.
 *
 * `toVisual` is the destination position in the visual up-next list.
 * The function returns the controller index where `moveMediaItem`
 * should insert the dragged track.
 */
internal fun <T> firstDifference(before: List<T>, after: List<T>): Pair<Int, Int>? {
  if (before.size != after.size) return null
  // The drag helper produces lists that differ by exactly one
  // `removeAt(from)` + `add(to, moved)` — i.e. all items except one
  // shift in lockstep, and one item jumps to a new index.
  //
  // To identify the moved item we test both candidates at the first
  // divergence: `before[from]` (jumped down) and `after[from]`
  // (something else moved up into this slot from below). The hypothesis
  // that produces an exact `before`-with-removeAt-then-insert
  // reconstruction wins.
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

/**
 * D.21.3 helper — translate a visual up-next position into a real
 * controller-queue index. The visual list skips the active track at
 * `activeIndex`, so visual positions ≥ active need to bump by one.
 */
internal fun computeDestRealIndex(
  activeIndex: Int,
  toVisual: Int,
): Int = if (toVisual < activeIndex) toVisual else toVisual + 1

private const val QUEUE_ROW_HEIGHT_DP = 56
