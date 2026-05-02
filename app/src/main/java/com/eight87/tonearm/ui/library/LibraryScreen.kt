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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import com.eight87.tonearm.ui.settings.ViewMode
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
  // D.28.1 — read every tab's persisted view mode in one Flow so the
  // toggle is per-tab and switching tabs reads that tab's saved mode.
  val viewModes by settingsRepository.viewModes.collectAsState(
    initial = LibraryTab.entries.associateWith { ViewMode.defaultFor(it) },
  )
  val activeViewMode = viewModes[activeTab] ?: ViewMode.defaultFor(activeTab)
  val scope = rememberCoroutineScope()

  var showSortSheet by remember { mutableStateOf(false) }
  // D.27.5 — filter sheet visibility. The filter state itself is owned
  // by the app root so it survives tab changes and screen pushes.
  var showFilterSheet by remember { mutableStateOf(false) }

  // Drive the dynamic top-bar title for the active tab.
  val sectionTitle = LocalSectionTitle.current
  LaunchedEffect(activeTab, activeCustomTab) {
    sectionTitle.value = if (activeCustomTab != null) {
      activeCustomTab.name
    } else {
      tabLabel(activeTab)
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
          // D.28.2 — view-mode toggle. Sits between sort and filter so
          // the row reads search → sort → view → filter → settings. The
          // icon shows the *target* mode (tap to switch), not the
          // current one — same pattern as a play/pause button.
          IconButton(
            onClick = {
              val next = activeViewMode.toggle()
              scope.launch { settingsRepository.setViewModeFor(activeTab, next) }
            },
            modifier = Modifier.semantics { testTag = "topbar_view_mode" },
          ) {
            if (activeViewMode == ViewMode.Tile) {
              Icon(
                Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "Switch to list view",
              )
            } else {
              Icon(
                Icons.Filled.GridView,
                contentDescription = "Switch to tile view",
              )
            }
          }
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
            viewMode = activeViewMode,
            albumCoversMode = snapshot.albumCoversMode,
            onTrackClick = onTrackClick,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onAddTracksToPlaylist = onAddTracksToPlaylist,
            onGoToAlbum = { t -> onOpenAlbum(t.album ?: return@TracksListScreen, t.albumArtist ?: t.artist) },
            onGoToArtist = { t -> onOpenArtist((t.albumArtist?.takeIf { it.isNotBlank() } ?: t.artist) ?: return@TracksListScreen) },
            onComingSoon = onComingSoon,
            onDeleteTracks = onDeleteTracks,
          )
          LibraryTab.Albums -> AlbumsTabScreen(
            repository = repository,
            sort = activeSort,
            intelligentSorting = snapshot.intelligentSorting,
            forceSquare = snapshot.forceSquareCovers,
            albumCoversMode = snapshot.albumCoversMode,
            viewMode = activeViewMode,
            onAlbumClick = { a -> onOpenAlbum(a.name, a.artist) },
          )
          LibraryTab.Artists -> ArtistsTabScreen(
            repository = repository,
            settingsRepository = settingsRepository,
            sort = activeSort,
            intelligentSorting = snapshot.intelligentSorting,
            viewMode = activeViewMode,
            onArtistClick = { a -> onOpenArtist(a.name) },
          )
          LibraryTab.Genres -> GenresTabScreen(
            repository = repository,
            sort = activeSort,
            viewMode = activeViewMode,
            onGenreClick = { g -> onOpenGenre(g.name) },
          )
          LibraryTab.Playlists -> PlaylistsTabScreen(
            repository = repository,
            viewMode = activeViewMode,
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

/**
 * D.28 — albums tab dispatcher. List mode → sticky-header list with
 * a 48 dp leading thumbnail (D.28.4); Tile mode → existing grid (was
 * the only mode pre-D.28). Both modes mount the alphabet rail when
 * sort is alphabetical (D.28.5).
 */
@Composable
fun AlbumsTabScreen(
  repository: LibraryRepository,
  sort: TabSort,
  intelligentSorting: Boolean,
  forceSquare: Boolean,
  albumCoversMode: com.eight87.tonearm.ui.settings.AlbumCoversMode,
  viewMode: ViewMode,
  onAlbumClick: (Album) -> Unit = {},
) {
  val albums by repository.observeAlbums().collectAsState(initial = emptyList())
  if (albums.isEmpty()) {
    EmptyState("No albums yet. Add audio files to your device, then tap Rescan music.")
    return
  }
  AlbumsTabContent(
    albums = albums,
    sort = sort,
    intelligentSorting = intelligentSorting,
    albumCoversMode = albumCoversMode,
    viewMode = viewMode,
    onAlbumClick = onAlbumClick,
  )
}

/**
 * D.28.4 / D.28.6 — repository-free body of [AlbumsTabScreen] so the
 * `LibraryAlbumsListViewTest` can render against a hand-built album
 * list without spinning the scanner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumsTabContent(
  albums: List<Album>,
  sort: TabSort,
  intelligentSorting: Boolean,
  albumCoversMode: com.eight87.tonearm.ui.settings.AlbumCoversMode,
  viewMode: ViewMode,
  onAlbumClick: (Album) -> Unit = {},
) {
  val sorted = remember(albums, sort, intelligentSorting) { sortAlbums(albums, sort, intelligentSorting) }
  val sectionKeys = remember(sorted, sort, intelligentSorting) {
    if (sort.key == SortKey.Name) sorted.map { initialKey(sortNameKey(it.name, intelligentSorting)) }
    else emptyList()
  }
  val orderedKeys = remember(sectionKeys) { sectionKeys.distinct() }
  val listState = rememberLazyListState()
  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()

  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "albums_tab" }) {
    if (viewMode == ViewMode.Tile) {
      val tileItems = remember(sorted) {
        sorted.map {
          TileItem(
            id = it.id,
            title = it.name,
            subtitle = it.artist ?: "Unknown artist",
            artUri = null,
            albumArtId = it.mediaStoreAlbumId,
          )
        }
      }
      LibraryTileGrid(
        tiles = tileItems,
        sectionKeys = sectionKeys,
        state = gridState,
        albumCoversMode = albumCoversMode,
        onTileClick = { tile ->
          val a = sorted.firstOrNull { it.id == tile.id } ?: return@LibraryTileGrid
          onAlbumClick(a)
        },
        modifier = Modifier.weight(1f).padding(horizontal = SettingsDimens.PagePadding),
      )
    } else {
      val grouped = remember(sorted, sectionKeys) {
        if (sectionKeys.isEmpty()) emptyMap()
        else sorted.zip(sectionKeys).groupBy({ it.second }, { it.first })
      }
      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .libraryListCard()
          .semantics { testTag = "albums_list" },
      ) {
        if (grouped.isNotEmpty()) {
          orderedKeys.forEach { key ->
            stickyHeader { SectionHeader(key) }
            items(grouped.getValue(key), key = { it.id }) { album ->
              AlbumListRow(album, albumCoversMode, onClick = { onAlbumClick(album) })
            }
          }
        } else {
          items(sorted, key = { it.id }) { album ->
            AlbumListRow(album, albumCoversMode, onClick = { onAlbumClick(album) })
          }
        }
      }
    }
    if (orderedKeys.isNotEmpty()) {
      AlphabetScroller(
        keys = orderedKeys,
        onLetter = { letter ->
          if (viewMode == ViewMode.List) {
            val flat = computeFlatIndexFromKeys(orderedKeys, sectionKeys, letter)
            if (flat >= 0) scope.launch { listState.scrollToItem(flat) }
          } else {
            val tileIdx = tileIndexFor(sectionKeys, letter)
            if (tileIdx >= 0) scope.launch { gridState.scrollToItem(tileIdx) }
          }
        },
      )
    }
  }
}

/**
 * D.28.4 — list-mode album row: 48 dp leading thumbnail + name + artist.
 * Mirrors the size-and-shape contract of the Songs list rows so a tab
 * switch into list mode reads as the same chrome.
 */
@Composable
private fun AlbumListRow(
  album: Album,
  albumCoversMode: com.eight87.tonearm.ui.settings.AlbumCoversMode,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .semantics { testTag = "album_list_row" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CoverArt(
      albumId = album.mediaStoreAlbumId,
      size = 48.dp,
      mode = albumCoversMode,
      contentDescription = album.name,
      modifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(6.dp)),
    )
    Column(modifier = Modifier.padding(start = 12.dp)) {
      Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
      Text(
        text = album.artist ?: "Unknown artist",
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
      )
    }
  }
  HorizontalDivider()
}

/**
 * Pre-D.28 entry point retained for the album-detail callers and the
 * existing `AlbumsGridScreenTest`; delegates straight to the dispatcher
 * in [ViewMode.Tile] to preserve behaviour.
 */
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
  AlbumsTabScreen(
    repository = repository,
    sort = sort,
    intelligentSorting = intelligentSorting,
    forceSquare = forceSquare,
    albumCoversMode = albumCoversMode,
    viewMode = ViewMode.Tile,
    onAlbumClick = onAlbumClick,
  )
}

