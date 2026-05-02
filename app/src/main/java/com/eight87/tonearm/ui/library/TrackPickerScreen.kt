package com.eight87.tonearm.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.model.Track
import kotlinx.coroutines.flow.first

/**
 * D.27.3 — multi-select-only track picker used by the playlist detail
 * "Add tracks" flow.
 *
 * Renders all library tracks as a `LazyColumn` of one-line rows with
 * checkboxes. Tracks already in the playlist are pre-checked **and
 * disabled** so the user can't accidentally re-add them, but they're
 * visible so the picker doubles as "remove from playlist by unchecking
 * a pre-checked row" (D.27.3 plan note).
 *
 * On `Confirm`, returns:
 *  - [onConfirm]'d list of newly-checked ids (NOT already in the
 *    playlist) — for the host to add via [LibraryRepository.addTrackToPlaylist]
 *  - [onConfirm]'d list of unchecked ids that WERE in the playlist
 *    — for the host to remove
 *
 * The host (TonearmApp) receives both lists and routes them through
 * the existing add / remove repository APIs in a single launch block.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPickerScreen(
  repository: LibraryRepository,
  playlistId: Long,
  onBack: () -> Unit,
  onConfirm: (toAdd: List<Long>, toRemove: List<Long>) -> Unit,
) {
  val allTracks by repository.observeTracks().collectAsState(initial = emptyList())
  // Initial set of ids already in the playlist. Read once on enter so
  // the user's checkbox edits are stable while they're scrolling.
  var alreadyIn by remember { mutableStateOf<Set<Long>?>(null) }
  androidx.compose.runtime.LaunchedEffect(playlistId) {
    alreadyIn = repository.observePlaylistTracks(playlistId).first().map { it.id }.toSet()
  }
  val initialIds = alreadyIn ?: emptySet()
  var selected by remember(initialIds) { mutableStateOf(initialIds) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          val newCount = (selected - initialIds).size
          val removedCount = (initialIds - selected).size
          val sub = when {
            newCount == 0 && removedCount == 0 -> "Add tracks"
            removedCount == 0 -> "+$newCount"
            newCount == 0 -> "-$removedCount"
            else -> "+$newCount / -$removedCount"
          }
          Text(sub, modifier = Modifier.semantics { testTag = "track_picker_title" })
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = {
              val toAdd = (selected - initialIds).toList()
              val toRemove = (initialIds - selected).toList()
              onConfirm(toAdd, toRemove)
            },
            modifier = Modifier.semantics { testTag = "track_picker_confirm" },
          ) { Icon(Icons.Filled.Check, contentDescription = "Confirm selection") }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .semantics { testTag = "track_picker_list" },
    ) {
      items(allTracks, key = { it.id }) { t ->
        val isChecked = t.id in selected
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              selected = if (isChecked) selected - t.id else selected + t.id
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { testTag = "track_picker_row" },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = isChecked,
            onCheckedChange = {
              selected = if (it) selected + t.id else selected - t.id
            },
          )
          Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
              text = listOfNotNull(t.artist, t.album).joinToString(" · ").ifEmpty { "Unknown" },
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
            )
          }
        }
        HorizontalDivider()
      }
    }
  }
}
