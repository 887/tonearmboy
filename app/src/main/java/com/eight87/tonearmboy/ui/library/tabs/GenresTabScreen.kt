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
import com.eight87.tonearmboy.data.GenreSource
import com.eight87.tonearmboy.data.model.Genre
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
import com.eight87.tonearmboy.ui.library.sortGenres
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode
import com.eight87.tonearmboy.ui.settings.catalog.SettingsDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenresTabScreen(
  repository: GenreSource,
  sort: TabSort,
  viewMode: ViewMode,
  // D.30.2 — when non-empty, the underlying genres Flow is filtered.
  filter: com.eight87.tonearmboy.data.FilterCriteria = com.eight87.tonearmboy.data.FilterCriteria(),
  onGenreClick: (Genre) -> Unit = {},
) {
  val genres by remember(filter) {
    if (filter.isEmpty()) repository.observeGenres() else repository.genresMatching(filter)
  }.collectAsState(initial = emptyList())
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

/** Pre-D.28 wrapper retained for callers / tests. */
@Composable
fun GenresListScreen(
  repository: GenreSource,
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
