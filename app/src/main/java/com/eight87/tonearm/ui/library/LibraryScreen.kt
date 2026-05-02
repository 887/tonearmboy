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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.eight87.tonearm.ui.nav.LocalSectionTitle
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
import com.eight87.tonearm.ui.settings.LibraryTab
import com.eight87.tonearm.ui.settings.SettingsRepository
import com.eight87.tonearm.ui.settings.SortDirection
import com.eight87.tonearm.ui.settings.SortKey
import com.eight87.tonearm.ui.settings.TabSort
import kotlinx.coroutines.launch

/**
 * The library is the **root** destination. It holds the top app bar
 * ("tonearm" title + search / sort / overflow icons), the
 * `PrimaryTabRow` for {Songs, Albums, Artists, Genres, Playlists}, and
 * the per-tab content. Per-tab sort state is read from
 * [SettingsRepository] and persisted on confirm.
 *
 * Tab visibility / order comes from [SettingsRepository.snapshot]; tabs
 * marked hidden in Personalize are filtered out at the strip layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  repository: LibraryRepository,
  settingsRepository: SettingsRepository,
  onTrackClick: (List<Track>, Int) -> Unit,
  onPlaylistClick: (Long) -> Unit,
  onOpenSearch: () -> Unit,
  onOpenSettings: () -> Unit,
  onRefreshMusic: () -> Unit,
  onRescanMusic: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val snapshot by settingsRepository.snapshot.collectAsState(
    initial = com.eight87.tonearm.ui.settings.SettingsSnapshot.Default,
  )
  // Visible tabs: anything in libraryTabs is "shown" (hidden tabs
  // already filtered by SettingsRepository's parser when wired to the
  // Personalize sub-page). Default order = canonical.
  val visibleTabs = remember(snapshot.libraryTabs) {
    snapshot.libraryTabs.ifEmpty { LibraryTab.DefaultOrder }
  }
  var selectedIndex by rememberSaveable { mutableStateOf(0) }
  if (selectedIndex >= visibleTabs.size) selectedIndex = 0
  val activeTab = visibleTabs[selectedIndex]

  val activeSort by settingsRepository.tabSort(activeTab).collectAsState(initial = TabSort.Default)
  val scope = rememberCoroutineScope()

  var showSortSheet by remember { mutableStateOf(false) }
  var showOverflow by remember { mutableStateOf(false) }

  // Drive the dynamic top-bar title for the active tab.
  val sectionTitle = LocalSectionTitle.current
  LaunchedEffect(activeTab) {
    sectionTitle.value = "Library ${tabLabel(activeTab)}"
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = sectionTitle.value,
            modifier = Modifier.semantics { testTag = "library_title" },
          )
        },
        actions = {
          IconButton(onClick = onOpenSearch, modifier = Modifier.semantics { testTag = "topbar_search" }) {
            Icon(Icons.Filled.Search, contentDescription = "Search")
          }
          IconButton(
            onClick = { showSortSheet = true },
            modifier = Modifier.semantics { testTag = "topbar_sort" },
          ) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
          Box {
            IconButton(
              onClick = { showOverflow = true },
              modifier = Modifier.semantics { testTag = "topbar_overflow" },
            ) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
            DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
              DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { showOverflow = false; onOpenSettings() },
                modifier = Modifier.semantics { testTag = "menu_settings" },
              )
              DropdownMenuItem(
                text = { Text("Refresh music") },
                onClick = { showOverflow = false; onRefreshMusic() },
                modifier = Modifier.semantics { testTag = "menu_refresh" },
              )
              DropdownMenuItem(
                text = { Text("Rescan music") },
                onClick = { showOverflow = false; onRescanMusic() },
                modifier = Modifier.semantics { testTag = "menu_rescan" },
              )
            }
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    // Vertical-rail layout: rail on the left, content on the right.
    // The rail extends the full content height (Scaffold-padded so we
    // sit under the top app bar and over the mini-player).
    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      LibraryRail(
        tabs = visibleTabs,
        selectedIndex = selectedIndex,
        onSelect = { selectedIndex = it },
        onOpenSettings = onOpenSettings,
      )
      Box(modifier = Modifier.fillMaxSize()) {
        when (activeTab) {
          LibraryTab.Songs -> TracksListScreen(
            repository = repository,
            sort = activeSort,
            intelligentSorting = snapshot.intelligentSorting,
            onTrackClick = onTrackClick,
            onComingSoon = onComingSoon,
          )
          LibraryTab.Albums -> AlbumsGridScreen(
            repository = repository,
            sort = activeSort,
            intelligentSorting = snapshot.intelligentSorting,
            forceSquare = snapshot.forceSquareCovers,
            contentPadding = PaddingValues(8.dp),
          )
          LibraryTab.Artists -> ArtistsListScreen(
            repository = repository,
            settingsRepository = settingsRepository,
            sort = activeSort,
            intelligentSorting = snapshot.intelligentSorting,
          )
          LibraryTab.Genres -> GenresListScreen(
            repository = repository,
            sort = activeSort,
          )
          LibraryTab.Playlists -> PlaylistsListScreen(
            repository = repository,
            onPlaylistClick = onPlaylistClick,
          )
        }
      }
    }
  }

  if (showSortSheet) {
    SortSheet(
      current = activeSort,
      onDismiss = { showSortSheet = false },
      onConfirm = { newSort ->
        scope.launch { settingsRepository.setTabSort(activeTab, newSort) }
        showSortSheet = false
      },
    )
  }
}

private fun tabLabel(tab: LibraryTab): String = when (tab) {
  LibraryTab.Songs -> "Songs"
  LibraryTab.Albums -> "Albums"
  LibraryTab.Artists -> "Artists"
  LibraryTab.Genres -> "Genres"
  LibraryTab.Playlists -> "Playlists"
}

// --- sort helpers ---------------------------------------------------------

internal fun sortNameKey(name: String, intelligentSorting: Boolean): String {
  if (!intelligentSorting) return name.uppercase()
  // Drop a leading article token. Auxio's "intelligent sorting" feature
  // is exactly this: ignore "the", "a", "an" at the start of the
  // displayable name when computing sort order.
  val trimmed = name.trim()
  val lower = trimmed.lowercase()
  for (article in arrayOf("the ", "a ", "an ")) {
    if (lower.startsWith(article)) return trimmed.substring(article.length).uppercase()
  }
  return trimmed.uppercase()
}

private fun <T> applyDirection(items: List<T>, direction: SortDirection, comparator: Comparator<T>): List<T> {
  val sorted = items.sortedWith(comparator)
  return if (direction == SortDirection.Descending) sorted.reversed() else sorted
}

internal fun sortTracks(tracks: List<Track>, sort: TabSort, intelligentSorting: Boolean): List<Track> {
  val comparator: Comparator<Track> = when (sort.key) {
    SortKey.Name -> compareBy { sortNameKey(it.title, intelligentSorting) }
    SortKey.Artist -> compareBy { sortNameKey(it.artist ?: "", intelligentSorting) }
    SortKey.Album -> compareBy { sortNameKey(it.album ?: "", intelligentSorting) }
    SortKey.Date -> compareBy { it.year ?: Int.MIN_VALUE }
    SortKey.DateAdded -> compareBy { it.dateAddedSeconds }
    SortKey.Duration -> compareBy { it.durationMs }
  }
  return applyDirection(tracks, sort.direction, comparator)
}

internal fun sortAlbums(albums: List<Album>, sort: TabSort, intelligentSorting: Boolean): List<Album> {
  val comparator: Comparator<Album> = when (sort.key) {
    SortKey.Name -> compareBy { sortNameKey(it.name, intelligentSorting) }
    SortKey.Artist -> compareBy { sortNameKey(it.artist ?: "", intelligentSorting) }
    SortKey.Album -> compareBy { sortNameKey(it.name, intelligentSorting) }
    SortKey.Date -> compareBy { it.year ?: Int.MIN_VALUE }
    SortKey.DateAdded -> compareBy { it.id }
    SortKey.Duration -> compareBy { -it.trackCount }
  }
  return applyDirection(albums, sort.direction, comparator)
}

internal fun sortArtists(artists: List<Artist>, sort: TabSort, intelligentSorting: Boolean): List<Artist> {
  val comparator: Comparator<Artist> = when (sort.key) {
    SortKey.Artist, SortKey.Name -> compareBy { sortNameKey(it.name, intelligentSorting) }
    SortKey.Album -> compareBy { -it.albumCount }
    SortKey.Duration -> compareBy { -it.trackCount }
    SortKey.Date, SortKey.DateAdded -> compareBy { it.id }
  }
  return applyDirection(artists, sort.direction, comparator)
}

internal fun sortGenres(genres: List<Genre>, sort: TabSort): List<Genre> {
  val comparator: Comparator<Genre> = when (sort.key) {
    SortKey.Duration -> compareBy { -it.trackCount }
    else -> compareBy { it.name.uppercase() }
  }
  return applyDirection(genres, sort.direction, comparator)
}

// --- Albums ---------------------------------------------------------------

@Composable
fun AlbumsGridScreen(
  repository: LibraryRepository,
  sort: TabSort,
  intelligentSorting: Boolean,
  forceSquare: Boolean,
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  val albums by repository.observeAlbums().collectAsState(initial = emptyList())
  if (albums.isEmpty()) {
    EmptyState("No albums yet. Add audio files to your device, then tap Rescan music.")
    return
  }
  val sorted = remember(albums, sort, intelligentSorting) { sortAlbums(albums, sort, intelligentSorting) }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 140.dp),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize().semantics { testTag = "albums_grid" },
  ) {
    items(sorted, key = { it.id }) { album -> AlbumCell(album, forceSquare) }
  }
}

@Composable
private fun AlbumCell(album: Album, forceSquare: Boolean) {
  Column(modifier = Modifier.padding(4.dp)) {
    val shape = if (forceSquare) RoundedCornerShape(0.dp) else RoundedCornerShape(8.dp)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(shape)
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
fun ArtistsListScreen(
  repository: LibraryRepository,
  settingsRepository: SettingsRepository,
  sort: TabSort,
  intelligentSorting: Boolean,
) {
  // D.9a.6 — `observeArtists(hideCollaboratorsFlow)` re-emits when the
  // user flips the toggle without forcing a library rescan.
  val artists by repository
    .observeArtists(settingsRepository.hideCollaborators)
    .collectAsState(initial = emptyList())
  if (artists.isEmpty()) {
    EmptyState("No artists yet.")
    return
  }
  val sorted = remember(artists, sort, intelligentSorting) { sortArtists(artists, sort, intelligentSorting) }
  val grouped = remember(sorted) {
    sorted.groupBy { initialKey(sortNameKey(it.name, intelligentSorting)) }.toSortedMap()
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
  sort: TabSort,
  intelligentSorting: Boolean,
  onTrackClick: (List<Track>, Int) -> Unit,
  onComingSoon: (String) -> Unit,
) {
  val tracks by repository.observeTracks().collectAsState(initial = emptyList())
  if (tracks.isEmpty()) {
    EmptyState("No tracks yet.")
    return
  }
  val sorted = remember(tracks, sort, intelligentSorting) { sortTracks(tracks, sort, intelligentSorting) }
  val grouped = remember(sorted, sort, intelligentSorting) {
    if (sort.key == SortKey.Name) {
      sorted.groupBy { initialKey(sortNameKey(it.title, intelligentSorting)) }
    } else emptyMap()
  }
  val orderedKeys = remember(grouped) { grouped.keys.toList() }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "tracks_list" }) {
    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
      if (orderedKeys.isNotEmpty()) {
        orderedKeys.forEach { key ->
          val group = grouped.getValue(key)
          stickyHeader { SectionHeader(key) }
          items(group, key = { it.id }) { track ->
            val itemIndex = sorted.indexOf(track)
            TrackRow(
              track = track,
              onClick = { onTrackClick(sorted, itemIndex) },
              onAction = { action -> handleTrackAction(track, sorted, itemIndex, action, onTrackClick, onComingSoon) },
            )
          }
        }
      } else {
        items(sorted, key = { it.id }) { track ->
          val itemIndex = sorted.indexOf(track)
          TrackRow(
            track = track,
            onClick = { onTrackClick(sorted, itemIndex) },
            onAction = { action -> handleTrackAction(track, sorted, itemIndex, action, onTrackClick, onComingSoon) },
          )
        }
      }
    }
    if (orderedKeys.isNotEmpty()) {
      AlphabetScroller(
        keys = orderedKeys,
        onLetter = { letter ->
          val flatIndex = computeFlatIndex(orderedKeys, grouped, letter)
          if (flatIndex >= 0) scope.launch { listState.scrollToItem(flatIndex) }
        },
      )
    }
  }
}

private fun handleTrackAction(
  track: Track,
  list: List<Track>,
  index: Int,
  action: TrackRowAction,
  onPlayQueue: (List<Track>, Int) -> Unit,
  onComingSoon: (String) -> Unit,
) {
  when (action) {
    TrackRowAction.Play -> onPlayQueue(list, index)
    TrackRowAction.AddToQueue -> onComingSoon("Add to queue")
    TrackRowAction.AddToPlaylist -> onComingSoon("Add to playlist")
    TrackRowAction.GoToAlbum -> onComingSoon("Go to album")
    TrackRowAction.GoToArtist -> onComingSoon("Go to artist")
    TrackRowAction.Delete -> onComingSoon("Delete file")
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

internal enum class TrackRowAction { Play, AddToQueue, AddToPlaylist, GoToAlbum, GoToArtist, Delete }

@Composable
private fun TrackRow(
  track: Track,
  onClick: () -> Unit,
  onAction: (TrackRowAction) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 10.dp)
      .semantics { testTag = "track_row" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
      Text(
        text = listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, track.album?.takeIf { it.isNotBlank() })
          .joinToString(" · ")
          .ifEmpty { "Unknown" },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
      )
    }
    Box {
      IconButton(
        onClick = { menuOpen = true },
        modifier = Modifier.semantics { testTag = "track_row_overflow" },
      ) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
          text = { Text("Play") },
          onClick = { menuOpen = false; onAction(TrackRowAction.Play) },
        )
        DropdownMenuItem(
          text = { Text("Add to queue") },
          onClick = { menuOpen = false; onAction(TrackRowAction.AddToQueue) },
        )
        DropdownMenuItem(
          text = { Text("Add to playlist…") },
          onClick = { menuOpen = false; onAction(TrackRowAction.AddToPlaylist) },
        )
        DropdownMenuItem(
          text = { Text("Go to album") },
          onClick = { menuOpen = false; onAction(TrackRowAction.GoToAlbum) },
        )
        DropdownMenuItem(
          text = { Text("Go to artist") },
          onClick = { menuOpen = false; onAction(TrackRowAction.GoToArtist) },
        )
        // Phase F: file deletion lives here.
        DropdownMenuItem(
          text = { Text("Delete file…") },
          onClick = { menuOpen = false; onAction(TrackRowAction.Delete) },
          enabled = false,
        )
      }
    }
  }
  HorizontalDivider()
}

// --- Genres ---------------------------------------------------------------

@Composable
fun GenresListScreen(
  repository: LibraryRepository,
  sort: TabSort,
) {
  val genres by repository.observeGenres().collectAsState(initial = emptyList())
  if (genres.isEmpty()) {
    EmptyState("No genres yet.")
    return
  }
  val sorted = remember(genres, sort) { sortGenres(genres, sort) }
  LazyColumn(modifier = Modifier.fillMaxSize().semantics { testTag = "genres_list" }) {
    items(sorted, key = { it.id }) { g -> GenreRow(g) }
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
