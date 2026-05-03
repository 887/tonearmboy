package com.eight87.tonearm.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eight87.tonearm.data.AlbumSource
import com.eight87.tonearm.data.ArtistSource
import com.eight87.tonearm.data.FilterCriteria
import com.eight87.tonearm.data.GenreSource
import com.eight87.tonearm.data.TrackSource
import com.eight87.tonearm.data.db.CustomTabContentType
import com.eight87.tonearm.data.db.CustomTabEntity
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.ui.settings.SettingsRepository
import com.eight87.tonearm.ui.settings.SettingsSnapshot
import com.eight87.tonearm.ui.settings.TabSort
import com.eight87.tonearm.ui.settings.ViewMode

/**
 * Per-content-type fallback view mode for a freshly-created custom tab.
 * Mirrors `ViewMode.defaultFor` for the built-ins: ALBUMS gets Tile,
 * everything else gets List. Used by [SettingsRepository.customTabViewMode]
 * and the top-bar toggle when the user hasn't picked a mode yet.
 */
fun defaultViewModeFor(contentType: CustomTabContentType): ViewMode = when (contentType) {
  CustomTabContentType.ALBUMS -> ViewMode.Tile
  else -> ViewMode.List
}

/**
 * D.30.2 — content host for a user-defined library tab.
 *
 * A custom tab is *just a filtered view* of the same content the
 * built-in tabs render. Each branch dispatches straight into the
 * corresponding built-in screen ([TracksListScreen] / [AlbumsTabScreen]
 * / [ArtistsTabScreen] / [GenresTabScreen]) with the saved
 * [FilterCriteria] applied via the screen's own `filter` parameter.
 *
 * Effect: custom tabs inherit the full chrome of the equivalent
 * built-in — alphabet rail, working sort, list↔tile toggle, multi-
 * select, the works — without any of it being re-implemented here.
 *
 * The previous (pre-D.30.2) `Filtered*` helpers in this file were a
 * parallel implementation that drifted: sort wasn't honoured by the
 * row layout, no alphabet rail, no multi-select. Replaced.
 */
@Composable
internal fun CustomTabContent(
  customTab: CustomTabEntity,
  // R.A.4 — each branch dispatches into one tab screen so we take all
  // four sources up-front. Smaller-blast-radius alternative (one
  // composite "LibrarySources" interface) was rejected per the brief:
  // "ISP isn't about minimizing parameter count, it's about minimizing
  // the surface a caller depends on".
  tracks: TrackSource,
  albums: AlbumSource,
  artists: ArtistSource,
  genres: GenreSource,
  settingsRepository: SettingsRepository,
  sort: TabSort,
  snapshot: SettingsSnapshot,
  viewMode: ViewMode,
  onTrackClick: (List<Track>, Int) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  onAddTracksToPlaylist: ((List<Long>) -> Unit)?,
  onDeleteTracks: ((List<Track>) -> Unit)?,
  onOpenAlbum: (name: String, albumArtist: String?) -> Unit,
  onOpenArtist: (name: String) -> Unit,
  onOpenGenre: (name: String) -> Unit,
  onComingSoon: (String) -> Unit,
) {
  val criteria = remember(customTab.criteriaJson) {
    FilterCriteria.fromJson(customTab.criteriaJson)
  }
  when (customTab.contentType) {
    CustomTabContentType.SONGS -> TracksListScreen(
      repository = tracks,
      sort = sort,
      intelligentSorting = snapshot.intelligentSorting,
      filter = criteria,
      viewMode = viewMode,
      albumCoversMode = snapshot.albumCoversMode,
      onTrackClick = onTrackClick,
      onAddToQueue = onAddToQueue,
      onAddToPlaylist = onAddToPlaylist,
      onAddTracksToPlaylist = onAddTracksToPlaylist,
      onGoToAlbum = { t ->
        onOpenAlbum(
          t.album ?: return@TracksListScreen,
          t.albumArtist ?: t.artist,
        )
      },
      onGoToArtist = { t ->
        onOpenArtist(
          (t.albumArtist?.takeIf { it.isNotBlank() } ?: t.artist) ?: return@TracksListScreen,
        )
      },
      onComingSoon = onComingSoon,
      onDeleteTracks = onDeleteTracks,
    )
    CustomTabContentType.ALBUMS -> AlbumsTabScreen(
      repository = albums,
      sort = sort,
      intelligentSorting = snapshot.intelligentSorting,
      forceSquare = snapshot.forceSquareCovers,
      albumCoversMode = snapshot.albumCoversMode,
      viewMode = viewMode,
      filter = criteria,
      onAlbumClick = { a -> onOpenAlbum(a.name, a.artist) },
    )
    CustomTabContentType.ARTISTS -> ArtistsTabScreen(
      repository = artists,
      settingsRepository = settingsRepository,
      sort = sort,
      intelligentSorting = snapshot.intelligentSorting,
      viewMode = viewMode,
      filter = criteria,
      onArtistClick = { a -> onOpenArtist(a.name) },
    )
    CustomTabContentType.GENRES -> GenresTabScreen(
      repository = genres,
      sort = sort,
      viewMode = viewMode,
      filter = criteria,
      onGenreClick = { g -> onOpenGenre(g.name) },
    )
  }
}
