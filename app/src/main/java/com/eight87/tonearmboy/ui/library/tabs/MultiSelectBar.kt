package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
 *
 * R5 — when [coverBulk] is non-null an extra 3-dot overflow icon
 * surfaces a per-kind "Cover art" submenu with the bulk variants of
 * the per-row cover actions (Set empty / Reset / Search MusicBrainz).
 * The bulk variants apply to every selected entity in one batch.
 */
@Composable
internal fun MultiSelectBar(
  count: Int,
  onClose: () -> Unit,
  onAddToPlaylist: (() -> Unit)?,
  onDelete: (() -> Unit)?,
  coverBulk: BulkCoverHandlers? = null,
) {
  var menuOpen by remember { mutableStateOf(false) }
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
    if (coverBulk != null) Box {
      IconButton(
        onClick = { menuOpen = true },
        modifier = Modifier.semantics { testTag = "multi_select_overflow" },
      ) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_cd_more_options)) }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        if (coverBulk.onBulkSearchOnline != null) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.library_bulk_cover_action_search)) },
            onClick = { menuOpen = false; coverBulk.onBulkSearchOnline.invoke() },
          )
        }
        DropdownMenuItem(
          text = { Text(stringResource(R.string.library_bulk_cover_action_clear)) },
          onClick = { menuOpen = false; coverBulk.onBulkSetEmpty() },
        )
        DropdownMenuItem(
          text = { Text(stringResource(R.string.library_bulk_cover_action_reset)) },
          onClick = { menuOpen = false; coverBulk.onBulkReset() },
        )
      }
    }
  }
}

/**
 * R5 — bulk variants of the per-row cover actions, applied to every
 * currently-selected entity. Replace / Pick is intentionally absent:
 * picking a single SAF URI and applying it to N tracks would either
 * overwrite distinct artwork with one image or require a per-row
 * picker, neither of which is what the user wants.
 */
internal data class BulkCoverHandlers(
  val onBulkSetEmpty: () -> Unit,
  val onBulkReset: () -> Unit,
  val onBulkSearchOnline: (() -> Unit)? = null,
)
