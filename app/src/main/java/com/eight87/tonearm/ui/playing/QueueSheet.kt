package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.playback.QueueSnapshot

/**
 * D.15.5 — modal bottom sheet listing the current MediaController queue.
 *
 * Drag-to-reorder is implemented as up/down arrow buttons rather than
 * pulling in `androidx.compose.foundation.lazy.staggeredgrid.draggable`
 * or a third-party reorder lib. The arrows mutate the controller queue
 * via the same `moveQueueItem(from, to)` call a drag handler would,
 * which keeps the public surface and the behaviour identical and
 * dodges the new dependency. The arrow-button approach matches what
 * Android Auto's queue sheet uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
  snapshot: QueueSnapshot,
  onDismiss: () -> Unit,
  onJumpTo: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onMove: (from: Int, to: Int) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "queue_sheet" },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 240.dp)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Up next",
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = "${snapshot.items.size} tracks",
        style = MaterialTheme.typography.bodySmall,
      )
      HorizontalDivider()
      if (snapshot.items.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          Text("Queue is empty", style = MaterialTheme.typography.bodyMedium)
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          itemsIndexed(
            items = snapshot.items,
            key = { index, item -> "${index}_${item.mediaId}" },
          ) { index, item ->
            QueueRow(
              index = index,
              title = item.title,
              artist = item.artist,
              isCurrent = index == snapshot.currentIndex,
              isFirst = index == 0,
              isLast = index == snapshot.items.lastIndex,
              onJumpTo = { onJumpTo(index) },
              onRemove = { onRemove(index) },
              onMoveUp = { onMove(index, index - 1) },
              onMoveDown = { onMove(index, index + 1) },
            )
            HorizontalDivider()
          }
        }
      }
    }
  }
}

@Composable
private fun QueueRow(
  index: Int,
  title: String,
  artist: String,
  isCurrent: Boolean,
  isFirst: Boolean,
  isLast: Boolean,
  onJumpTo: () -> Unit,
  onRemove: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onJumpTo)
      .background(
        if (isCurrent) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
      )
      .padding(horizontal = 4.dp, vertical = 8.dp)
      .semantics { testTag = "queue_row" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (isCurrent) {
      Icon(
        imageVector = Icons.Filled.PlayArrow,
        contentDescription = "Now playing",
        modifier = Modifier.padding(end = 8.dp),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title.ifEmpty { "Track $index" },
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
      )
      Text(
        text = artist.ifEmpty { "Unknown" },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
      )
    }
    IconButton(
      onClick = onMoveUp,
      enabled = !isFirst,
      modifier = Modifier.semantics { testTag = "queue_move_up" },
    ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
    IconButton(
      onClick = onMoveDown,
      enabled = !isLast,
      modifier = Modifier.semantics { testTag = "queue_move_down" },
    ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
    IconButton(
      onClick = onRemove,
      modifier = Modifier.semantics { testTag = "queue_remove" },
    ) { Icon(Icons.Filled.Close, contentDescription = "Remove from queue") }
  }
}
