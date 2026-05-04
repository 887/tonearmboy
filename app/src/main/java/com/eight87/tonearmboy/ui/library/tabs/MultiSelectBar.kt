package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

/**
 * Phase F.3 — contextual top bar for multi-select on the Songs tab.
 * Selected count, close-X, Add-to-playlist (D.27.2), Delete.
 */
@Composable
internal fun MultiSelectBar(
  count: Int,
  onClose: () -> Unit,
  onAddToPlaylist: (() -> Unit)?,
  onDelete: (() -> Unit)?,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.secondaryContainer)
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .semantics { testTag = "multi_select_bar" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      onClick = onClose,
      modifier = Modifier.semantics { testTag = "multi_select_close" },
    ) { Icon(Icons.Filled.Close, contentDescription = "Exit selection mode") }
    Text(
      text = "$count selected",
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier
        .weight(1f)
        .padding(start = 4.dp)
        .semantics { testTag = "multi_select_count" },
    )
    val playlistLabel = "Add $count tracks to playlist"
    IconButton(
      onClick = { onAddToPlaylist?.invoke() },
      enabled = onAddToPlaylist != null,
      modifier = Modifier.semantics { testTag = "multi_select_add_to_playlist" },
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
        contentDescription = playlistLabel,
      )
    }
    val deleteLabel = "Delete $count tracks"
    IconButton(
      onClick = { onDelete?.invoke() },
      enabled = onDelete != null,
      modifier = Modifier.semantics { testTag = "multi_select_delete" },
    ) { Icon(Icons.Filled.Delete, contentDescription = deleteLabel) }
  }
}
