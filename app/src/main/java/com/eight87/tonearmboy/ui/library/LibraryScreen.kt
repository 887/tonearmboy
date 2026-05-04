package com.eight87.tonearmboy.ui.library

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.eight87.tonearmboy.ui.nav.LocalSectionTitle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.data.AlbumSource
import com.eight87.tonearmboy.data.ArtistSource
import com.eight87.tonearmboy.data.CustomTabStore
import com.eight87.tonearmboy.data.GenreSource
import com.eight87.tonearmboy.data.LibraryRepository
import com.eight87.tonearmboy.data.PlaylistStore
import com.eight87.tonearmboy.data.TrackSource
import com.eight87.tonearmboy.data.model.Album
import com.eight87.tonearmboy.data.model.Artist
import com.eight87.tonearmboy.data.model.Genre
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.common.FastScrollbar
import com.eight87.tonearmboy.ui.library.tabs.AlbumsTabScreen
import com.eight87.tonearmboy.ui.library.tabs.ArtistsTabScreen
import com.eight87.tonearmboy.ui.library.tabs.GenresTabScreen
import com.eight87.tonearmboy.ui.library.tabs.TracksListScreen
import com.eight87.tonearmboy.ui.settings.LibraryTab
import com.eight87.tonearmboy.ui.settings.SettingsRepository
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode
import com.eight87.tonearmboy.ui.settings.catalog.SettingsDimens
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
 * that [com.eight87.tonearmboy.ui.settings.catalog.SettingsCard] uses, and
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
 * ("tonearmboy" title + search / sort / overflow icons), the
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
  // R.A.4 — composition root for the Library destination, so it takes
  // every narrow interface its sub-tabs need (ISP at the dispatch layer
  // means more constructor params here, but each downstream screen
  // depends on only the slice it reads). Keep them in the same order
  // as the eight-interface declaration in `data/LibraryDataInterfaces`.
  tracks: TrackSource,
  albums: AlbumSource,
  artists: ArtistSource,
  genres: GenreSource,
  playlists: PlaylistStore,
  customTabs: CustomTabStore,
  scanner: com.eight87.tonearmboy.data.LibraryScanner,
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
  filter: com.eight87.tonearmboy.data.FilterCriteria,
  onFilterChange: (com.eight87.tonearmboy.data.FilterCriteria) -> Unit,
  // Phase F.2 / F.3 — file-deletion entry. Passed list lets the same
  // callback serve the single-track menu item and the multi-select bar.
  onDeleteTracks: (List<Track>) -> Unit,
) {
  // R.B.5 — narrow per-key Flow reads via the facets the repository implements.
  val libraryTabsList by settingsRepository.libraryTabs.flow
    .collectAsState(initial = LibraryTab.DefaultOrder)
  val intelligentSorting by settingsRepository.intelligentSorting.flow
    .collectAsState(initial = true)
  val albumCoversMode by settingsRepository.albumCoversMode.flow
    .collectAsState(initial = com.eight87.tonearmboy.ui.settings.AlbumCoversMode.Default)
  val forceSquareCovers by settingsRepository.forceSquareCovers.flow
    .collectAsState(initial = false)
  // Visible tabs: anything in libraryTabsList is "shown" (hidden tabs
  // already filtered by SettingsRepository's parser when wired to the
  // Personalize sub-page). Default order = canonical.
  val visibleTabs = remember(libraryTabsList) {
    libraryTabsList.ifEmpty { LibraryTab.DefaultOrder }
  }
  // D.18.5 — custom tabs are rendered after the built-ins in the rail.
  val customTabsList by customTabs.customTabs().collectAsState(initial = emptyList())
  val totalRailCount = visibleTabs.size + customTabsList.size
  var selectedIndex by rememberSaveable { mutableStateOf(0) }
  if (totalRailCount == 0) {
    selectedIndex = 0
  } else if (selectedIndex >= totalRailCount) {
    selectedIndex = 0
  }
  val isCustomSelected = selectedIndex >= visibleTabs.size
  val activeTab = if (!isCustomSelected && visibleTabs.isNotEmpty()) visibleTabs[selectedIndex] else LibraryTab.Songs
  val activeCustomTab = if (isCustomSelected) customTabsList.getOrNull(selectedIndex - visibleTabs.size) else null

  val activeSort by settingsRepository.tabSort(activeTab).collectAsState(initial = TabSort.Default)
  // D.28.1 — read every tab's persisted view mode in one Flow so the
  // toggle is per-tab and switching tabs reads that tab's saved mode.
  val viewModes by settingsRepository.viewModes.collectAsState(
    initial = LibraryTab.entries.associateWith { ViewMode.defaultFor(it) },
  )
  // Custom tabs persist their view mode in DataStore keyed by tab id —
  // independent from the built-in `viewModes` map. When `activeCustomTab`
  // changes we re-subscribe so the icon and content reflect the new tab.
  val customTabViewModeDefault = activeCustomTab?.let { defaultViewModeFor(it.contentType) } ?: ViewMode.List
  val customTabViewMode by produceState(
    initialValue = customTabViewModeDefault,
    key1 = activeCustomTab?.id,
  ) {
    val tab = activeCustomTab
    if (tab != null) {
      val default = defaultViewModeFor(tab.contentType)
      settingsRepository.customTabViewMode(tab.id, default).collect { value = it }
    }
  }
  val activeViewMode = if (activeCustomTab != null) customTabViewMode
    else viewModes[activeTab] ?: ViewMode.defaultFor(activeTab)
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
              scope.launch {
                val custom = activeCustomTab
                if (custom != null) settingsRepository.setCustomTabViewMode(custom.id, next)
                else settingsRepository.setViewModeFor(activeTab, next)
              }
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
    ScanProgressBar(scanner = scanner)
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
        customTabs = customTabsList,
      )
      Box(modifier = Modifier.fillMaxSize()) {
        if (activeCustomTab != null) {
          CustomTabContent(
            customTab = activeCustomTab,
            tracks = tracks,
            albums = albums,
            artists = artists,
            genres = genres,
            settingsRepository = settingsRepository,
            sort = activeSort,
            intelligentSorting = intelligentSorting,
            forceSquareCovers = forceSquareCovers,
            albumCoversMode = albumCoversMode,
            viewMode = activeViewMode,
            onTrackClick = onTrackClick,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onAddTracksToPlaylist = onAddTracksToPlaylist,
            onDeleteTracks = onDeleteTracks,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onOpenGenre = onOpenGenre,
            onComingSoon = onComingSoon,
          )
        } else when (activeTab) {
          LibraryTab.Songs -> TracksListScreen(
            repository = tracks,
            sort = activeSort,
            intelligentSorting = intelligentSorting,
            filter = filter,
            viewMode = activeViewMode,
            albumCoversMode = albumCoversMode,
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
            repository = albums,
            sort = activeSort,
            intelligentSorting = intelligentSorting,
            forceSquare = forceSquareCovers,
            albumCoversMode = albumCoversMode,
            viewMode = activeViewMode,
            onAlbumClick = { a -> onOpenAlbum(a.name, a.artist) },
          )
          LibraryTab.Artists -> ArtistsTabScreen(
            repository = artists,
            settingsRepository = settingsRepository,
            sort = activeSort,
            intelligentSorting = intelligentSorting,
            viewMode = activeViewMode,
            onArtistClick = { a -> onOpenArtist(a.name) },
          )
          LibraryTab.Genres -> GenresTabScreen(
            repository = genres,
            sort = activeSort,
            viewMode = activeViewMode,
            onGenreClick = { g -> onOpenGenre(g.name) },
          )
          LibraryTab.Playlists -> PlaylistsTabScreen(
            repository = playlists,
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
    val tracksForRange by tracks.observeTracks().collectAsState(initial = emptyList())
    LibraryFilterSheet(
      current = filter,
      tracks = tracksForRange,
      onDismiss = { showFilterSheet = false },
      onApply = {
        onFilterChange(it)
        showFilterSheet = false
      },
      onReset = {
        onFilterChange(com.eight87.tonearmboy.data.FilterCriteria())
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
  return com.eight87.tonearmboy.data.sort.IntelligentSort
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

// --- Tracks ---------------------------------------------------------------

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
 * R.A.Q — inverse of [computeFlatIndexFromKeys] / [computeFlatIndex]
 * for the per-item-keys layout. Given a flat LazyColumn index,
 * returns the section letter that contains it (or null if no
 * sections exist).
 */
internal fun letterForFlatIndex(
  orderedKeys: List<String>,
  perItemKeys: List<String>,
  flatIndex: Int,
): String? {
  if (orderedKeys.isEmpty()) return null
  var flat = 0
  for (key in orderedKeys) {
    flat += 1 // header
    flat += perItemKeys.count { it == key }
    if (flatIndex < flat) return key
  }
  return orderedKeys.last()
}

/**
 * R.A.Q — inverse for the `grouped: Map<String, List<Track>>` layout
 * used by Tracks tab. Avoids re-counting per-item-keys by reading
 * directly from group sizes.
 */
internal fun letterForFlatIndexInGrouped(
  orderedKeys: List<String>,
  grouped: Map<String, List<*>>,
  flatIndex: Int,
): String? {
  if (orderedKeys.isEmpty()) return null
  var flat = 0
  for (key in orderedKeys) {
    flat += 1 // header
    flat += grouped[key]?.size ?: 0
    if (flatIndex < flat) return key
  }
  return orderedKeys.last()
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

// --- Playlists ------------------------------------------------------------

/**
 * D.28 — Playlists tab dispatcher. Tile mode → the existing
 * [PlaylistsTilesScreen] (D.27.6); List mode → sticky-header letter
 * list with the alphabet rail mounted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsTabScreen(
  repository: PlaylistStore,
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
    FastScrollbar(
      state = listState,
      sectionLabelFor = if (orderedKeys.isNotEmpty()) {
        { idx -> letterForFlatIndex(orderedKeys, sectionKeys, idx) }
      } else null,
    )
  }
}

/** Pre-D.28 wrapper retained for the existing `PlaylistsListScreenTest` and callers. */
@Composable
fun PlaylistsListScreen(
  repository: PlaylistStore,
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
internal fun TwoLineRow(
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
internal fun SectionHeader(letter: String) {
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
internal fun MultiSelectBar(
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
internal fun EmptyState(message: String) {
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
