package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.eight87.tonearmboy.data.ArtistSource
import com.eight87.tonearmboy.data.model.Artist
import com.eight87.tonearmboy.ui.library.TileItem
import com.eight87.tonearmboy.ui.library.initialKey
import com.eight87.tonearmboy.ui.library.sortArtists
import com.eight87.tonearmboy.ui.library.sortNameKey
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.SettingsRepository
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode

/**
 * D.28 — Artists tab dispatcher. List = sticky-header rows; Tile =
 * LibraryTileGrid with letter-avatar fallback (no representative
 * cover resolved per artist on this pass).
 */
@Composable
fun ArtistsTabScreen(
  repository: ArtistSource,
  settingsRepository: SettingsRepository,
  sort: TabSort,
  intelligentSorting: Boolean,
  viewMode: ViewMode,
  // D.30.2 — when non-empty the underlying artists Flow is filtered.
  // Note: the global `hideCollaborators` setting only applies to the
  // unfiltered path; custom tabs apply their own predicate.
  filter: com.eight87.tonearmboy.data.FilterCriteria = com.eight87.tonearmboy.data.FilterCriteria(),
  onArtistClick: (Artist) -> Unit = {},
) {
  val artists by remember(filter) {
    if (filter.isEmpty()) repository.observeArtists(settingsRepository.hideCollaborators.flow)
    else repository.artistsMatching(filter)
  }.collectAsState(initial = emptyList())
  val sorted = remember(artists, sort, intelligentSorting) { sortArtists(artists, sort, intelligentSorting) }
  LibraryTabRenderer(
    spec = ArtistsTabSpec,
    items = sorted,
    sort = sort,
    viewMode = viewMode,
    intelligentSorting = intelligentSorting,
    albumCoversMode = AlbumCoversMode.Default,
    onItemClick = onArtistClick,
  )
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

/** R.D.3 — TabSpec for the Artists tab. */
internal object ArtistsTabSpec : TabSpec<Artist> {
  override val testTag: String = "artists_tab"
  override val emptyMessage: String = "No artists yet."
  override val supportsTileMode: Boolean = true

  override fun id(item: Artist): Long = item.id

  override fun sectionKey(item: Artist, sort: TabSort, intelligentSorting: Boolean): String? =
    if (sort.key == SortKey.Name || sort.key == SortKey.Artist)
      initialKey(sortNameKey(item.name, intelligentSorting))
    else null

  override fun toTile(item: Artist): TileItem = TileItem(
    id = item.id,
    title = item.name,
    subtitle = "${item.albumCount} albums · ${item.trackCount} tracks",
    artUri = null,
    albumArtId = null,
  )

  @Composable
  override fun ListRow(
    item: Artist,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
  ) {
    TwoLineRow(
      primary = item.name,
      secondary = "${item.albumCount} albums · ${item.trackCount} tracks",
      onClick = onClick,
    )
  }
}