// --- Artists --------------------------------------------------------------

/**
 * D.28 — Artists tab dispatcher. List = sticky-header rows (existing
 * shape); Tile = LibraryTileGrid with a placeholder cover (we don't
 * resolve a representative album per artist on this pass — the tile
 * carries the letter avatar fallback so the grid is still
 * meaningful).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsTabScreen(
  repository: LibraryRepository,
  settingsRepository: SettingsRepository,
  sort: TabSort,
  intelligentSorting: Boolean,
  viewMode: ViewMode,
  onArtistClick: (Artist) -> Unit = {},
) {
  val artists by repository
    .observeArtists(settingsRepository.hideCollaborators)
    .collectAsState(initial = emptyList())
  if (artists.isEmpty()) {
    EmptyState("No artists yet.")
    return
  }
  val sorted = remember(artists, sort, intelligentSorting) { sortArtists(artists, sort, intelligentSorting) }
  val sectionKeys = remember(sorted, sort, intelligentSorting) {
    if (sort.key == SortKey.Name || sort.key == SortKey.Artist)
      sorted.map { initialKey(sortNameKey(it.name, intelligentSorting)) }
    else emptyList()
  }
  val orderedKeys = remember(sectionKeys) { sectionKeys.distinct() }
  val listState = rememberLazyListState()
  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()

  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "artists_tab" }) {
    if (viewMode == ViewMode.Tile) {
      val tileItems = remember(sorted) {
        sorted.map {
          TileItem(
            id = it.id,
            title = it.name,
            subtitle = "${it.albumCount} albums · ${it.trackCount} tracks",
            artUri = null,
            albumArtId = null,
          )
        }
      }
      LibraryTileGrid(
        tiles = tileItems,
        sectionKeys = sectionKeys,
        state = gridState,
        onTileClick = { tile ->
          val a = sorted.firstOrNull { it.id == tile.id } ?: return@LibraryTileGrid
          onArtistClick(a)
        },
        modifier = Modifier.weight(1f).padding(horizontal = SettingsDimens.PagePadding),
      )
    } else {
      val grouped = remember(sorted, sectionKeys) {
        if (sectionKeys.isEmpty()) emptyMap()
        else sorted.zip(sectionKeys).groupBy({ it.second }, { it.first })
      }
      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .libraryListCard()
          .semantics { testTag = "artists_list" },
      ) {
        if (grouped.isNotEmpty()) {
          orderedKeys.forEach { key ->
            stickyHeader { SectionHeader(key) }
            items(grouped.getValue(key), key = { it.id }) { artist ->
              ArtistRow(artist, onClick = { onArtistClick(artist) })
            }
          }
        } else {
          items(sorted, key = { it.id }) { artist ->
            ArtistRow(artist, onClick = { onArtistClick(artist) })
          }
        }
      }
    }
    if (orderedKeys.isNotEmpty()) {
      AlphabetScroller(
        keys = orderedKeys,
        onLetter = { letter ->
          if (viewMode == ViewMode.List) {
            val flat = computeFlatIndexFromKeys(orderedKeys, sectionKeys, letter)
            if (flat >= 0) scope.launch { listState.scrollToItem(flat) }
          } else {
            val tileIdx = tileIndexFor(sectionKeys, letter)
            if (tileIdx >= 0) scope.launch { gridState.scrollToItem(tileIdx) }
          }
        },
      )
    }
  }
}

/** Pre-D.28 wrapper retained so existing callers / tests still compile. */
@Composable
fun ArtistsListScreen(
  repository: LibraryRepository,
  settingsRepository: SettingsRepository,
  sort: TabSort,
  intelligentSorting: Boolean,
  onArtistClick: (Artist) -> Unit = {},
) {
  ArtistsTabScreen(
    repository = repository,
    settingsRepository = settingsRepository,
    sort = sort,
    intelligentSorting = intelligentSorting,
    viewMode = ViewMode.List,
    onArtistClick = onArtistClick,
  )
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
  // D.28.3 — list vs tile dispatch. Defaults to `List` so the search
  // screen and other repository-free callers still see the legacy
  // shape without explicit wiring.
  viewMode: ViewMode = ViewMode.List,
  albumCoversMode: com.eight87.tonearm.ui.settings.AlbumCoversMode =
    com.eight87.tonearm.ui.settings.AlbumCoversMode.Balanced,
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
    viewMode = viewMode,
    albumCoversMode = albumCoversMode,
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
  viewMode: ViewMode = ViewMode.List,
  albumCoversMode: com.eight87.tonearm.ui.settings.AlbumCoversMode =
    com.eight87.tonearm.ui.settings.AlbumCoversMode.Balanced,
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
  val gridState = rememberLazyGridState()
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
      if (viewMode == ViewMode.List) {
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
      } else {
        // D.28.3 — Songs in tile mode: each tile is the track's album art
        // resolved through CoverArt. We still respect the section-key
        // grouping when sorted by Name so the alphabet rail can scroll
        // the grid by letter (D.28.5).
        val tileItems = remember(sorted) {
          sorted.map {
            TileItem(
              id = it.id,
              title = it.title,
              subtitle = it.artist?.takeIf(String::isNotBlank),
              artUri = null,
              albumArtId = it.mediaStoreAlbumId,
            )
          }
        }
        val sectionKeys = remember(sorted, sort, intelligentSorting) {
          if (sort.key == SortKey.Name)
            sorted.map { initialKey(sortNameKey(it.title, intelligentSorting)) }
          else emptyList()
        }
        LibraryTileGrid(
          tiles = tileItems,
          sectionKeys = sectionKeys,
          state = gridState,
          albumCoversMode = albumCoversMode,
          onTileClick = { tile ->
            val idx = sorted.indexOfFirst { it.id == tile.id }
            if (idx >= 0) {
              if (inSelectionMode) selectedIds = selectedIds.toggle(tile.id)
              else onTrackClick(sorted, idx)
            }
          },
          onTileLongClick = { tile -> selectedIds = selectedIds + tile.id },
          modifier = Modifier.weight(1f).padding(horizontal = SettingsDimens.PagePadding),
        )
      }
      if (orderedKeys.isNotEmpty()) {
        AlphabetScroller(
          keys = orderedKeys,
          onLetter = { letter ->
            if (viewMode == ViewMode.List) {
              val flatIndex = computeFlatIndex(orderedKeys, grouped, letter)
              if (flatIndex >= 0) scope.launch { listState.scrollToItem(flatIndex) }
            } else {
              val sectionKeys = sorted.map { initialKey(sortNameKey(it.title, intelligentSorting)) }
              val tileIdx = tileIndexFor(sectionKeys, letter)
              if (tileIdx >= 0) scope.launch { gridState.scrollToItem(tileIdx) }
            }
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

/**
 * D.28.5 — generic flat-index helper for tabs that group by an
 * ordered list of section keys (one entry per item). Walks
 * [orderedKeys] in order, advancing past one header + the count of
 * matching items per section. Returns -1 when the letter is unknown.
 *
 * Visible for unit tests.
 */
internal fun computeFlatIndexFromKeys(
  orderedKeys: List<String>,
  perItemKeys: List<String>,
  letter: String,
): Int {
  var flat = 0
  for (key in orderedKeys) {
    if (key == letter) return flat
    flat += 1 // header
    flat += perItemKeys.count { it == key }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenresTabScreen(
  repository: LibraryRepository,
  sort: TabSort,
  viewMode: ViewMode,
  onGenreClick: (Genre) -> Unit = {},
) {
  val genres by repository.observeGenres().collectAsState(initial = emptyList())
  if (genres.isEmpty()) {
    EmptyState("No genres yet.")
    return
  }
  val sorted = remember(genres, sort) { sortGenres(genres, sort) }
  // Genres always group by initial letter when sorted by Name (which is
  // the default sort key); duration sort drops the rail.
  val sectionKeys = remember(sorted, sort) {
    if (sort.key != SortKey.Duration) sorted.map { initialKey(it.name.uppercase()) }
    else emptyList()
  }
  val orderedKeys = remember(sectionKeys) { sectionKeys.distinct() }
  val listState = rememberLazyListState()
  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()

  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "genres_tab" }) {
    if (viewMode == ViewMode.Tile) {
      val tileItems = remember(sorted) {
        sorted.map {
          TileItem(
            id = it.id,
            title = it.name,
            subtitle = "${it.trackCount} tracks",
            artUri = null,
            albumArtId = null,
          )
        }
      }
      LibraryTileGrid(
        tiles = tileItems,
        sectionKeys = sectionKeys,
        state = gridState,
        onTileClick = { tile ->
          val g = sorted.firstOrNull { it.id == tile.id } ?: return@LibraryTileGrid
          onGenreClick(g)
        },
        modifier = Modifier.weight(1f).padding(horizontal = SettingsDimens.PagePadding),
      )
    } else {
      val grouped = remember(sorted, sectionKeys) {
        if (sectionKeys.isEmpty()) emptyMap()
        else sorted.zip(sectionKeys).groupBy({ it.second }, { it.first })
      }
      LazyColumn(
        state = listState,
        modifier = Modifier
          .weight(1f)
          .libraryListCard()
          .semantics { testTag = "genres_list" },
      ) {
        if (grouped.isNotEmpty()) {
          orderedKeys.forEach { key ->
            stickyHeader { SectionHeader(key) }
            items(grouped.getValue(key), key = { it.id }) { g ->
              GenreRow(g, onClick = { onGenreClick(g) })
            }
          }
        } else {
          items(sorted, key = { it.id }) { g -> GenreRow(g, onClick = { onGenreClick(g) }) }
        }
      }
    }
    if (orderedKeys.isNotEmpty()) {
      AlphabetScroller(
        keys = orderedKeys,
        onLetter = { letter ->
          if (viewMode == ViewMode.List) {
            val flat = computeFlatIndexFromKeys(orderedKeys, sectionKeys, letter)
            if (flat >= 0) scope.launch { listState.scrollToItem(flat) }
          } else {
            val tileIdx = tileIndexFor(sectionKeys, letter)
            if (tileIdx >= 0) scope.launch { gridState.scrollToItem(tileIdx) }
          }
        },
      )
    }
  }
}

/** Pre-D.28 wrapper retained for callers / tests. */
@Composable
fun GenresListScreen(
  repository: LibraryRepository,
  sort: TabSort,
  onGenreClick: (Genre) -> Unit = {},
) {
  GenresTabScreen(
    repository = repository,
    sort = sort,
    viewMode = ViewMode.List,
    onGenreClick = onGenreClick,
  )
}

@Composable
private fun GenreRow(genre: Genre, onClick: () -> Unit) {
  TwoLineRow(primary = genre.name, secondary = "${genre.trackCount} tracks", onClick = onClick)
}

// --- Playlists ------------------------------------------------------------

/**
 * D.28 — Playlists tab dispatcher. Tile mode → the existing
 * [PlaylistsTilesScreen] (D.27.6); List mode → sticky-header letter
 * list with the alphabet rail mounted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsTabScreen(
  repository: LibraryRepository,
  viewMode: ViewMode,
  onPlaylistClick: (Long) -> Unit,
  onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
  onDeletePlaylist: (Long) -> Unit = {},
  onSetPlaylistCover: (Long, String?) -> Unit = { _, _ -> },
) {
  if (viewMode == ViewMode.Tile) {
    PlaylistsTilesScreen(
      repository = repository,
      onPlaylistClick = onPlaylistClick,
      onRenamePlaylist = onRenamePlaylist,
      onDeletePlaylist = onDeletePlaylist,
      onSetPlaylistCover = onSetPlaylistCover,
    )
    return
  }
  val playlists by repository.observePlaylists().collectAsState(initial = emptyList())
  if (playlists.isEmpty()) {
    EmptyState("No playlists yet. Tap + to create one.")
    return
  }
  val sectionKeys = remember(playlists) {
    playlists.map { initialKey(it.name.uppercase()) }
  }
  val orderedKeys = remember(sectionKeys) { sectionKeys.distinct() }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  Row(modifier = Modifier.fillMaxSize().semantics { testTag = "playlists_tab" }) {
    val grouped = remember(playlists, sectionKeys) {
      playlists.zip(sectionKeys).groupBy({ it.second }, { it.first })
    }
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .libraryListCard()
        .semantics { testTag = "playlists_list" },
    ) {
      orderedKeys.forEach { key ->
        stickyHeader { SectionHeader(key) }
        items(grouped.getValue(key), key = { it.id }) { p ->
          TwoLineRow(
            primary = p.name,
            secondary = "${p.trackCount} tracks",
            onClick = { onPlaylistClick(p.id) },
          )
        }
      }
    }
    if (orderedKeys.isNotEmpty()) {
      AlphabetScroller(
        keys = orderedKeys,
        onLetter = { letter ->
          val flat = computeFlatIndexFromKeys(orderedKeys, sectionKeys, letter)
          if (flat >= 0) scope.launch { listState.scrollToItem(flat) }
        },
      )
    }
  }
}

/** Pre-D.28 wrapper retained for the existing `PlaylistsListScreenTest` and callers. */
@Composable
fun PlaylistsListScreen(
  repository: LibraryRepository,
  onPlaylistClick: (Long) -> Unit,
  onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
  onDeletePlaylist: (Long) -> Unit = {},
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
