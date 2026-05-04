package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.eight87.tonearmboy.data.PlaylistStore
import com.eight87.tonearmboy.data.model.Playlist
import com.eight87.tonearmboy.ui.library.PlaylistsTilesScreen
import com.eight87.tonearmboy.ui.library.TileItem
import com.eight87.tonearmboy.ui.library.initialKey
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode

/**
 * D.28 — Playlists tab dispatcher. Tile mode → the existing
 * [PlaylistsTilesScreen] (D.27.6) which has its own rename/delete/
 * set-cover affordances; List mode → [LibraryTabRenderer] driven by
 * [PlaylistsTabSpec].
 */
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
  LibraryTabRenderer(
    spec = PlaylistsTabSpec,
    items = playlists,
    sort = TabSort.Default,
    viewMode = ViewMode.List,
    intelligentSorting = false,
    albumCoversMode = AlbumCoversMode.Default,
    onItemClick = { onPlaylistClick(it.id) },
  )
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

/**
 * R.D.3 — TabSpec for the Playlists tab. List-only inside this spec;
 * tile mode is dispatched to [PlaylistsTilesScreen] before the
 * renderer is reached.
 */
internal object PlaylistsTabSpec : TabSpec<Playlist> {
  override val testTag: String = "playlists_tab"
  override val emptyMessageRes: Int = com.eight87.tonearmboy.R.string.library_empty_playlists
  override val supportsTileMode: Boolean = false

  override fun id(item: Playlist): Long = item.id

  override fun sectionKey(item: Playlist, sort: TabSort, intelligentSorting: Boolean): String? =
    initialKey(item.name.uppercase())

  override fun toTile(item: Playlist): TileItem? = null

  @Composable
  override fun ListRow(
    item: Playlist,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
  ) {
    TwoLineRow(
      primary = item.name,
      secondary = "${item.trackCount} tracks",
      onClick = onClick,
    )
  }
}
