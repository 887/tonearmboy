package com.eight87.tonearm.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
  repository: LibraryRepository,
  playlistId: Long,
  onTrackClick: (List<Track>, Int) -> Unit,
  onBack: () -> Unit,
) {
  val tracks by repository.observePlaylistTracks(playlistId).collectAsState(initial = emptyList())
  val playlists by repository.observePlaylists().collectAsState(initial = emptyList())
  val playlist = playlists.firstOrNull { it.id == playlistId }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(playlist?.name ?: "Playlist") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    if (tracks.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp), contentAlignment = Alignment.Center) {
        Text("No tracks in this playlist yet.", style = MaterialTheme.typography.bodyMedium)
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        items(tracks, key = { it.id }) { track ->
          val itemIndex = tracks.indexOf(track)
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onTrackClick(tracks, itemIndex) }
              .padding(horizontal = 16.dp, vertical = 12.dp),
          ) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
              text = listOfNotNull(track.artist, track.album).joinToString(" · ").ifEmpty { "Unknown" },
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
            )
          }
          HorizontalDivider()
        }
      }
    }
  }
}
