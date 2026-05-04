package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.data.TrackSource
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.common.FastScrollbar
// EmptyState is in same package (tabs)
import com.eight87.tonearmboy.ui.library.LibraryTileGrid
// MultiSelectBar is in same package (tabs)
// SectionHeader is in same package (tabs)
import com.eight87.tonearmboy.ui.library.TileItem
import com.eight87.tonearmboy.ui.library.initialKey
import com.eight87.tonearmboy.ui.library.letterForFlatIndexInGrouped
import com.eight87.tonearmboy.ui.library.letterForTileIndex
import com.eight87.tonearmboy.ui.library.libraryListCard
import com.eight87.tonearmboy.ui.library.sortNameKey
import com.eight87.tonearmboy.ui.library.sortTracks
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode
import com.eight87.tonearmboy.ui.settings.catalog.SettingsDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TracksListScreen(
  repository: TrackSource,
  sort: TabSort,
  intelligentSorting: Boolean,
  // D.27.5 — when non-empty, the underlying tracks Flow is filtered.
  filter: com.eight87.tonearmboy.data.FilterCriteria = com.eight87.tonearmboy.data.FilterCriteria(),
  // D.28.3 — list vs tile dispatch. Defaults to `List` so the search
  // screen and other repository-free callers still see the legacy
  // shape without explicit wiring.
  viewMode: ViewMode = ViewMode.List,
  albumCoversMode: com.eight87.tonearmboy.ui.settings.AlbumCoversMode =
    com.eight87.tonearmboy.ui.settings.AlbumCoversMode.Balanced,
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
 * track list without needing to bypass `LibraryRepository`'s on-first-
 * subscription scanner trigger (which wipes a seeded in-memory DB).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TracksListContent(
  tracks: List<Track>,
  sort: TabSort,
  intelligentSorting: Boolean,
  viewMode: ViewMode = ViewMode.List,
  albumCoversMode: com.eight87.tonearmboy.ui.settings.AlbumCoversMode =
    com.eight87.tonearmboy.ui.settings.AlbumCoversMode.Balanced,
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
  // R.A.Q — tile-mode per-tile section keys, hoisted to function
  // scope so the FastScrollbar's section-label lookup can read them.
  val tileSectionKeys = remember(sorted, sort, intelligentSorting) {
    if (sort.key == SortKey.Name)
      sorted.map { initialKey(sortNameKey(it.title, intelligentSorting)) }
    else emptyList()
  }
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
        LibraryTileGrid(
          tiles = tileItems,
          sectionKeys = tileSectionKeys,
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
      if (viewMode == ViewMode.List) {
        FastScrollbar(
          state = listState,
          sectionLabelFor = if (orderedKeys.isNotEmpty()) {
            { idx -> letterForFlatIndexInGrouped(orderedKeys, grouped, idx) }
          } else null,
        )
      } else {
        FastScrollbar(
          state = gridState,
          sectionLabelFor = if (tileSectionKeys.isNotEmpty()) {
            { idx -> letterForTileIndex(tileSectionKeys, idx) }
          } else null,
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
