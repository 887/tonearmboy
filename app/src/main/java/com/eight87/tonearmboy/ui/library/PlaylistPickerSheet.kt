package com.eight87.tonearmboy.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.data.model.Playlist

/**
 * D.15.6.2 — picker sheet for "Add to playlist…".
 *
 * Lists the user's existing playlists and an "Add to new playlist…"
 * affordance at the top. Tapping a row dispatches [onPick]; tapping
 * the new-playlist row opens an inline AlertDialog that creates the
 * playlist via [onCreate] and then dispatches the same [onPick] with
 * its name (the caller resolves the new id from the next observation
 * of the playlists Flow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
  playlists: List<Playlist>,
  onDismiss: () -> Unit,
  onPick: (Playlist) -> Unit,
  onCreateAndPick: (name: String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showCreate by remember { mutableStateOf(false) }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "playlist_picker_sheet" },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 200.dp)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "Add to playlist",
        style = MaterialTheme.typography.titleMedium,
      )
      HorizontalDivider()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { showCreate = true }
          .padding(vertical = 12.dp)
          .semantics { testTag = "playlist_picker_new" },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
        Text("New playlist…", style = MaterialTheme.typography.titleSmall)
      }
      HorizontalDivider()
      if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          Text("No playlists yet", style = MaterialTheme.typography.bodyMedium)
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          items(playlists, key = { it.id }) { p ->
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(p) }
                .padding(vertical = 12.dp)
                .semantics { testTag = "playlist_picker_row" },
            ) {
              Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
              Text("${p.trackCount} tracks", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
          }
        }
      }
    }
  }

  if (showCreate) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreate = false },
      title = { Text("New playlist") },
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
          if (trimmed.isNotEmpty()) {
            onCreateAndPick(trimmed)
          }
          showCreate = false
        }) { Text("Create") }
      },
      dismissButton = {
        TextButton(onClick = { showCreate = false }) { Text("Cancel") }
      },
    )
  }
}
