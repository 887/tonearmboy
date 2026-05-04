package com.eight87.tonearmboy.ui.library

import android.content.Context
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eight87.tonearmboy.data.PlaylistStore
import com.eight87.tonearmboy.data.model.Playlist

/**
 * R.F.19 — sealed dialog-state model + host composable for
 * [PlaylistsTilesScreen]. Replaces the four ad-hoc
 * `mutableStateOf<Playlist?>(...)` slots that previously sprawled
 * across the screen body. (UI-F9.)
 *
 * Tile context-menus stay anchored per-tile; this host only owns the
 * **modal** dialogs (Create / Rename / Delete / CoverChooser).
 */
internal sealed interface PlaylistDialogState {
  data object None : PlaylistDialogState
  data object Create : PlaylistDialogState
  data class Rename(val target: Playlist) : PlaylistDialogState
  data class Delete(val target: Playlist) : PlaylistDialogState
  data class CoverChooser(val target: Playlist) : PlaylistDialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistDialogHost(
  state: PlaylistDialogState,
  repository: PlaylistStore,
  context: Context,
  onDismiss: () -> Unit,
  onCreate: (String) -> Unit,
  onRename: (Long, String) -> Unit,
  onDelete: (Long) -> Unit,
  onSetCover: (Long, String?) -> Unit,
) {
  when (state) {
    PlaylistDialogState.None -> Unit
    PlaylistDialogState.Create -> NewPlaylistSheet(
      onDismiss = onDismiss,
      onCreate = { name ->
        onCreate(name)
        onDismiss()
      },
    )
    is PlaylistDialogState.Rename -> {
      var name by remember(state.target.id) { mutableStateOf(state.target.name) }
      AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
          )
        },
        confirmButton = {
          TextButton(onClick = {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && trimmed != state.target.name) {
              onRename(state.target.id, trimmed)
            }
            onDismiss()
          }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
      )
    }
    is PlaylistDialogState.Delete -> AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Delete playlist") },
      text = { Text("Delete \"${state.target.name}\"? This removes the playlist but not the tracks themselves.") },
      confirmButton = {
        TextButton(onClick = {
          onDelete(state.target.id)
          onDismiss()
        }) { Text("Delete") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    is PlaylistDialogState.CoverChooser -> PlaylistCoverChooserSheet(
      playlist = state.target,
      repository = repository,
      onDismiss = onDismiss,
      onChoose = { uri ->
        // D.27.6 — Persist read access for SAF-picked images so the
        // cover survives reboot.
        if (uri != null && uri.startsWith("content://")) {
          runCatching {
            context.contentResolver.takePersistableUriPermission(
              Uri.parse(uri),
              android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
          }
        }
        onSetCover(state.target.id, uri)
        onDismiss()
      },
    )
  }
}
