package com.eight87.tonearmboy.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.playlist.PlaylistImportCollisionPolicy

/**
 * Phase H.5 — name-collision dialog shown when imported playlists
 * conflict with existing names. Three actions: Overwrite, Merge, Cancel.
 */
@Composable
fun PlaylistImportCollisionDialog(
  collidingNames: List<String>,
  onPick: (PlaylistImportCollisionPolicy) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.playlist_collision_dialog_title)) },
    text = {
      Column(modifier = Modifier.semantics { testTag = "playlist_import_collision_dialog" }) {
        Text(stringResource(R.string.playlist_collision_dialog_intro))
        Text(
          collidingNames.joinToString(", "),
          modifier = Modifier.padding(top = 4.dp),
        )
        Text(
          stringResource(R.string.playlist_collision_dialog_explanation),
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onPick(PlaylistImportCollisionPolicy.Overwrite) },
        modifier = Modifier.semantics { testTag = "playlist_import_overwrite" },
      ) { Text(stringResource(R.string.playlist_collision_overwrite)) }
    },
    dismissButton = {
      Column {
        TextButton(
          onClick = { onPick(PlaylistImportCollisionPolicy.Merge) },
          modifier = Modifier.semantics { testTag = "playlist_import_merge" },
        ) { Text(stringResource(R.string.playlist_collision_merge)) }
        TextButton(
          onClick = { onPick(PlaylistImportCollisionPolicy.Cancel) },
          modifier = Modifier.semantics { testTag = "playlist_import_skip" },
        ) { Text(stringResource(R.string.playlist_collision_cancel)) }
      }
    },
  )
}
