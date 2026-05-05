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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R

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
    ) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_cd_multiselect_close)) }
    Text(
      text = pluralStringResource(R.plurals.library_multiselect_count, count, count),
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier
        .weight(1f)
        .padding(start = 4.dp)
        .semantics { testTag = "multi_select_count" },
    )
    val playlistLabel = pluralStringResource(R.plurals.library_multiselect_add_to_playlist_cd, count, count)
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
    val deleteLabel = pluralStringResource(R.plurals.library_multiselect_delete_cd, count, count)
    IconButton(
      onClick = { onDelete?.invoke() },
      enabled = onDelete != null,
      modifier = Modifier.semantics { testTag = "multi_select_delete" },
    ) { Icon(Icons.Filled.Delete, contentDescription = deleteLabel) }
  }
}
