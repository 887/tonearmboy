package com.eight87.tonearmboy.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode

/**
 * D.28.3 — shared tile renderer for every library tab in tile mode.
 *
 * Each tab maps its domain rows ([Track] / [Album] / [Artist] / [Genre] /
 * [Playlist]) into [TileItem] before handing the list off here, so
 * tile-rendering chrome only lives in one place.
 *
 * The grid is built with `GridCells.Adaptive(minSize = 160.dp)` per the
 * D.28 plan, giving ~2 columns on phone and ~3 on tablet. Each tile is
 * a square cover image (via Coil for an explicit URI, or [CoverArt] for
 * a MediaStore album id) followed by title + optional subtitle.
 *
 * D.28.5 — when [sectionKeys] is non-empty the grid surfaces letter
 * group headers as full-row items (`span = maxLineSpan`) so the
 * alphabet rail can scroll the grid by section the same way it does
 * the list.
 *
 * @param items per-tile data
 * @param sectionKeys parallel list — `sectionKeys[i]` is the section
 *   key (e.g. `"A"`) for `items[i]`. Empty list = no sectioning.
 */
data class TileItem(
  val id: Long,
  val title: String,
  val subtitle: String?,
  /**
   * SAF / network image URI for the cover. When null and
   * [albumArtId] is also null the tile renders the music-note
   * placeholder via [CoverArt].
   */
  val artUri: String?,
  /**
   * MediaStore album id used by [CoverArt] when [artUri] is null.
   * Songs / Albums / Genres / Artists tiles use this; Playlist tiles
   * generally pre-resolve to [artUri] (or the letter avatar fallback).
   */
  val albumArtId: Long?,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryTileGrid(
  tiles: List<TileItem>,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(8.dp),
  state: LazyGridState = rememberLazyGridState(),
  sectionKeys: List<String> = emptyList(),
  onTileClick: (TileItem) -> Unit = {},
  onTileLongClick: (TileItem) -> Unit = {},
  albumCoversMode: AlbumCoversMode = AlbumCoversMode.Balanced,
) {
  // Build the (header letter, runStartIndex, runLength) tuples once so
  // both the renderer and the alphabet helpers can reason about the
  // same layout. A group whose key matches the previous item's key
  // collapses into the same run — i.e. the headers appear exactly once
  // per letter group.
  val groups = remember(tiles, sectionKeys) {
    if (sectionKeys.size != tiles.size) emptyList() else buildGroups(sectionKeys)
  }

  LazyVerticalGrid(
    state = state,
    columns = GridCells.Adaptive(minSize = 160.dp),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.semantics { testTag = "library_tile_grid" },
  ) {
    if (groups.isEmpty()) {
      items(items = tiles, key = { it.id }) { tile ->
        TileCell(tile, onTileClick, onTileLongClick, albumCoversMode)
      }
    } else {
      groups.forEach { group ->
        item(
          span = { GridItemSpan(maxLineSpan) },
          key = "header_${group.letter}",
        ) {
          TileSectionHeader(group.letter)
        }
        val slice = tiles.subList(group.start, group.start + group.length)
        items(items = slice, key = { it.id }) { tile ->
          TileCell(tile, onTileClick, onTileLongClick, albumCoversMode)
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCell(
  item: TileItem,
  onClick: (TileItem) -> Unit,
  onLongClick: (TileItem) -> Unit,
  albumCoversMode: AlbumCoversMode,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(4.dp)
      .combinedClickable(
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
      )
      .semantics { testTag = "library_tile_${item.id}" },
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      when {
        item.artUri != null -> AsyncImage(
          model = item.artUri,
          contentDescription = item.title,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
        item.albumArtId != null -> CoverArt(
          albumId = item.albumArtId,
          size = 160.dp,
          mode = albumCoversMode,
          contentDescription = item.title,
          modifier = Modifier.fillMaxSize(),
        )
        else -> Text(
          text = letterFor(item.title),
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    Text(
      text = item.title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
    )
    if (item.subtitle != null) {
      Text(
        text = item.subtitle,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
      )
    }
  }
}

@Composable
private fun TileSectionHeader(letter: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .padding(horizontal = 16.dp, vertical = 6.dp)
      .semantics { testTag = "library_tile_section_${letter}" },
  ) {
    Text(text = letter, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
  }
}

/** Internal grouping representation; visible for tests. */
internal data class TileGroup(val letter: String, val start: Int, val length: Int)

/**
 * Walk a parallel list of section keys and produce one [TileGroup] per
 * contiguous run of equal keys. Empty input yields empty output.
 *
 * Visible for `AlphabetScrollerTileTest`.
 */
internal fun buildGroups(keys: List<String>): List<TileGroup> {
  if (keys.isEmpty()) return emptyList()
  val out = mutableListOf<TileGroup>()
  var runStart = 0
  for (i in 1..keys.size) {
    if (i == keys.size || keys[i] != keys[runStart]) {
      out += TileGroup(letter = keys[runStart], start = runStart, length = i - runStart)
      runStart = i
    }
  }
  return out
}

/**
 * D.28.5 — convert a section letter into the *grid* index of the
 * header cell for that letter. Tile mode prepends a full-row header
 * before each run, so the grid indices look like:
 *
 *   0: header A
 *   1..k: tiles starting with A
 *   k+1: header B
 *   …
 *
 * Returns -1 when the letter has no matching group.
 *
 * Pure / public so `AlphabetScrollerTileTest` can pin the math without
 * standing up a Compose host.
 */
internal fun tileIndexForGroups(groups: List<TileGroup>, letter: String): Int {
  var idx = 0
  for (g in groups) {
    if (g.letter == letter) return idx
    idx += 1 // header
    idx += g.length
  }
  return -1
}

/** Convenience overload working straight from a flat keys list. */
fun tileIndexFor(sectionKeys: List<String>, letter: String): Int =
  tileIndexForGroups(buildGroups(sectionKeys), letter)

/**
 * R.A.Q — inverse of [tileIndexFor]: which section letter contains
 * the tile at [gridIndex]? Used by the FastScrollbar bubble.
 */
fun letterForTileIndex(sectionKeys: List<String>, gridIndex: Int): String? {
  val groups = buildGroups(sectionKeys)
  var idx = 0
  for (g in groups) {
    val sectionEnd = idx + 1 + g.length
    if (gridIndex < sectionEnd) return g.letter
    idx = sectionEnd
  }
  return groups.lastOrNull()?.letter
}
