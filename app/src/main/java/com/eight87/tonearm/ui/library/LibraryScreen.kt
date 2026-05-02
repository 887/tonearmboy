package com.eight87.tonearm.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
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
import com.eight87.tonearm.ui.settings.catalog.SettingsDimens
import kotlinx.coroutines.launch

// D.16.1 — shared chrome contract for library lists / grids / details.
//
// Every per-row composable (`TrackRow`, `ArtistRow`, …) accepts a
// `containerStyle` so callers can decide whether the row sits inside a
// settings-style card or runs edge-to-edge. The default is the M3
// Expressive grouped card; the EdgeToEdge variant is preserved for
// places where the card chrome would fight the surrounding surface
// (e.g. the album-detail tracks list lives inside its own card already).
//
// See the M3 Expressive note in CLAUDE.md ("M3 Expressive layout") for
// the larger discussion of insets and the boundary between the rail
// and the content area.
enum class ContainerStyle { SettingsCard, EdgeToEdge }

/**
 * D.16.1 — modifier that gives a list/grid the M3 Expressive grouped-
 * card look: rounded corners, the same `surfaceContainer` background
 * that [com.eight87.tonearm.ui.settings.catalog.SettingsCard] uses, and
 * the canonical 16 dp horizontal page padding that makes the chrome
 * "sit in the middle" rather than running edge-to-edge.
 *
 * We ship this as a Modifier (rather than a wrapping Card composable)
 * because library content is a `LazyColumn` / `LazyVerticalGrid`
 * — placing those inside a `Card { Column { … } }` would force them
 * into bounded height, breaking scroll. Same visual, lighter chrome.
 */
