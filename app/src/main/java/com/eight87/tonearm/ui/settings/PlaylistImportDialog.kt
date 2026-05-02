package com.eight87.tonearm.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.playlist.PlaylistImportCollisionPolicy

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
    title = { Text("Playlist names already exist") },
    text = {
      Column(modifier = Modifier.semantics { testTag = "playlist_import_collision_dialog" }) {
        Text("These playlists already exist in your library:")
        Text(
          collidingNames.joinToString(", "),
          modifier = Modifier.padding(top = 4.dp),
        )
        Text(
          "Overwrite replaces existing tracks. Merge appends new tracks " +
            "to the existing playlists. Cancel skips just the colliding " +
            "playlists; non-colliding ones still import.",
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onPick(PlaylistImportCollisionPolicy.Overwrite) },
        modifier = Modifier.semantics { testTag = "playlist_import_overwrite" },
      ) { Text("Overwrite") }
    },
    dismissButton = {
      Column {
        TextButton(
          onClick = { onPick(PlaylistImportCollisionPolicy.Merge) },
          modifier = Modifier.semantics { testTag = "playlist_import_merge" },
        ) { Text("Merge") }
        TextButton(
          onClick = { onPick(PlaylistImportCollisionPolicy.Cancel) },
          modifier = Modifier.semantics { testTag = "playlist_import_skip" },
        ) { Text("Cancel collisions") }
      }
    },
  )
}
