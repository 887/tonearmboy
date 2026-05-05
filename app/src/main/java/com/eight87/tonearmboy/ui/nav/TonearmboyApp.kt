package com.eight87.tonearmboy.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.eight87.tonearmboy.AppGraph
import com.eight87.tonearmboy.playback.PlaybackService
import com.eight87.tonearmboy.ui.nav.routes.Register
import com.eight87.tonearmboy.ui.playing.MiniPlayer
import com.eight87.tonearmboy.ui.playing.NowPlayingScreen
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.CustomBarAction
import com.eight87.tonearmboy.ui.settings.MusicSourcesDialog
import com.eight87.tonearmboy.ui.settings.catalog.LocalHighlightedSettingId
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * R.E.3 — root composable. Builds [RouteScope], dispatches each
 * destination through its `Register(scope)` extension. Per-destination
 * data plumbing lives in `routes/{Library,Playing,Settings}Routes.kt`.
 *
 * G+ — NowPlaying is no longer a nav route. It's an overlay sheet
 * rendered above the library at the app root, controlled by a shared
 * `Animatable<Float>` (0 = collapsed, mini-player visible; 1 = fully
 * expanded, NowPlaying covers the library). The mini-player forwards
 * vertical drags directly to the sheet progress, and a
 * [NestedScrollConnection] on the open sheet drains overscroll-down
 * back into the sheet progress so the queue's pull-down-from-top
 * keeps the same continuous-drag feel as the open / close gesture.
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
  val playback = graph.playbackUiController
  val playbackState by playback.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val sectionTitle = remember { mutableStateOf("tonearmboy") }
  val highlightedSettingId = remember { mutableStateOf<String?>(null) }
  var showMusicSourcesDialog by remember { mutableStateOf(false) }

  // G+ — sheet progress (0..1). Owned at the app root so the mini-player,
  // the sheet overlay, the back-handler, and the deeplink reactor all
  // drive the same value.
  val sheetProgress = remember { Animatable(0f) }
  val coroutineScope = rememberCoroutineScope()

  // R.E.6 — settings → playback mirrors.
  PlaybackSettingsBridge(playback, playback, graph.settingsRepository)

  // D.20.1 — react to a notification deeplink: animate the sheet open.
  LaunchedEffect(deeplinkNonce, pendingDeeplink) {
    if (pendingDeeplink == PlaybackService.DEEPLINK_NOW_PLAYING) {
      sheetProgress.animateTo(1f)
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

  val openNowPlayingSheet: () -> Unit = remember {
    { coroutineScope.launch { sheetProgress.animateTo(1f) }; Unit }
  }
  val closeSheet: () -> Unit = remember {
    { coroutineScope.launch { sheetProgress.animateTo(0f) }; Unit }
  }

  val scope = rememberRouteScope(
    graph = graph,
    backStack = backStack,
    snackbar = snackbar,
    showMusicSourcesDialog = { showMusicSourcesDialog = true },
    openNowPlayingSheet = openNowPlayingSheet,
  )

  val showMiniPlayer = playbackState.hasMedia

  // BackHandler: when the sheet is open, back collapses it instead
  // of popping the underlying library back-stack.
  BackHandler(enabled = sheetProgress.value > 0f) {
    closeSheet()
  }

  CompositionLocalProvider(
    LocalSectionTitle provides sectionTitle,
    LocalHighlightedSettingId provides highlightedSettingId,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val sheetTravelPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

      // Drag-delta forwarder for the mini-player. delta < 0 means
      // user dragged up by |delta| px, which raises the sheet by
      // |delta|/sheetTravelPx of progress.
      val onSheetDragDelta: (Float) -> Unit = { delta ->
        coroutineScope.launch {
          val next = (sheetProgress.value - delta / sheetTravelPx).coerceIn(0f, 1f)
          sheetProgress.snapTo(next)
        }
      }
      val onSheetDragSettle: () -> Unit = {
        coroutineScope.launch {
          sheetProgress.animateTo(if (sheetProgress.value >= 0.5f) 1f else 0f)
        }
      }

      // ---- Layer 1: library + mini-player (always under the sheet) ----
      // Mini-player's alpha tracks (1 - progress) so it fades out as
      // the sheet rises — otherwise the bottom of the screen would
      // double-render the same controls (sheet's NowPlaying header
      // landing on top of the mini-player's row). When fully expanded,
      // mini-player is fully transparent + non-interactive.
      val miniPlayerAlpha = (1f - sheetProgress.value).coerceIn(0f, 1f)
      Scaffold(
        bottomBar = {
          if (showMiniPlayer) {
            Box(modifier = Modifier.alpha(miniPlayerAlpha)) {
              MiniPlayer(
                state = playbackState,
                onTogglePlayPause = playback::togglePlayPause,
                onClose = playback::stop,
                onExpand = openNowPlayingSheet,
                onSkipNext = playback::seekToNext,
                onSkipPrevious = playback::seekToPrevious,
                onPlayButtonLongPress = { playback.performCustomBarAction(customBarAction) },
                onToggleShuffle = playback::toggleShuffle,
                onCycleRepeat = playback::cycleRepeatMode,
                onSeekTo = playback::seekTo,
                albumCoversMode = albumCoversMode,
                onSheetDragDelta = onSheetDragDelta,
                onSheetDragSettle = onSheetDragSettle,
              )
            }
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
            entry<PlaylistDetail> { it.Register(scope) }
            entry<PlaylistTrackPicker> { it.Register(scope) }
            entry<CustomTabEditor> { it.Register(scope) }
            entry<SettingsRootDest> { it.Register(scope) }
            entry<SettingsAbout> { it.Register(scope) }
            entry<SettingsLicenses> { it.Register(scope) }
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

      // ---- Layer 2: NowPlaying sheet (rises over the library) ----
      // The sheet renders only when there is media to display, and is
      // translated by `(1 - progress) * sheetTravelPx` so it slides in
      // from the bottom. Alpha tracks progress so it crossfades over
      // the mini-player + library beneath. Pointer-input is gated on
      // a non-trivial progress so taps fall through to the mini-player
      // while the sheet is fully closed.
      if (playbackState.hasMedia) {
        val progress = sheetProgress.value
        val sheetIsInteractive = progress > 0.01f
        // NestedScrollConnection drains over-scroll-down (e.g. queue
        // already at top, user keeps pulling) into sheet progress so
        // the queue's drag-from-top continues the same gesture.
        val sheetNestedScroll = remember(sheetTravelPx) {
          object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
              // Scrolling up while sheet is partially closed: re-open
              // the sheet by consuming the upward delta first.
              if (available.y < 0f && sheetProgress.value < 1f) {
                val delta = -available.y / sheetTravelPx
                coroutineScope.launch {
                  sheetProgress.snapTo((sheetProgress.value + delta).coerceAtMost(1f))
                }
                return Offset(0f, available.y)
              }
              return Offset.Zero
            }
            override fun onPostScroll(
              consumed: Offset,
              available: Offset,
              source: NestedScrollSource,
            ): Offset {
              if (available.y > 0f && source == NestedScrollSource.UserInput) {
                val delta = available.y / sheetTravelPx
                coroutineScope.launch {
                  sheetProgress.snapTo((sheetProgress.value - delta).coerceAtLeast(0f))
                }
                return Offset(0f, available.y)
              }
              return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
              sheetProgress.animateTo(if (sheetProgress.value >= 0.5f) 1f else 0f)
              return Velocity.Zero
            }
          }
        }

        // The sheet is always mounted (first-mount cost amortizes off
        // the user-visible drag) but translated below the screen when
        // closed, so pointer events at progress=0 fall through to the
        // mini-player + library beneath. The sheet renders SOLID — no
        // alpha crossfade. As the user drags up, the sheet rises from
        // the bottom edge of the screen as a curtain; its top reveals
        // the NowPlaying screen's top (TopAppBar + cover) progressively
        // covering the library above. NestedScroll is only attached
        // while interactive so closed-state queue drags don't bleed
        // into sheet progress.
        Box(
          modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
              val placeable = measurable.measure(constraints)
              layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, ((1f - progress) * sheetTravelPx).roundToInt())
              }
            }
            .then(
              if (sheetIsInteractive) Modifier.nestedScroll(sheetNestedScroll)
              else Modifier,
            ),
        ) {
          NowPlayingScreen(
            nowPlayingState = playback,
            transport = playback,
            queueCommands = playback,
            onBack = closeSheet,
            albumCoversMode = albumCoversMode,
            onSaveQueueAsPlaylist = { mediaIds ->
              val trackIds = mediaIds.mapNotNull { it.toLongOrNull() }
              scope.addToPlaylist.requestBulk(trackIds)
            },
          )
        }
      }
    }
  }
}