@Composable
internal fun Modifier.libraryListCard(): Modifier {
  val bg = MaterialTheme.colorScheme.surfaceContainer
  return this
    .padding(horizontal = SettingsDimens.PagePadding)
    .clip(RoundedCornerShape(SettingsDimens.CardCornerRadius))
    .background(bg)
    .semantics { testTag = "library_list_card" }
}

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
  // D.16.3 — top-right wheel goes directly to Settings root.
  onOpenSettings: () -> Unit,
  // D.16.2 — bottom-left rail gear goes to the Library-tabs config
  // (Settings → Personalize). Two distinct entry points to the Settings
  // *system*, keyed to two different intents.
  onOpenLibraryTabsConfig: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
  // D.15: navigation hooks for the new detail screens + track-row overflow.
  onOpenAlbum: (name: String, albumArtist: String?) -> Unit,
  onOpenArtist: (name: String) -> Unit,
  onOpenGenre: (name: String) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  /**
   * D.27.2 — bulk Add-to-playlist from the multi-select contextual bar.
   * Receives the selected track ids; the app-root resolves them against
   * the current library and opens [PlaylistPickerSheet] for the batch.
   */
  onAddTracksToPlaylist: (List<Long>) -> Unit,
  onRenamePlaylist: (Long, String) -> Unit,
  onDeletePlaylist: (Long) -> Unit,
  // D.27.6 — set the playlist's cover URI (or clear it).
  onSetPlaylistCover: (Long, String?) -> Unit,
  // D.27.6 — open the track-picker for the playlist (used from the
  // playlist tile context menu).
  onOpenPlaylistDetail: (Long) -> Unit = {},
  // D.27.5 — current library filter + setter, owned by the app root so
  // the badge / sheet survive across tab changes.
  filter: com.eight87.tonearm.data.FilterCriteria,
  onFilterChange: (com.eight87.tonearm.data.FilterCriteria) -> Unit,
  // Phase F.2 / F.3 — file-deletion entry. Passed list lets the same
  // callback serve the single-track menu item and the multi-select bar.
  onDeleteTracks: (List<Track>) -> Unit,
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
  // D.18.5 — custom tabs are rendered after the built-ins in the rail.
  val customTabs by repository.customTabs().collectAsState(initial = emptyList())
  val totalRailCount = visibleTabs.size + customTabs.size
  var selectedIndex by rememberSaveable { mutableStateOf(0) }
  if (totalRailCount == 0) {
    selectedIndex = 0
  } else if (selectedIndex >= totalRailCount) {
    selectedIndex = 0
  }
  val isCustomSelected = selectedIndex >= visibleTabs.size
  val activeTab = if (!isCustomSelected && visibleTabs.isNotEmpty()) visibleTabs[selectedIndex] else LibraryTab.Songs
  val activeCustomTab = if (isCustomSelected) customTabs.getOrNull(selectedIndex - visibleTabs.size) else null

  val activeSort by settingsRepository.tabSort(activeTab).collectAsState(initial = TabSort.Default)
  val scope = rememberCoroutineScope()

  var showSortSheet by remember { mutableStateOf(false) }
  // D.27.5 — filter sheet visibility. The filter state itself is owned
  // by the app root so it survives tab changes and screen pushes.
  var showFilterSheet by remember { mutableStateOf(false) }

  // Drive the dynamic top-bar title for the active tab.
  val sectionTitle = LocalSectionTitle.current
  LaunchedEffect(activeTab, activeCustomTab) {
    sectionTitle.value = if (activeCustomTab != null) {
      "Library ${activeCustomTab.name}"
    } else {
      "Library ${tabLabel(activeTab)}"
    }
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
          // D.27.5 — Filter icon. The badge ("filter active" dot) is
          // applied via `BadgedBox` when the filter has any non-null
          // field. Tapping opens the filter sheet.
          val filterActive = !filter.isEmpty()
          IconButton(
            onClick = { showFilterSheet = true },
            modifier = Modifier.semantics { testTag = "topbar_filter" },
          ) {
            if (filterActive) {
              androidx.compose.material3.BadgedBox(
                badge = {
                  androidx.compose.material3.Badge(
                    modifier = Modifier.semantics { testTag = "topbar_filter_badge" },
                  )
                },
              ) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filter (active)")
              }
            } else {
              Icon(Icons.Filled.FilterList, contentDescription = "Filter")
            }
          }
          // D.16.3 — direct Settings entry. The kebab dropdown is gone;
          // Refresh / Rescan now live exclusively under Settings → Library
          // (where the catalog already advertises them) so the top-bar
          // is no longer surface-noise for rarely-used actions.
          IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.semantics { testTag = "topbar_settings" },
          ) {
            Icon(
              Icons.Outlined.Settings,
              contentDescription = "Settings",
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    // Vertical-rail layout: rail on the left, content on the right.
    // The rail extends the full content height (Scaffold-padded so we
    // sit under the top app bar and over the mini-player).
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    // Library scan progress — appears at the top while a scan runs,
    // disappears when done. Bound to LibraryRepository.scanProgress.
    ScanProgressBar(repository = repository)
    Row(modifier = Modifier.fillMaxSize()) {
      LibraryRail(
        tabs = visibleTabs,
        selectedIndex = selectedIndex,
        onSelect = { selectedIndex = it },
        // D.16.2 — rail gear is now a *shortcut* to the Library-tabs
        // configuration screen, not the Settings root. The user already
        // identified the gear-on-the-rail as "tab settings", so this
        // matches accepted intent.
        onOpenSettings = onOpenLibraryTabsConfig,
        customTabs = customTabs,
      )
      Box(modifier = Modifier.fillMaxSize()) {
        if (activeCustomTab != null) {
          CustomTabContent(
            customTab = activeCustomTab,
            repository = repository,
            sort = activeSort,
            snapshot = snapshot,
            onTrackClick = onTrackClick,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onOpenGenre = onOpenGenre,
            onComingSoon = onComingSoon,
          )
        } else when (activeTab) {
          LibraryTab.Songs -> TracksListScreen(
            repository = repository,
            sort = activeSort,
            intelligentSorting = snapshot.intelligentSorting,
            filter = filter,
            onTrackClick = onTrackClick,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onAddTracksToPlaylist = onAddTracksToPlaylist,
            onGoToAlbum = { t -> onOpenAlbum(t.album ?: return@TracksListScreen, t.albumArtist ?: t.artist) },
            onGoToArtist = { t -> onOpenArtist((t.albumArtist?.takeIf { it.isNotBlank() } ?: t.artist) ?: return@TracksListScreen) },
            onComingSoon = onComingSoon,
            onDeleteTracks = onDeleteTracks,
          )
          LibraryTab.Albums -> AlbumsGridScreen(
            repository = repository,
            sort = activeSort,
            intelligentSorting = snapshot.intelligentSorting,
            forceSquare = snapshot.forceSquareCovers,
            albumCoversMode = snapshot.albumCoversMode,
            contentPadding = PaddingValues(8.dp),
            onAlbumClick = { a -> onOpenAlbum(a.name, a.artist) },
          )
          LibraryTab.Artists -> ArtistsListScreen(
            repository = repository,
            settingsRepository = settingsRepository,
            sort = activeSort,
            intelligentSorting = snapshot.intelligentSorting,
            onArtistClick = { a -> onOpenArtist(a.name) },
          )
          LibraryTab.Genres -> GenresListScreen(
            repository = repository,
            sort = activeSort,
            onGenreClick = { g -> onOpenGenre(g.name) },
          )
          LibraryTab.Playlists -> PlaylistsListScreen(
            repository = repository,
            onPlaylistClick = onPlaylistClick,
            onRenamePlaylist = onRenamePlaylist,
            onDeletePlaylist = onDeletePlaylist,
            onSetPlaylistCover = onSetPlaylistCover,
          )
        }
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

  if (showFilterSheet) {
    val tracksForRange by repository.observeTracks().collectAsState(initial = emptyList())
    LibraryFilterSheet(
      current = filter,
      tracks = tracksForRange,
      onDismiss = { showFilterSheet = false },
      onApply = {
        onFilterChange(it)
        showFilterSheet = false
      },
      onReset = {
        onFilterChange(com.eight87.tonearm.data.FilterCriteria())
        showFilterSheet = false
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
  // D.9c.2 — drop a leading article in any of the supported languages.
  // The article list lives in `data/sort/IntelligentSort.kt`; see the
  // multi-language test (`IntelligentSortMultiLanguageTest`).
  return com.eight87.tonearm.data.sort.IntelligentSort
    .stripLeadingArticle(name)
    .uppercase()
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
  albumCoversMode: com.eight87.tonearm.ui.settings.AlbumCoversMode,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  onAlbumClick: (Album) -> Unit = {},
) {
  val albums by repository.observeAlbums().collectAsState(initial = emptyList())
  if (albums.isEmpty()) {
    EmptyState("No albums yet. Add audio files to your device, then tap Rescan music.")
    return
  }
  val sorted = remember(albums, sort, intelligentSorting) { sortAlbums(albums, sort, intelligentSorting) }
  // D.16.1 — page-padded grid. Tiles still render with rounded corners,
  // but the *grid section* sits inset from the rail / right edge so the
  // chrome lines up with Settings cards.
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 140.dp),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = SettingsDimens.PagePadding)
      .semantics { testTag = "albums_grid" },
  ) {
    items(sorted, key = { it.id }) { album ->
      AlbumCell(
        album = album,
        forceSquare = forceSquare,
        albumCoversMode = albumCoversMode,
        onClick = { onAlbumClick(album) },
      )
    }
  }
}

@Composable
private fun AlbumCell(
  album: Album,
  forceSquare: Boolean,
  albumCoversMode: com.eight87.tonearm.ui.settings.AlbumCoversMode,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .padding(4.dp)
      .clickable(onClick = onClick)
      .semantics { testTag = "album_cell" },
  ) {
    val shape = if (forceSquare) RoundedCornerShape(0.dp) else RoundedCornerShape(8.dp)
    CoverArt(
      // The MediaStore album id (captured at scan time) is what the
      // legacy `content://media/external/audio/albumart/<id>` provider
      // is keyed by — distinct from `Album.id`, which is the Room
      // rollup primary key.
      albumId = album.mediaStoreAlbumId,
      size = 48.dp,
      mode = albumCoversMode,
      contentDescription = album.name,
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(shape),
    )
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
  onArtistClick: (Artist) -> Unit = {},
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
  // D.16.1 — wrap rows inside the grouped-card chrome. Sticky alphabet
  // headers compose as full-width rows inside the card so they keep the
  // page-padding inset.
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .libraryListCard()
      .semantics { testTag = "artists_list" },
  ) {
    grouped.forEach { (initial, group) ->
      stickyHeader { SectionHeader(initial) }
      items(group, key = { it.id }) { artist -> ArtistRow(artist, onClick = { onArtistClick(artist) }) }
    }
  }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
  TwoLineRow(
    primary = artist.name,
    secondary = "${artist.albumCount} albums · ${artist.trackCount} tracks",
    onClick = onClick,
  )
}

// --- Tracks ---------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TracksListScreen(
  repository: LibraryRepository,
  sort: TabSort,
  intelligentSorting: Boolean,
  // D.27.5 — when non-empty, the underlying tracks Flow is filtered.
  filter: com.eight87.tonearm.data.FilterCriteria = com.eight87.tonearm.data.FilterCriteria(),
  onTrackClick: (List<Track>, Int) -> Unit,
  onComingSoon: (String) -> Unit,
  onAddToQueue: (Track) -> Unit = {},
  onAddToPlaylist: (Track) -> Unit = {},
  // D.27.2 — multi-select bulk Add to playlist; null disables the icon.
  onAddTracksToPlaylist: ((List<Long>) -> Unit)? = null,
  onGoToAlbum: (Track) -> Unit = {},
  onGoToArtist: (Track) -> Unit = {},
  // Phase F.2 / F.3 — single-track delete + bulk delete from the
  // multi-select bar. When null (e.g. the search screen), Delete
  // routes through `onComingSoon` instead.
  onDeleteTracks: ((List<Track>) -> Unit)? = null,
) {
  val tracks by remember(filter) {
    if (filter.isEmpty()) repository.observeTracks() else repository.tracksMatching(filter)
  }.collectAsState(initial = emptyList())
  TracksListContent(
    tracks = tracks,
    sort = sort,
    intelligentSorting = intelligentSorting,
    onTrackClick = onTrackClick,
    onComingSoon = onComingSoon,
    onAddToQueue = onAddToQueue,
    onAddToPlaylist = onAddToPlaylist,
    onAddTracksToPlaylist = onAddTracksToPlaylist,
    onGoToAlbum = onGoToAlbum,
    onGoToArtist = onGoToArtist,
    onDeleteTracks = onDeleteTracks,
  )
}

/**
 * Phase F.2 / F.3 — pure, repository-free body of [TracksListScreen].
 * Pulled out so Compose UI tests can render against a hand-supplied
 * track list without needing to bypass [LibraryRepository]'s on-first-
 * subscription scanner trigger (which wipes a seeded in-memory DB).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TracksListContent(
  tracks: List<Track>,
  sort: TabSort,
  intelligentSorting: Boolean,
  onTrackClick: (List<Track>, Int) -> Unit,
  onComingSoon: (String) -> Unit,
  onAddToQueue: (Track) -> Unit = {},
  onAddToPlaylist: (Track) -> Unit = {},
  onAddTracksToPlaylist: ((List<Long>) -> Unit)? = null,
  onGoToAlbum: (Track) -> Unit = {},
  onGoToArtist: (Track) -> Unit = {},
  onDeleteTracks: ((List<Track>) -> Unit)? = null,
) {
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

  // Phase F.3 — multi-select state. Long-press enters select mode and
  // toggles the long-pressed track's id; subsequent taps then toggle
  // selection rather than start playback. Tapping the close icon in
  // the contextual bar exits.
  var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
  val inSelectionMode = selectedIds.isNotEmpty()

  Column(modifier = Modifier.fillMaxSize().semantics { testTag = "tracks_screen" }) {
    if (inSelectionMode) {
      MultiSelectBar(
        count = selectedIds.size,
        onClose = { selectedIds = emptySet() },
        onAddToPlaylist = onAddTracksToPlaylist?.let { addMany ->
          {
            // D.27.2 — capture the snapshot ids before clearing the
            // selection so the picker sheet sees the right batch even
            // if the user dismisses without picking and re-enters.
            val ids = selectedIds.toList()
            selectedIds = emptySet()
            addMany(ids)
          }
        },
        onDelete = onDeleteTracks?.let { delete ->
          {
            val selectedTracks = sorted.filter { it.id in selectedIds }
            selectedIds = emptySet()
            delete(selectedTracks)
          }
        },
      )
    }
    Row(modifier = Modifier.fillMaxSize().semantics { testTag = "tracks_list" }) {
      // D.16.1 — tracks card. The alphabet scroller stays *outside* the
      // card so it floats over the right margin, the way Settings'
      // sub-page surfaces float their accent strip.
      LazyColumn(state = listState, modifier = Modifier.weight(1f).libraryListCard()) {
        val rowFor: @Composable (Track) -> Unit = { track ->
          val itemIndex = sorted.indexOf(track)
          val selected = track.id in selectedIds
          TrackRow(
            track = track,
            selected = selected,
            inSelectionMode = inSelectionMode,
            onClick = {
              if (inSelectionMode) {
                selectedIds = selectedIds.toggle(track.id)
              } else {
                onTrackClick(sorted, itemIndex)
              }
            },
            onLongClick = { selectedIds = selectedIds + track.id },
            onAction = { action ->
              if (action == TrackRowAction.Delete && onDeleteTracks != null) {
                onDeleteTracks(listOf(track))
              } else {
                handleTrackAction(track, sorted, itemIndex, action, onTrackClick, onAddToQueue, onAddToPlaylist, onGoToAlbum, onGoToArtist, onComingSoon)
              }
            },
          )
        }
        if (orderedKeys.isNotEmpty()) {
          orderedKeys.forEach { key ->
            val group = grouped.getValue(key)
            stickyHeader { SectionHeader(key) }
            items(group, key = { it.id }) { track -> rowFor(track) }
          }
        } else {
          items(sorted, key = { it.id }) { track -> rowFor(track) }
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
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
  if (value in this) this - value else this + value

private fun handleTrackAction(
  track: Track,
  list: List<Track>,
  index: Int,
  action: TrackRowAction,
  onPlayQueue: (List<Track>, Int) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  onGoToAlbum: (Track) -> Unit,
  onGoToArtist: (Track) -> Unit,
  onComingSoon: (String) -> Unit,
) {
  when (action) {
    TrackRowAction.Play -> onPlayQueue(list, index)
    TrackRowAction.AddToQueue -> onAddToQueue(track)
    TrackRowAction.AddToPlaylist -> onAddToPlaylist(track)
    TrackRowAction.GoToAlbum -> onGoToAlbum(track)
    TrackRowAction.GoToArtist -> onGoToArtist(track)
    // Phase F (file deletion) wires this; the menu item is `enabled = false`
    // in the meantime so the snackbar stays out of the user's way.
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
  track: Track,
  onClick: () -> Unit,
  onAction: (TrackRowAction) -> Unit,
  selected: Boolean = false,
  inSelectionMode: Boolean = false,
  onLongClick: () -> Unit = {},
) {
  var menuOpen by remember { mutableStateOf(false) }
  val rowBackground = if (selected) MaterialTheme.colorScheme.secondaryContainer
  else androidx.compose.ui.graphics.Color.Transparent
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(rowBackground)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .padding(horizontal = 16.dp, vertical = 10.dp)
      .semantics {
        testTag = if (selected) "track_row_selected" else "track_row"
      },
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
    if (!inSelectionMode) Box {
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
          modifier = Modifier.semantics { testTag = "track_context_delete" },
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
  onGenreClick: (Genre) -> Unit = {},
) {
  val genres by repository.observeGenres().collectAsState(initial = emptyList())
  if (genres.isEmpty()) {
    EmptyState("No genres yet.")
    return
  }
  val sorted = remember(genres, sort) { sortGenres(genres, sort) }
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .libraryListCard()
      .semantics { testTag = "genres_list" },
  ) {
    items(sorted, key = { it.id }) { g -> GenreRow(g, onClick = { onGenreClick(g) }) }
  }
}

@Composable
private fun GenreRow(genre: Genre, onClick: () -> Unit) {
  TwoLineRow(primary = genre.name, secondary = "${genre.trackCount} tracks", onClick = onClick)
}

// --- Playlists ------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsListScreen(
  repository: LibraryRepository,
  onPlaylistClick: (Long) -> Unit,
  onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
  onDeletePlaylist: (Long) -> Unit = {},
  // D.27.6 — write the playlist's cover URI through to Room. The chooser
  // sheet lifts up the chosen URI (or null) via this lambda. Default
  // no-op so existing tests / callers don't need to wire it.
  onSetPlaylistCover: (Long, String?) -> Unit = { _, _ -> },
) {
  PlaylistsTilesScreen(
    repository = repository,
    onPlaylistClick = onPlaylistClick,
    onRenamePlaylist = onRenamePlaylist,
    onDeletePlaylist = onDeletePlaylist,
    onSetPlaylistCover = onSetPlaylistCover,
  )
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

/**
 * Phase F.3 — contextual top bar for multi-select on the Songs tab.
 * Renders the selected count, a close-X to exit, an Add-to-playlist icon
 * (D.27.2), and a delete icon whose enabled-state mirrors whether the
 * host wired a delete handler.
 */
@Composable
private fun MultiSelectBar(
  count: Int,
  onClose: () -> Unit,
  onAddToPlaylist: (() -> Unit)?,
  onDelete: (() -> Unit)?,
) {
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
    ) { Icon(Icons.Filled.Close, contentDescription = "Exit selection mode") }
    Text(
      text = "$count selected",
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier
        .weight(1f)
        .padding(start = 4.dp)
        .semantics { testTag = "multi_select_count" },
    )
    val playlistLabel = "Add $count tracks to playlist"
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
    val deleteLabel = "Delete $count tracks"
    IconButton(
      onClick = { onDelete?.invoke() },
      enabled = onDelete != null,
      modifier = Modifier.semantics { testTag = "multi_select_delete" },
    ) { Icon(Icons.Filled.Delete, contentDescription = deleteLabel) }
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
