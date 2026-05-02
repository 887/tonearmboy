package com.eight87.tonearm.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.material3.Scaffold
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.eight87.tonearm.AppGraph
import com.eight87.tonearm.ui.library.LibraryScreen
import com.eight87.tonearm.ui.library.PlaylistDetailScreen
import com.eight87.tonearm.ui.playing.MiniPlayer
import com.eight87.tonearm.ui.playing.NowPlayingScreen
import com.eight87.tonearm.ui.search.SearchScreen
import com.eight87.tonearm.ui.settings.SettingsAudioScreen
import com.eight87.tonearm.ui.settings.SettingsContentScreen
import com.eight87.tonearm.ui.settings.SettingsLookAndFeelScreen
import com.eight87.tonearm.ui.settings.SettingsPersonalizeScreen
import com.eight87.tonearm.ui.settings.SettingsScreen
import com.eight87.tonearm.ui.settings.catalog.LocalHighlightedSettingId
import com.eight87.tonearm.ui.settings.catalog.SettingsSearchScreen
import kotlinx.coroutines.launch

/**
 * Root composable: a [NavDisplay] over a single back stack rooted at
 * [LibraryRoot]. There is **no bottom navigation**. The library is the
 * landing surface (via its own top tabs); search and settings are
 * pushed on top of it from the top app bar.
 *
 * The mini-player floats at the bottom of every destination except
 * [NowPlaying] (where the full player is already visible).
 */
@OptIn(UnstableApi::class)
@Composable
fun TonearmApp(graph: AppGraph) {
  val backStack = remember { TonearmBackStack(LibraryRoot) }
  val current = backStack.backStack.lastOrNull() ?: LibraryRoot

  val playback = graph.playbackUiController
  val playbackState by playback.state.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  // Single shared title cell driven by per-screen LaunchedEffects.
  val sectionTitle = remember { mutableStateOf("tonearm") }

  // Settings search seeds this state with the id of the row to flash
  // when navigating to its destination sub-page; the receiving row
  // animates a 300 ms background colour change and then clears it.
  val highlightedSettingId = remember { mutableStateOf<String?>(null) }

  // Keep the MediaController bound for the lifetime of the activity.
  // The full-screen NowPlaying re-uses this same connection.
  LaunchedEffect(Unit) { playback.connect() }

  val showMiniPlayer = playbackState.hasMedia && current !is NowPlaying

  val onComingSoon: (String) -> Unit = { feature ->
    graph.applicationScope.launch {
      snackbarHostState.showSnackbar("$feature — coming in v1.1")
    }
  }
  val onRefreshMusic: () -> Unit = {
    graph.applicationScope.launch {
      graph.libraryRepository.rescanNow()
      snackbarHostState.showSnackbar("Refreshed music library")
    }
  }
  val onRescanMusic: () -> Unit = {
    graph.applicationScope.launch {
      graph.libraryRepository.rescanNow()
      snackbarHostState.showSnackbar("Rescanned music library")
    }
  }

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
        )
      }
    },
  ) { innerPadding ->
    NavDisplay(
      backStack = backStack.backStack,
      onBack = { backStack.pop() },
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      entryProvider = entryProvider {
        entry<LibraryRoot> {
          LibraryScreen(
            repository = graph.libraryRepository,
            settingsRepository = graph.settingsRepository,
            onTrackClick = { tracks, index ->
              playback.playQueue(tracks, index)
              backStack.push(NowPlaying)
            },
            onPlaylistClick = { id -> backStack.push(PlaylistDetail(id)) },
            onOpenSearch = { backStack.push(Search) },
            onOpenSettings = { backStack.push(SettingsRootDest) },
            onRefreshMusic = onRefreshMusic,
            onRescanMusic = onRescanMusic,
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<Search> {
          LaunchedEffect(Unit) { sectionTitle.value = "Search" }
          SearchScreen(
            repository = graph.libraryRepository,
            onTrackClick = { track ->
              playback.playTrack(track)
              backStack.push(NowPlaying)
            },
            onBack = { backStack.pop() },
          )
        }
        entry<NowPlaying> {
          LaunchedEffect(Unit) { sectionTitle.value = "Now Playing" }
          NowPlayingScreen(
            playback = playback,
            onBack = { backStack.pop() },
          )
        }
        entry<PlaylistDetail> { key ->
          PlaylistDetailScreen(
            repository = graph.libraryRepository,
            playlistId = key.playlistId,
            onTrackClick = { tracks, index ->
              playback.playQueue(tracks, index)
              backStack.push(NowPlaying)
            },
            onBack = { backStack.pop() },
          )
        }
        entry<SettingsRootDest> {
          LaunchedEffect(Unit) { sectionTitle.value = "Settings" }
          SettingsScreen(
            onBack = { backStack.pop() },
            onLookAndFeel = { backStack.push(SettingsLookAndFeel) },
            onPersonalize = { backStack.push(SettingsPersonalize) },
            onContent = { backStack.push(SettingsContent) },
            onAudio = { backStack.push(SettingsAudio) },
            onMusicSources = { onComingSoon("Music sources") },
            onRefreshMusic = onRefreshMusic,
            onRescanMusic = onRescanMusic,
            onOpenSearch = { backStack.push(SettingsSearch) },
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsSearch> {
          LaunchedEffect(Unit) { sectionTitle.value = "Search settings" }
          SettingsSearchScreen(
            onBack = { backStack.pop() },
            onResult = { destination, id ->
              // Pop the search overlay, then push (or stay on) the
              // destination sub-page. Seed the highlight so the
              // matched row briefly flashes when it composes.
              highlightedSettingId.value = id
              backStack.pop()
              if (destination !is SettingsRootDest) {
                backStack.push(destination)
              }
            },
          )
        }
        entry<SettingsLookAndFeel> {
          LaunchedEffect(Unit) { sectionTitle.value = "Look and Feel" }
          SettingsLookAndFeelScreen(
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsPersonalize> {
          LaunchedEffect(Unit) { sectionTitle.value = "Personalize" }
          SettingsPersonalizeScreen(
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsContent> {
          LaunchedEffect(Unit) { sectionTitle.value = "Content" }
          SettingsContentScreen(
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsAudio> {
          LaunchedEffect(Unit) { sectionTitle.value = "Audio" }
          SettingsAudioScreen(
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
      },
    )
  }
  }
}
