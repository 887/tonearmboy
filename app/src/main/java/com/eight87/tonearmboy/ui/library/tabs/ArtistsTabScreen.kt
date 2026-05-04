package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.eight87.tonearmboy.data.ArtistSource
import com.eight87.tonearmboy.data.model.Artist
import com.eight87.tonearmboy.ui.common.FastScrollbar
import com.eight87.tonearmboy.ui.library.EmptyState
import com.eight87.tonearmboy.ui.library.LibraryTileGrid
import com.eight87.tonearmboy.ui.library.SectionHeader
import com.eight87.tonearmboy.ui.library.TileItem
import com.eight87.tonearmboy.ui.library.TwoLineRow
import com.eight87.tonearmboy.ui.library.initialKey
import com.eight87.tonearmboy.ui.library.letterForFlatIndex
import com.eight87.tonearmboy.ui.library.letterForTileIndex
import com.eight87.tonearmboy.ui.library.libraryListCard
import com.eight87.tonearmboy.ui.library.sortArtists
import com.eight87.tonearmboy.ui.library.sortNameKey
import com.eight87.tonearmboy.ui.settings.SettingsRepository
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode
import com.eight87.tonearmboy.ui.settings.catalog.SettingsDimens

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
  repository: ArtistSource,
  settingsRepository: SettingsRepository,
  sort: TabSort,
  intelligentSorting: Boolean,
  viewMode: ViewMode,
  // D.30.2 — when non-empty, the underlying artists Flow is filtered.
  // Note: the global `hideCollaborators` setting only applies to the
  // unfiltered path; custom tabs apply their own predicate against the
  // unfiltered artist set, since the user is intentionally building a
  // subset and may want collaborators (the filter is the authority).
  filter: com.eight87.tonearmboy.data.FilterCriteria = com.eight87.tonearmboy.data.FilterCriteria(),
  onArtistClick: (Artist) -> Unit = {},
) {
  val artists by remember(filter) {
    if (filter.isEmpty()) repository.observeArtists(settingsRepository.hideCollaborators.flow)
    else repository.artistsMatching(filter)
  }.collectAsState(initial = emptyList())
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

/** Pre-D.28 wrapper retained so existing callers / tests still compile. */
@Composable
fun ArtistsListScreen(
  repository: ArtistSource,
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
