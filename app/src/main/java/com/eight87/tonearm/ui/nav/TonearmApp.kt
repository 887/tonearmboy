package com.eight87.tonearm.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
          SettingsScreen(
            onBack = { backStack.pop() },
            onLookAndFeel = { backStack.push(SettingsLookAndFeel) },
            onPersonalize = { backStack.push(SettingsPersonalize) },
            onContent = { backStack.push(SettingsContent) },
            onAudio = { backStack.push(SettingsAudio) },
            onMusicSources = { onComingSoon("Music sources") },
            onRefreshMusic = onRefreshMusic,
            onRescanMusic = onRescanMusic,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsLookAndFeel> {
          SettingsLookAndFeelScreen(
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsPersonalize> {
          SettingsPersonalizeScreen(
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsContent> {
          SettingsContentScreen(
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsAudio> {
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
