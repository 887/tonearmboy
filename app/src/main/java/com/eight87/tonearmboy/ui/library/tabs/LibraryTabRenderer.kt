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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.eight87.tonearmboy.ui.common.FastScrollbar
import com.eight87.tonearmboy.ui.library.LibraryTileGrid
import com.eight87.tonearmboy.ui.library.letterForFlatIndex
import com.eight87.tonearmboy.ui.library.letterForTileIndex
import com.eight87.tonearmboy.ui.library.libraryListCard
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode
import com.eight87.tonearmboy.ui.settings.catalog.SettingsDimens

/**
 * R.D.3 — generic library-tab renderer. Walks a [TabSpec] against
 * a pre-sorted item list and produces the chrome (LazyColumn or
 * LazyVerticalGrid + FastScrollbar + sticky headers).
 *
 * Replaces the 5 near-duplicate dispatcher composables that R.D.1
 * extracted. Each per-tab `XxxTabScreen` now does its own
 * data-fetch + sort, then hands the result here.
 *
 * Multi-select is opt-in: the [selection] parameter wires
 * `selected` + `inSelectionMode` into [TabSpec.ListRow] when
 * supplied; pass null for tabs that don't need selection (Albums /
 * Artists / Genres / Playlists).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Any> LibraryTabRenderer(
  spec: TabSpec<T>,
  items: List<T>,
  sort: TabSort,
  viewMode: ViewMode,
  intelligentSorting: Boolean,
  albumCoversMode: AlbumCoversMode,
  onItemClick: (T) -> Unit,
  modifier: Modifier = Modifier,
  selection: SelectionState<Long>? = null,
  onItemLongClick: ((T) -> Unit)? = null,
) {
  if (items.isEmpty()) {
    EmptyState(spec.emptyMessageRes)
    return
  }

  val sectionKeys = remember(items, sort, intelligentSorting) {
    items.map { spec.sectionKey(it, sort, intelligentSorting) ?: "" }
      .takeIf { keys -> keys.any { it.isNotEmpty() } }
      ?: emptyList()
  }
  val orderedKeys = remember(sectionKeys) { sectionKeys.distinct().filter { it.isNotEmpty() } }
  val grouped = remember(items, sectionKeys) {
    if (sectionKeys.isEmpty()) emptyMap()
    else items.zip(sectionKeys).groupBy({ it.second }, { it.first })
  }

  val listState = rememberLazyListState()
  val gridState = rememberLazyGridState()
  val effectiveTileMode = viewMode == ViewMode.Tile && spec.supportsTileMode

  Row(modifier = modifier.fillMaxSize().semantics { testTag = spec.testTag }) {
    if (effectiveTileMode) {
      val resources = LocalContext.current.resources
      val tileItems = remember(items, resources) { items.mapNotNull { spec.toTile(it, resources) } }
      LibraryTileGrid(
        tiles = tileItems,
        sectionKeys = sectionKeys,
        state = gridState,
        albumCoversMode = albumCoversMode,
        onTileClick = { tile ->
          val item = items.firstOrNull { spec.id(it) == tile.id } ?: return@LibraryTileGrid
          onItemClick(item)
        },
        onTileLongClick = { tile ->
          val item = items.firstOrNull { spec.id(it) == tile.id } ?: return@LibraryTileGrid
          onItemLongClick?.invoke(item)
        },
        modifier = Modifier.weight(1f).padding(horizontal = SettingsDimens.PagePadding),
      )
    } else {
      LazyColumn(state = listState, modifier = Modifier.weight(1f).libraryListCard()) {
        if (grouped.isNotEmpty()) {
          orderedKeys.forEach { key ->
            stickyHeader { SectionHeader(key) }
            items(grouped.getValue(key), key = { spec.id(it) }) { item ->
              spec.ListRow(
                item = item,
                selected = selection?.contains(spec.id(item)) ?: false,
                inSelectionMode = selection?.inSelectionMode ?: false,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick?.invoke(item) },
              )
            }
          }
        } else {
          items(items, key = { spec.id(it) }) { item ->
            spec.ListRow(
              item = item,
              selected = selection?.contains(spec.id(item)) ?: false,
              inSelectionMode = selection?.inSelectionMode ?: false,
              onClick = { onItemClick(item) },
              onLongClick = { onItemLongClick?.invoke(item) },
            )
          }
        }
      }
    }
    if (effectiveTileMode) {
      FastScrollbar(
        state = gridState,
        sectionLabelFor = if (sectionKeys.isNotEmpty()) {
          { idx -> letterForTileIndex(sectionKeys, idx) }
        } else null,
      )
    } else {
      FastScrollbar(
        state = listState,
        sectionLabelFor = if (orderedKeys.isNotEmpty()) {
          { idx -> letterForFlatIndex(orderedKeys, sectionKeys, idx) }
        } else null,
      )
    }
  }
}
