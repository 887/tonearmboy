package com.eight87.tonearm.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.model.Album
import com.eight87.tonearm.data.model.Artist
import com.eight87.tonearm.data.model.Genre
import com.eight87.tonearm.data.model.Track
import kotlinx.coroutines.launch

private val LibraryTabs = listOf("Albums", "Artists", "Tracks", "Genres", "Playlists")

/**
 * Single-screen Library with five tabs.
 *
 * **Design choice (D.2):** Five sibling library views work as a tabbed
 * group rather than five top-level destinations. Reasoning:
 *  - The five views are conceptually one place ("the library"), and
 *    Material 3's bottom-nav guidance reserves top-level entries for
 *    distinct app sections, not facets of one section.
 *  - All five views consume the same Flows from [LibraryRepository];
 *    keeping them under one Scaffold avoids re-subscribing per tab and
 *    makes a future fast-scroll alphabet bar reusable.
 *  - Bottom nav stays at four entries (Home / Library / Search /
 *    Settings) — the canonical Material 3 sweet spot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  repository: LibraryRepository,
  onTrackClick: (List<Track>, Int) -> Unit,
  onPlaylistClick: (Long) -> Unit,
) {
  var selectedTab by rememberSaveable { mutableStateOf(0) }

  Scaffold(
    topBar = {
      Column {
        TopAppBar(title = { Text("Library") })
        PrimaryTabRow(selectedTabIndex = selectedTab) {
          LibraryTabs.forEachIndexed { i, label ->
            Tab(
              selected = selectedTab == i,
              onClick = { selectedTab = i },
              text = { Text(label) },
              modifier = Modifier.semantics { testTag = "library_tab_$label" },
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (selectedTab) {
        0 -> AlbumsGridScreen(repository, contentPadding = PaddingValues(8.dp))
        1 -> ArtistsListScreen(repository)
        2 -> TracksListScreen(repository, onTrackClick = onTrackClick)
        3 -> GenresListScreen(repository)
        4 -> PlaylistsListScreen(repository, onPlaylistClick = onPlaylistClick)
      }
    }
  }
}

// --- Albums ---------------------------------------------------------------

@Composable
fun AlbumsGridScreen(
  repository: LibraryRepository,
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  val albums by repository.observeAlbums().collectAsState(initial = emptyList())
  if (albums.isEmpty()) {
    EmptyState("No albums yet. Add audio files to your device, then pull-to-rescan from Settings.")
    return
  }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 140.dp),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize().semantics { testTag = "albums_grid" },
  ) {
    items(albums, key = { it.id }) { album -> AlbumCell(album) }
  }
}

@Composable
private fun AlbumCell(album: Album) {
  Column(modifier = Modifier.padding(4.dp)) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.MusicNote,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
      )
    }
    Text(
      text = album.name,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      modifier = Modifier.padding(top = 6.dp),
    )
    Text(
      text = album.artist ?: "Unknown artist",
      style = MaterialTheme.typography.bodySmall,
      maxLines = 1,
    )
  }
}

// --- Artists --------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsListScreen(repository: LibraryRepository) {
  val artists by repository.observeArtists().collectAsState(initial = emptyList())
  if (artists.isEmpty()) {
    EmptyState("No artists yet.")
    return
  }
  val grouped = remember(artists) {
    artists.groupBy { initialKey(it.name) }.toSortedMap()
  }
  LazyColumn(modifier = Modifier.fillMaxSize().semantics { testTag = "artists_list" }) {
    grouped.forEach { (initial, group) ->
      stickyHeader { SectionHeader(initial) }
      items(group, key = { it.id }) { artist -> ArtistRow(artist) }
    }
  }
}

@Composable
private fun ArtistRow(artist: Artist) {
  TwoLineRow(
    primary = artist.name,
    secondary = "${artist.albumCount} albums · ${artist.trackCount} tracks",
  )
}

// --- Tracks ---------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TracksListScreen(
  repository: LibraryRepository,
  onTrackClick: (List<Track>, Int) -> Unit,
) {
  val tracks by repository.observeTracks().collectAsState(initial = emptyList())
  if (tracks.isEmpty()) {
    EmptyState("No tracks yet.")
    return
  }
  val sorted = remember(tracks) { tracks.sortedBy { it.title.uppercase() } }
  val grouped = remember(sorted) { sorted.groupBy { initialKey(it.title) } }
  val orderedKeys = remember(grouped) { grouped.keys.toList() }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "tracks_list" }) {
    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f),
    ) {
      orderedKeys.forEach { key ->
        val group = grouped.getValue(key)
        stickyHeader { SectionHeader(key) }
        items(group, key = { it.id }) { track ->
          val itemIndex = sorted.indexOf(track)
          TrackRow(track) { onTrackClick(sorted, itemIndex) }
        }
      }
    }
    AlphabetScroller(
      keys = orderedKeys,
      onLetter = { letter ->
        val flatIndex = computeFlatIndex(orderedKeys, grouped, letter)
        if (flatIndex >= 0) scope.launch { listState.scrollToItem(flatIndex) }
      },
    )
  }
}

private fun computeFlatIndex(
  orderedKeys: List<String>,
  grouped: Map<String, List<Track>>,
  letter: String,
): Int {
  var flat = 0
  for (key in orderedKeys) {
    if (key == letter) return flat
    flat += 1 // header
    flat += grouped.getValue(key).size
  }
  return -1
}

@Composable
private fun AlphabetScroller(
  keys: List<String>,
  onLetter: (String) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxHeight()
      .padding(end = 4.dp)
      .semantics { testTag = "alphabet_scroller" },
    verticalArrangement = Arrangement.SpaceEvenly,
  ) {
    keys.forEach { letter ->
      Text(
        text = letter,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
          .padding(horizontal = 6.dp)
          .clickable { onLetter(letter) },
      )
    }
  }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    Text(
      text = listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, track.album?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
        .ifEmpty { "Unknown" },
      style = MaterialTheme.typography.bodySmall,
      maxLines = 1,
    )
  }
  HorizontalDivider()
}

// --- Genres ---------------------------------------------------------------

@Composable
fun GenresListScreen(repository: LibraryRepository) {
  val genres by repository.observeGenres().collectAsState(initial = emptyList())
  if (genres.isEmpty()) {
    EmptyState("No genres yet.")
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize().semantics { testTag = "genres_list" }) {
    items(genres, key = { it.id }) { g -> GenreRow(g) }
  }
}

@Composable
private fun GenreRow(genre: Genre) {
  TwoLineRow(primary = genre.name, secondary = "${genre.trackCount} tracks")
}

// --- Playlists ------------------------------------------------------------

@Composable
fun PlaylistsListScreen(
  repository: LibraryRepository,
  onPlaylistClick: (Long) -> Unit,
) {
  val playlists by repository.observePlaylists().collectAsState(initial = emptyList())
  var showCreate by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  Box(modifier = Modifier.fillMaxSize().semantics { testTag = "playlists_screen" }) {
    if (playlists.isEmpty()) {
      EmptyState("No playlists yet. Tap + to create one.")
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize().semantics { testTag = "playlists_list" }) {
        items(playlists, key = { it.id }) { p ->
          TwoLineRow(
            primary = p.name,
            secondary = "${p.trackCount} tracks",
            onClick = { onPlaylistClick(p.id) },
          )
        }
      }
    }
    ExtendedFloatingActionButton(
      onClick = { showCreate = true },
      icon = { Icon(Icons.Filled.Add, contentDescription = null) },
      text = { Text("New playlist") },
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    )
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
        TextButton(
          onClick = {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
              scope.launch { repository.createPlaylist(trimmed) }
            }
            showCreate = false
          },
        ) { Text("Create") }
      },
      dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
    )
  }
}

// --- Shared row helpers ---------------------------------------------------

@Composable
private fun TwoLineRow(
  primary: String,
  secondary: String,
  onClick: (() -> Unit)? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .let { if (onClick != null) it.clickable(onClick = onClick) else it }
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Text(primary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    Text(secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
  }
  HorizontalDivider()
}

@Composable
private fun SectionHeader(letter: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .padding(horizontal = 16.dp, vertical = 6.dp),
  ) {
    Text(text = letter, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun EmptyState(message: String) {
  Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.semantics { testTag = "empty_state" },
    )
  }
}

internal fun initialKey(name: String): String {
  val ch = name.firstOrNull()?.uppercaseChar() ?: '#'
  return if (ch.isLetter()) ch.toString() else "#"
}
