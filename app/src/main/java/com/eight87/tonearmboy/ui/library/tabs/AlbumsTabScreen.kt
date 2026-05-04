package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.data.AlbumSource
import com.eight87.tonearmboy.data.model.Album
import com.eight87.tonearmboy.ui.common.FastScrollbar
import com.eight87.tonearmboy.ui.library.CoverArt
import com.eight87.tonearmboy.ui.library.EmptyState
import com.eight87.tonearmboy.ui.library.LibraryTileGrid
import com.eight87.tonearmboy.ui.library.SectionHeader
import com.eight87.tonearmboy.ui.library.TileItem
import com.eight87.tonearmboy.ui.library.initialKey
import com.eight87.tonearmboy.ui.library.letterForFlatIndex
import com.eight87.tonearmboy.ui.library.letterForTileIndex
import com.eight87.tonearmboy.ui.library.libraryListCard
import com.eight87.tonearmboy.ui.library.sortAlbums
import com.eight87.tonearmboy.ui.library.sortNameKey
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode
import com.eight87.tonearmboy.ui.settings.catalog.SettingsDimens

/**
 * D.28 — albums tab dispatcher. List mode → sticky-header list with
 * a 48 dp leading thumbnail (D.28.4); Tile mode → existing grid (was
 * the only mode pre-D.28). Both modes mount the alphabet rail when
 * sort is alphabetical (D.28.5).
 */
@Composable
fun AlbumsTabScreen(
  repository: AlbumSource,
  sort: TabSort,
  intelligentSorting: Boolean,
  forceSquare: Boolean,
  albumCoversMode: com.eight87.tonearmboy.ui.settings.AlbumCoversMode,
  viewMode: ViewMode,
  // D.30.2 — when non-empty, the underlying albums Flow is filtered.
  // Mirrors the [TracksListScreen.filter] contract so custom tabs can
  // delegate straight into this screen and inherit its full chrome
  // (alphabet rail, list↔tile, sort).
  filter: com.eight87.tonearmboy.data.FilterCriteria = com.eight87.tonearmboy.data.FilterCriteria(),
  onAlbumClick: (Album) -> Unit = {},
) {
  val albums by remember(filter) {
    if (filter.isEmpty()) repository.observeAlbums() else repository.albumsMatching(filter)
  }.collectAsState(initial = emptyList())
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
  albumCoversMode: com.eight87.tonearmboy.ui.settings.AlbumCoversMode,
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
    if (viewMode == ViewMode.List) {
      FastScrollbar(
        state = listState,
        sectionLabelFor = if (orderedKeys.isNotEmpty()) {
          { idx -> letterForFlatIndex(orderedKeys, sectionKeys, idx) }
        } else null,
      )
    } else {
      FastScrollbar(
        state = gridState,
        sectionLabelFor = if (sectionKeys.isNotEmpty()) {
          { idx -> letterForTileIndex(sectionKeys, idx) }
        } else null,
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
  albumCoversMode: com.eight87.tonearmboy.ui.settings.AlbumCoversMode,
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
  repository: AlbumSource,
  sort: TabSort,
  intelligentSorting: Boolean,
  forceSquare: Boolean,
  albumCoversMode: com.eight87.tonearmboy.ui.settings.AlbumCoversMode,
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
