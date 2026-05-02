package com.eight87.tonearm.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.FilterCriteria
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.db.CustomTabContentType
import com.eight87.tonearm.data.db.CustomTabEntity
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.ui.settings.SettingsSnapshot
import com.eight87.tonearm.ui.settings.TabSort
import com.eight87.tonearm.ui.settings.catalog.SettingsDimens

/**
 * D.18.5 — content host for a user-defined library tab.
 *
 * Switches on [CustomTabEntity.contentType] and consumes the
 * corresponding `*Matching` Flow from [LibraryRepository] so the
 * rendered subset reflects the saved [FilterCriteria].
 *
 * Each branch is a slim wrapper around the equivalent built-in
 * screen — same row chrome, same overflow menu, same alphabet
 * scroller — but bound to the filtered Flow rather than the all-X
 * Flow.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CustomTabContent(
  customTab: CustomTabEntity,
  repository: LibraryRepository,
  sort: TabSort,
  snapshot: SettingsSnapshot,
  onTrackClick: (List<Track>, Int) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  onOpenAlbum: (name: String, albumArtist: String?) -> Unit,
  onOpenArtist: (name: String) -> Unit,
  onOpenGenre: (name: String) -> Unit,
  onComingSoon: (String) -> Unit,
) {
  val criteria = remember(customTab.criteriaJson) { FilterCriteria.fromJson(customTab.criteriaJson) }
  when (customTab.contentType) {
    CustomTabContentType.SONGS -> FilteredTracks(
      repository = repository,
      criteria = criteria,
      sort = sort,
      snapshot = snapshot,
      onTrackClick = onTrackClick,
      onAddToQueue = onAddToQueue,
      onAddToPlaylist = onAddToPlaylist,
      onOpenAlbum = onOpenAlbum,
      onOpenArtist = onOpenArtist,
      onComingSoon = onComingSoon,
    )
    CustomTabContentType.ALBUMS -> FilteredAlbums(
      repository = repository,
      criteria = criteria,
      sort = sort,
      snapshot = snapshot,
      onAlbumClick = { a -> onOpenAlbum(a.name, a.artist) },
    )
    CustomTabContentType.ARTISTS -> FilteredArtists(
      repository = repository,
      criteria = criteria,
      sort = sort,
      snapshot = snapshot,
      onArtistClick = { a -> onOpenArtist(a.name) },
    )
    CustomTabContentType.GENRES -> FilteredGenres(
      repository = repository,
      criteria = criteria,
      sort = sort,
      onGenreClick = { g -> onOpenGenre(g.name) },
    )
  }
}

@Composable
private fun FilteredTracks(
  repository: LibraryRepository,
  criteria: FilterCriteria,
  sort: TabSort,
  snapshot: SettingsSnapshot,
  onTrackClick: (List<Track>, Int) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  onOpenAlbum: (name: String, albumArtist: String?) -> Unit,
  onOpenArtist: (name: String) -> Unit,
  onComingSoon: (String) -> Unit,
) {
  val tracks by repository.tracksMatching(criteria).collectAsState(initial = emptyList())
  if (tracks.isEmpty()) {
    EmptyCustomTabState(message = "No tracks match this custom tab.")
    return
  }
  val sorted = remember(tracks, sort, snapshot.intelligentSorting) {
    sortTracks(tracks, sort, snapshot.intelligentSorting)
  }
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .libraryListCard()
      .semantics { testTag = "custom_tracks_list" },
  ) {
    items(sorted, key = { it.id }) { track ->
      val itemIndex = sorted.indexOf(track)
      // Slim track row matching the built-in TrackRow's chrome — kept
      // separate so we don't have to reach into LibraryScreen.kt's
      // privates. Same testTag and tap shape.
      androidx.compose.foundation.layout.Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp)
          .semantics { testTag = "custom_track_row" },
      ) {
        androidx.compose.material3.Text(
          text = track.title,
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp)
            .semantics { testTag = "custom_track_title_${track.id}" },
        )
        androidx.compose.material3.TextButton(onClick = { onTrackClick(sorted, itemIndex) }) {
          androidx.compose.material3.Text("Play")
        }
      }
      androidx.compose.material3.HorizontalDivider()
    }
  }
}

@Composable
private fun FilteredAlbums(
  repository: LibraryRepository,
  criteria: FilterCriteria,
  sort: TabSort,
  snapshot: SettingsSnapshot,
  onAlbumClick: (com.eight87.tonearm.data.model.Album) -> Unit,
) {
  val albums by repository.albumsMatching(criteria).collectAsState(initial = emptyList())
  if (albums.isEmpty()) {
    EmptyCustomTabState(message = "No albums match this custom tab.")
    return
  }
  val sorted = remember(albums, sort, snapshot.intelligentSorting) {
    sortAlbums(albums, sort, snapshot.intelligentSorting)
  }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 140.dp),
    contentPadding = PaddingValues(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = SettingsDimens.PagePadding)
      .semantics { testTag = "custom_albums_grid" },
  ) {
    items(sorted, key = { it.id }) { album ->
      androidx.compose.foundation.layout.Column(
        modifier = Modifier
          .padding(4.dp)
          .semantics { testTag = "custom_album_cell" },
      ) {
        val shape = if (snapshot.forceSquareCovers) RoundedCornerShape(0.dp) else RoundedCornerShape(8.dp)
        CoverArt(
          albumId = album.mediaStoreAlbumId,
          size = 48.dp,
          mode = snapshot.albumCoversMode,
          contentDescription = album.name,
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape),
        )
        androidx.compose.material3.Text(
          text = album.name,
          modifier = Modifier
            .padding(top = 6.dp)
            .semantics { testTag = "custom_album_name_${album.id}" }
            .fillMaxWidth(),
        )
        androidx.compose.material3.Text(
          text = album.artist ?: "Unknown artist",
          modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.material3.TextButton(onClick = { onAlbumClick(album) }) {
          androidx.compose.material3.Text("Open")
        }
      }
    }
  }
}

@Composable
private fun FilteredArtists(
  repository: LibraryRepository,
  criteria: FilterCriteria,
  sort: TabSort,
  snapshot: SettingsSnapshot,
  onArtistClick: (com.eight87.tonearm.data.model.Artist) -> Unit,
) {
  val artists by repository.artistsMatching(criteria).collectAsState(initial = emptyList())
  if (artists.isEmpty()) {
    EmptyCustomTabState(message = "No artists match this custom tab.")
    return
  }
  val sorted = remember(artists, sort, snapshot.intelligentSorting) {
    sortArtists(artists, sort, snapshot.intelligentSorting)
  }
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .libraryListCard()
      .semantics { testTag = "custom_artists_list" },
  ) {
    items(sorted, key = { it.id }) { a ->
      androidx.compose.foundation.layout.Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp)
          .semantics { testTag = "custom_artist_row_${a.id}" },
      ) {
        androidx.compose.material3.Text(a.name)
        androidx.compose.material3.Text("${a.albumCount} albums · ${a.trackCount} tracks")
      }
      androidx.compose.material3.HorizontalDivider()
      androidx.compose.material3.TextButton(onClick = { onArtistClick(a) }) {
        androidx.compose.material3.Text("Open")
      }
    }
  }
}

@Composable
private fun FilteredGenres(
  repository: LibraryRepository,
  criteria: FilterCriteria,
  sort: TabSort,
  onGenreClick: (com.eight87.tonearm.data.model.Genre) -> Unit,
) {
  val genres by repository.genresMatching(criteria).collectAsState(initial = emptyList())
  if (genres.isEmpty()) {
    EmptyCustomTabState(message = "No genres match this custom tab.")
    return
  }
  val sorted = remember(genres, sort) { sortGenres(genres, sort) }
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .libraryListCard()
      .semantics { testTag = "custom_genres_list" },
  ) {
    items(sorted, key = { it.id }) { g ->
      androidx.compose.foundation.layout.Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp)
          .semantics { testTag = "custom_genre_row_${g.id}" },
      ) {
        androidx.compose.material3.Text(g.name)
        androidx.compose.material3.Text("${g.trackCount} tracks")
      }
      androidx.compose.material3.HorizontalDivider()
      androidx.compose.material3.TextButton(onClick = { onGenreClick(g) }) {
        androidx.compose.material3.Text("Open")
      }
    }
  }
}

@Composable
private fun EmptyCustomTabState(message: String) {
  androidx.compose.foundation.layout.Box(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    contentAlignment = androidx.compose.ui.Alignment.Center,
  ) {
    Text(
      text = message,
      modifier = Modifier.semantics { testTag = "custom_empty_state" },
    )
  }
}
