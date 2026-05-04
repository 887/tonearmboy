package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.data.AlbumSource
import com.eight87.tonearmboy.data.model.Album
import com.eight87.tonearmboy.ui.library.CoverArt
import com.eight87.tonearmboy.ui.library.TileItem
import com.eight87.tonearmboy.ui.library.initialKey
import com.eight87.tonearmboy.ui.library.sortAlbums
import com.eight87.tonearmboy.ui.library.sortNameKey
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode

/**
 * D.28 — albums tab dispatcher. Thin wrapper around
 * [LibraryTabRenderer] driven by [AlbumsTabSpec].
 */
@Composable
fun AlbumsTabScreen(
  repository: AlbumSource,
  sort: TabSort,
  intelligentSorting: Boolean,
  forceSquare: Boolean,
  albumCoversMode: AlbumCoversMode,
  viewMode: ViewMode,
  // D.30.2 — when non-empty the underlying albums Flow is filtered.
  filter: com.eight87.tonearmboy.data.FilterCriteria = com.eight87.tonearmboy.data.FilterCriteria(),
  onAlbumClick: (Album) -> Unit = {},
) {
  val albums by remember(filter) {
    if (filter.isEmpty()) repository.observeAlbums() else repository.albumsMatching(filter)
  }.collectAsState(initial = emptyList())
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
@Composable
internal fun AlbumsTabContent(
  albums: List<Album>,
  sort: TabSort,
  intelligentSorting: Boolean,
  albumCoversMode: AlbumCoversMode,
  viewMode: ViewMode,
  onAlbumClick: (Album) -> Unit = {},
) {
  val sorted = remember(albums, sort, intelligentSorting) { sortAlbums(albums, sort, intelligentSorting) }
  val spec = remember(albumCoversMode) { AlbumsTabSpec(albumCoversMode) }
  LibraryTabRenderer(
    spec = spec,
    items = sorted,
    sort = sort,
    viewMode = viewMode,
    intelligentSorting = intelligentSorting,
    albumCoversMode = albumCoversMode,
    onItemClick = onAlbumClick,
  )
}

/**
 * R.D.3 — TabSpec for the Albums tab. Sections by name when sort is
 * by name; tile mode shows cover art via [TileItem.albumArtId].
 */
internal class AlbumsTabSpec(
  private val albumCoversMode: AlbumCoversMode,
) : TabSpec<Album> {
  override val testTag: String = "albums_tab"
  override val emptyMessage: String =
    "No albums yet. Add audio files to your device, then tap Rescan music."
  override val supportsTileMode: Boolean = true

  override fun id(item: Album): Long = item.id

  override fun sectionKey(item: Album, sort: TabSort, intelligentSorting: Boolean): String? =
    if (sort.key == SortKey.Name) initialKey(sortNameKey(item.name, intelligentSorting))
    else null

  override fun toTile(item: Album): TileItem = TileItem(
    id = item.id,
    title = item.name,
    subtitle = item.artist ?: "Unknown artist",
    artUri = null,
    albumArtId = item.mediaStoreAlbumId,
  )

  @Composable
  override fun ListRow(
    item: Album,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
  ) {
    AlbumListRow(item, albumCoversMode, onClick)
  }
}

/**
 * D.28.4 — list-mode album row: 48 dp leading thumbnail + name + artist.
 */
@Composable
internal fun AlbumListRow(
  album: Album,
  albumCoversMode: AlbumCoversMode,
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
