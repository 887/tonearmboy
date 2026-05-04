package com.eight87.tonearmboy.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.AppGraph
import com.eight87.tonearmboy.data.FilterCriteria
import com.eight87.tonearmboy.data.delete.TrackDeleter
import com.eight87.tonearmboy.ui.library.rememberDeleteFlow
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.PlayFromItemDetails
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary
import kotlinx.coroutines.launch

/**
 * R.E.1 — Compose factory for the [RouteScope] handed to every
 * destination's `Register(scope)` extension. Hoists the cross-cutting
 * controllers and settings projections out of [TonearmboyApp] so the
 * top-level composable stays focused on its scaffold + dispatch role.
 *
 * The returned [RouteScope] also exposes `onShowMusicSourcesDialog`
 * which the host wires to its own `var showMusicSourcesDialog`.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberRouteScope(
  graph: AppGraph,
  backStack: TonearmboyBackStack,
  snackbar: SnackbarHostState,
  showMusicSourcesDialog: () -> Unit,
): RouteScope {
  val playback = graph.playbackUiController
  val playFromLibrary by graph.settingsRepository.playFromLibrary.flow
    .collectAsStateWithLifecycle(initialValue = PlayFromLibrary.Default)
  val playFromItemDetails by graph.settingsRepository.playFromItemDetails.flow
    .collectAsStateWithLifecycle(initialValue = PlayFromItemDetails.Default)
  val albumCoversMode by graph.settingsRepository.albumCoversMode.flow
    .collectAsStateWithLifecycle(initialValue = AlbumCoversMode.Default)

  val trackDeleter = remember { TrackDeleter(graph.applicationContextForUi) }
  val onDeleteTracks = rememberDeleteFlow(
    scanner = graph.scanner,
    playback = playback,
    snackbarHostState = snackbar,
    applicationScope = graph.applicationScope,
    trackDeleter = trackDeleter,
  )
  val playlistBackup = rememberPlaylistBackupController(
    playlists = graph.playlists,
    tracks = graph.tracks,
    snackbar = snackbar,
    applicationScope = graph.applicationScope,
  )
  val addToPlaylist = rememberAddToPlaylistController(
    playlists = graph.playlists,
    snackbar = snackbar,
    applicationScope = graph.applicationScope,
  )

  // D.27.5 — current library filter, owned at the app root so it
  // survives tab switches. Cleared on app close (no DataStore
  // persistence in v1, per the plan).
  var libraryFilter by remember { mutableStateOf(FilterCriteria()) }

  val onRefreshMusic: () -> Unit = {
    graph.applicationScope.launch {
      graph.scanner.rescanNow()
      snackbar.showSnackbar("Refreshed music library")
    }
  }
  val onRescanMusic: () -> Unit = {
    graph.applicationScope.launch {
      graph.scanner.rescanNow()
      snackbar.showSnackbar("Rescanned music library")
    }
  }
  val onComingSoon: (String) -> Unit = { feature ->
    graph.applicationScope.launch {
      snackbar.showSnackbar("$feature — coming in v1.1")
    }
  }

  return remember(graph, backStack, snackbar, addToPlaylist, playlistBackup) {
    object : RouteScope {
      override val graph = graph
      override val backStack = backStack
      override val snackbar = snackbar
      override val applicationScope = graph.applicationScope
      override val playback = playback
      override val playFromLibrary get() = playFromLibrary
      override val playFromItemDetails get() = playFromItemDetails
      override val albumCoversMode get() = albumCoversMode
      override val addToPlaylist = addToPlaylist
      override val playlistBackup = playlistBackup
      override val libraryFilter get() = libraryFilter
      override val onLibraryFilterChange: (FilterCriteria) -> Unit = { libraryFilter = it }
      override val onComingSoon = onComingSoon
      override val onDeleteTracks = onDeleteTracks
      override val onShowMusicSourcesDialog = showMusicSourcesDialog
      override val onRefreshMusic = onRefreshMusic
      override val onRescanMusic = onRescanMusic
    }
  }
}
