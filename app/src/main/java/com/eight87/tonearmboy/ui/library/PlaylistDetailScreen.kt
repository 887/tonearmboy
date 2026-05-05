package com.eight87.tonearmboy.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.PlaylistStore
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.nav.LocalSectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
  repository: PlaylistStore,
  playlistId: Long,
  onTrackClick: (List<Track>, Int) -> Unit,
  onBack: () -> Unit,
  /**
   * D.27.3 — open the multi-select track picker for the playlist. The
   * empty-state primary CTA and the top-bar `+` icon both invoke this.
   */
  onAddTracks: (Long) -> Unit = {},
) {
  val tracks by repository.observePlaylistTracks(playlistId).collectAsState(initial = emptyList())
  val playlists by repository.observePlaylists().collectAsState(initial = emptyList())
  val playlist = playlists.firstOrNull { it.id == playlistId }

  val sectionTitle = LocalSectionTitle.current
  val playlistFallback = stringResource(R.string.library_playlist_detail_default_title)
  val resolvedTitle = playlist?.name ?: playlistFallback
  LaunchedEffect(resolvedTitle) { sectionTitle.value = resolvedTitle }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(playlist?.name ?: playlistFallback) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_cd_back))
          }
        },
        actions = {
          // D.27.3 — `+` on every playlist detail (empty *and* non-empty)
          // so the user can add tracks without long-pressing songs in
          // the library first. Tap → `TrackPicker` destination.
          IconButton(
            onClick = { onAddTracks(playlistId) },
            modifier = Modifier.semantics { testTag = "playlist_detail_add" },
          ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_playlist_detail_add_tracks_cd)) }
        },
      )
    },
  ) { innerPadding ->
    if (tracks.isEmpty()) {
      // D.27.3 — empty-state CTA. Three pieces of chrome the user
      // wanted: a centered illustration / icon, a primary "Add tracks"
      // button, and a secondary text hint about the long-press flow.
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(32.dp)
          .semantics { testTag = "playlist_detail_empty" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.LibraryMusic,
          contentDescription = null,
          modifier = Modifier.size(72.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = stringResource(R.string.library_playlist_detail_empty_title),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 16.dp),
        )
        Button(
          onClick = { onAddTracks(playlistId) },
          modifier = Modifier
            .padding(top = 16.dp)
            .semantics { testTag = "playlist_detail_empty_add" },
        ) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Text(
            stringResource(R.string.library_playlist_detail_empty_button),
            modifier = Modifier.padding(start = 8.dp),
          )
        }
        Text(
          text = stringResource(R.string.library_playlist_detail_empty_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .padding(top = 16.dp)
            .semantics { testTag = "playlist_detail_empty_hint" },
        )
      }
    } else {
      // D.16.1 — playlist tracks inside the M3 Expressive card chrome.
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(vertical = 16.dp)
          .libraryListCard(),
      ) {
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
              text = listOfNotNull(track.artist, track.album).joinToString(" · ").ifEmpty { stringResource(R.string.library_unknown) },
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
