package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
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
  val effectiveTileMode = viewMode != ViewMode.List && spec.supportsTileMode
  // ViewMode.TwoColumn pins the grid to exactly two columns; Tile
  // keeps the pre-existing adaptive 160-dp-min layout.
  val gridColumns = if (viewMode == ViewMode.TwoColumn) {
    GridCells.Fixed(2)
  } else {
    GridCells.Adaptive(minSize = 160.dp)
  }

  // Box overlay (was a Row that gave the FastScrollbar 40 dp of its own
  // horizontal slot, leaving a black strip between the album cards and
  // the visible thumb). The list / grid now fillMaxSize and the
  // scrollbar overlays at CenterEnd; cards extend right up to the
  // screen edge whether the scrollbar is visible or not.
  Box(modifier = modifier.fillMaxSize().semantics { testTag = spec.testTag }) {
    if (effectiveTileMode) {
      val resources = LocalContext.current.resources
      val tileItems = remember(items, resources) { items.mapNotNull { spec.toTile(it, resources) } }
      // R4 — when the spec opts into tile overflow, wrap its
      // composable hook in a `(TileItem, dismiss) -> Unit` slot keyed
      // off the underlying entity (resolved via spec.id(t) == tile.id).
      val tileOverflowMenu: (@Composable (com.eight87.tonearmboy.ui.library.TileItem, () -> Unit) -> Unit)? =
        if (spec.showTileOverflow) {
          { tile, dismiss ->
            val entity = items.firstOrNull { spec.id(it) == tile.id }
            if (entity != null) spec.TileOverflowMenu(entity, dismiss)
          }
        } else null
      LibraryTileGrid(
        tiles = tileItems,
        sectionKeys = sectionKeys,
        state = gridState,
        albumCoversMode = albumCoversMode,
        columns = gridColumns,
        onTileClick = { tile ->
          val item = items.firstOrNull { spec.id(it) == tile.id } ?: return@LibraryTileGrid
          onItemClick(item)
        },
        onTileLongClick = { tile ->
          val item = items.firstOrNull { spec.id(it) == tile.id } ?: return@LibraryTileGrid
          onItemLongClick?.invoke(item)
        },
        tileOverflowMenu = tileOverflowMenu,
        inSelectionMode = selection?.inSelectionMode ?: false,
        isSelected = selection?.let { sel -> { tile -> sel.contains(tile.id) } },
        modifier = Modifier.fillMaxSize().padding(horizontal = SettingsDimens.PagePadding),
      )
    } else {
      LazyColumn(state = listState, modifier = Modifier.fillMaxSize().libraryListCard()) {
        if (grouped.isNotEmpty()) {
          orderedKeys.forEach { key ->
            stickyHeader { SectionHeader(key) }
            items(grouped.getValue(key), key = { spec.id(it) }) { item ->
              SelectableListRow(spec, item, selection, onItemClick, onItemLongClick)
            }
          }
        } else {
          items(items, key = { spec.id(it) }) { item ->
            SelectableListRow(spec, item, selection, onItemClick, onItemLongClick)
          }
        }
      }
    }
    if (effectiveTileMode) {
      FastScrollbar(
        state = gridState,
        modifier = Modifier.align(Alignment.CenterEnd),
        sectionLabelFor = if (sectionKeys.isNotEmpty()) {
          { idx -> letterForTileIndex(sectionKeys, idx) }
        } else null,
      )
    } else {
      FastScrollbar(
        state = listState,
        modifier = Modifier.align(Alignment.CenterEnd),
        sectionLabelFor = if (orderedKeys.isNotEmpty()) {
          { idx -> letterForFlatIndex(orderedKeys, sectionKeys, idx) }
        } else null,
      )
    }
  }
}

/**
 * Wraps a [TabSpec.ListRow] call in a Box that paints the
 * `secondaryContainer` selection background when the row is selected.
 * Each TabSpec's row implementation focuses on its own content; the
 * uniform selection visual is owned here so every tab (Albums, Artists,
 * Genres, Playlists, Tracks via the same renderer) gets the same
 * highlight without re-implementing the bg painting per row.
 */
@Composable
private fun <T : Any> SelectableListRow(
  spec: TabSpec<T>,
  item: T,
  selection: SelectionState<Long>?,
  onItemClick: (T) -> Unit,
  onItemLongClick: ((T) -> Unit)?,
) {
  val selected = selection?.contains(spec.id(item)) ?: false
  val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
  Box(modifier = Modifier.fillMaxWidth().background(bg)) {
    spec.ListRow(
      item = item,
      selected = selected,
      inSelectionMode = selection?.inSelectionMode ?: false,
      onClick = { onItemClick(item) },
      onLongClick = { onItemLongClick?.invoke(item) },
    )
  }
}
