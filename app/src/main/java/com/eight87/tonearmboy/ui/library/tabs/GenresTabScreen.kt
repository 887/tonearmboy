package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.eight87.tonearmboy.data.GenreSource
import com.eight87.tonearmboy.data.model.Genre
import com.eight87.tonearmboy.ui.library.TileItem
import com.eight87.tonearmboy.ui.library.initialKey
import com.eight87.tonearmboy.ui.library.sortGenres
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode

/**
 * D.28 — Genres tab dispatcher. Sections by initial letter when sort
 * is anything other than Duration; tile mode shows a letter-avatar
 * fallback (no representative cover per genre on this pass).
 */
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
  val sorted = remember(genres, sort) { sortGenres(genres, sort) }
  LibraryTabRenderer(
    spec = GenresTabSpec,
    items = sorted,
    sort = sort,
    viewMode = viewMode,
    intelligentSorting = false,
    albumCoversMode = AlbumCoversMode.Default,
    onItemClick = onGenreClick,
  )
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

/** R.D.3 — TabSpec for the Genres tab. */
internal object GenresTabSpec : TabSpec<Genre> {
  override val testTag: String = "genres_tab"
  override val emptyMessage: String = "No genres yet."
  override val supportsTileMode: Boolean = true

  override fun id(item: Genre): Long = item.id

  override fun sectionKey(item: Genre, sort: TabSort, intelligentSorting: Boolean): String? =
    if (sort.key != SortKey.Duration) initialKey(item.name.uppercase()) else null

  override fun toTile(item: Genre): TileItem = TileItem(
    id = item.id,
    title = item.name,
    subtitle = "${item.trackCount} tracks",
    artUri = null,
    albumArtId = null,
  )

  @Composable
  override fun ListRow(
    item: Genre,
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
