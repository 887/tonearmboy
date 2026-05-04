package com.eight87.tonearmboy.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.eight87.tonearmboy.AppGraph
import com.eight87.tonearmboy.playback.PlaybackService
import com.eight87.tonearmboy.ui.nav.routes.Register
import com.eight87.tonearmboy.ui.playing.MiniPlayer
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.CustomBarAction
import com.eight87.tonearmboy.ui.settings.MusicSourcesDialog
import com.eight87.tonearmboy.ui.settings.catalog.LocalHighlightedSettingId

/**
 * R.E.3 — root composable. Builds [RouteScope], dispatches each
 * destination through its `Register(scope)` extension. Per-destination
 * data plumbing lives in `routes/{Library,Playing,Settings}Routes.kt`.
 */
@OptIn(UnstableApi::class)
@Composable
fun TonearmboyApp(
  graph: AppGraph,
  /** D.20.1 — bumped each time `MainActivity.handleIntent` receives a notification deeplink. */
  deeplinkNonce: Int = 0,
  pendingDeeplink: String? = null,
  onDeeplinkConsumed: () -> Unit = {},
) {
  val backStack = remember { TonearmboyBackStack(LibraryRoot) }
  val current = backStack.backStack.lastOrNull() ?: LibraryRoot
  val playback = graph.playbackUiController
  val playbackState by playback.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val sectionTitle = remember { mutableStateOf("tonearmboy") }
  val highlightedSettingId = remember { mutableStateOf<String?>(null) }
  var showMusicSourcesDialog by remember { mutableStateOf(false) }

  // R.E.6 — settings → playback mirrors.
  PlaybackSettingsBridge(playback, playback, graph.settingsRepository)

  // D.20.1 — react to a notification deeplink; popToFirstOrPush keeps the
  // back stack sane regardless of where in the stack NowPlaying lives.
  LaunchedEffect(deeplinkNonce, pendingDeeplink) {
    if (pendingDeeplink == PlaybackService.DEEPLINK_NOW_PLAYING) {
      if (current !is NowPlaying) backStack.popToFirstOrPush(NowPlaying)
      onDeeplinkConsumed()
    }
  }

  // Keep the MediaController bound for the lifetime of the activity.
  LaunchedEffect(Unit) {
    // D.9b.1 — give the controller a library handle for ReplayGain
    // album lookups before it sees the first track transition.
    playback.setLibrary(graph.tracks)
    playback.connect()
  }

  // MiniPlayer-only settings reads kept here; everything else lives on RouteScope.
  val customBarAction by graph.settingsRepository.customBarAction.flow
    .collectAsStateWithLifecycle(initialValue = CustomBarAction.Default)
  val albumCoversMode by graph.settingsRepository.albumCoversMode.flow
    .collectAsStateWithLifecycle(initialValue = AlbumCoversMode.Default)

  val scope = rememberRouteScope(
    graph = graph,
    backStack = backStack,
    snackbar = snackbar,
    showMusicSourcesDialog = { showMusicSourcesDialog = true },
  )

  val showMiniPlayer = playbackState.hasMedia && current !is NowPlaying

  CompositionLocalProvider(
    LocalSectionTitle provides sectionTitle,
    LocalHighlightedSettingId provides highlightedSettingId,
  ) {
    Scaffold(
      bottomBar = {
        if (showMiniPlayer) {
          MiniPlayer(
            state = playbackState,
            onTogglePlayPause = playback::togglePlayPause,
            onClose = playback::stop,
            onExpand = { backStack.push(NowPlaying) },
            onSkipNext = playback::seekToNext,
            onSkipPrevious = playback::seekToPrevious,
            onPlayButtonLongPress = { playback.performCustomBarAction(customBarAction) },
            onToggleShuffle = playback::toggleShuffle,
            onCycleRepeat = playback::cycleRepeatMode,
            onSeekTo = playback::seekTo,
            albumCoversMode = albumCoversMode,
          )
        }
      },
    ) { innerPadding ->
      NavDisplay(
        backStack = backStack.backStack,
        onBack = { backStack.pop() },
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        entryProvider = entryProvider {
          entry<LibraryRoot> { it.Register(scope) }
          entry<AlbumDetail> { it.Register(scope) }
          entry<ArtistDetail> { it.Register(scope) }
          entry<GenreDetail> { it.Register(scope) }
          entry<Search> { it.Register(scope) }
          entry<NowPlaying> { it.Register(scope) }
          entry<PlaylistDetail> { it.Register(scope) }
          entry<PlaylistTrackPicker> { it.Register(scope) }
          entry<CustomTabEditor> { it.Register(scope) }
          entry<SettingsRootDest> { it.Register(scope) }
          entry<SettingsAbout> { it.Register(scope) }
          entry<SettingsSearch> { it.Register(scope) }
          entry<SettingsLookAndFeel> { it.Register(scope) }
          entry<SettingsPersonalize> { it.Register(scope) }
          entry<SettingsContent> { it.Register(scope) }
          entry<SettingsMusicSources> { it.Register(scope) }
          entry<SettingsAudio> { it.Register(scope) }
        },
      )

      // R.E.4 / R.E.5 — global overlays rendered once at the app root.
      scope.addToPlaylist.Overlay()
      scope.playlistBackup.Overlay()

      // D.17.3 — Music sources dialog. Toggled from the Settings root row
      // (RowKind.OpenDialog) and from search results routed to SettingsMusicSources.
      if (showMusicSourcesDialog) {
        MusicSourcesDialog(
          settings = graph.settingsRepository,
          scanner = graph.scanner,
          onDismiss = { showMusicSourcesDialog = false },
        )
      }
    }
  }
}
