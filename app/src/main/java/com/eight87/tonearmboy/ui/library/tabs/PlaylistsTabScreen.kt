package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.eight87.tonearmboy.data.PlaylistStore
import com.eight87.tonearmboy.ui.common.FastScrollbar
import com.eight87.tonearmboy.ui.library.EmptyState
import com.eight87.tonearmboy.ui.library.PlaylistsTilesScreen
import com.eight87.tonearmboy.ui.library.SectionHeader
import com.eight87.tonearmboy.ui.library.TwoLineRow
import com.eight87.tonearmboy.ui.library.initialKey
import com.eight87.tonearmboy.ui.library.letterForFlatIndex
import com.eight87.tonearmboy.ui.library.libraryListCard
import com.eight87.tonearmboy.ui.settings.ViewMode

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
