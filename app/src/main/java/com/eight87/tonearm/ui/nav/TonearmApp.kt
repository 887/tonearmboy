package com.eight87.tonearm.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import com.eight87.tonearm.AppGraph
import com.eight87.tonearm.ui.home.HomeScreen
import com.eight87.tonearm.ui.library.LibraryScreen
import com.eight87.tonearm.ui.library.PlaylistDetailScreen
import com.eight87.tonearm.ui.playing.MiniPlayer
import com.eight87.tonearm.ui.playing.NowPlayingScreen
import com.eight87.tonearm.ui.search.SearchScreen
import com.eight87.tonearm.ui.settings.SettingsScreen

/**
 * Root composable: bottom-nav `Scaffold` + Navigation 3 [NavDisplay].
 *
 * Per the official `navigation-3` skill's "Common UI" recipe, each
 * top-level destination owns its own back stack via [TonearmBackStack],
 * and detail keys (`NowPlaying`, `PlaylistDetail`) are pushed onto the
 * currently selected tab.
 *
 * The mini-player floats above the bottom nav whenever the controller
 * has media queued, except on the [NowPlaying] surface (where a full
 * player is already visible).
 */
@OptIn(UnstableApi::class)
@Composable
fun TonearmApp(graph: AppGraph) {
  val backStackHolder = remember { TonearmBackStack(Home) }
  val current = backStackHolder.backStack.lastOrNull() ?: Home

  val playback = graph.playbackUiController
  val playbackState by playback.state.collectAsStateWithLifecycle()

  // Keep the MediaController bound for the lifetime of the activity.
  // The full-screen NowPlaying re-uses this same connection.
  LaunchedEffect(Unit) { playback.connect() }

  val showBottomBar = current is TopLevelDestination
  val showMiniPlayer = playbackState.hasMedia && current !is com.eight87.tonearm.ui.nav.NowPlaying

  Scaffold(
    bottomBar = {
      if (showBottomBar) {
        Column {
          if (showMiniPlayer) {
            MiniPlayer(
              state = playbackState,
              onTogglePlayPause = playback::togglePlayPause,
              onClose = playback::stop,
              onExpand = { backStackHolder.push(NowPlaying) },
            )
          }
          BottomNav(backStackHolder)
        }
      } else if (showMiniPlayer && current !is com.eight87.tonearm.ui.nav.NowPlaying) {
        MiniPlayer(
          state = playbackState,
          onTogglePlayPause = playback::togglePlayPause,
          onClose = playback::stop,
          onExpand = { backStackHolder.push(NowPlaying) },
        )
      }
    },
  ) { innerPadding ->
    NavDisplay(
      backStack = backStackHolder.backStack,
      onBack = { backStackHolder.pop() },
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      entryProvider = entryProvider {
        entry<Home> {
          HomeScreen(
            onBrowseLibrary = { backStackHolder.switchTo(Library) },
            onOpenNowPlaying = { backStackHolder.push(NowPlaying) },
            playbackState = playbackState,
          )
        }
        entry<Library> {
          LibraryScreen(
            repository = graph.libraryRepository,
            onTrackClick = { tracks, index ->
              playback.playQueue(tracks, index)
              backStackHolder.push(NowPlaying)
            },
            onPlaylistClick = { id -> backStackHolder.push(PlaylistDetail(id)) },
          )
        }
        entry<Search> {
          SearchScreen(
            repository = graph.libraryRepository,
            onTrackClick = { track ->
              playback.playTrack(track)
              backStackHolder.push(NowPlaying)
            },
          )
        }
        entry<Settings> {
          SettingsScreen(
            store = graph.themePreferenceStore,
            onRescan = {
              graph.applicationScope.launch { graph.libraryRepository.rescanNow() }
            },
          )
        }
        entry<NowPlaying> {
          NowPlayingScreen(
            playback = playback,
            onBack = { backStackHolder.pop() },
          )
        }
        entry<PlaylistDetail> { key ->
          PlaylistDetailScreen(
            repository = graph.libraryRepository,
            playlistId = key.playlistId,
            onTrackClick = { tracks, index ->
              playback.playQueue(tracks, index)
              backStackHolder.push(NowPlaying)
            },
            onBack = { backStackHolder.pop() },
          )
        }
      },
    )
  }
}

@Composable
private fun BottomNav(backStackHolder: TonearmBackStack) {
  NavigationBar {
    TopLevelDestinations.forEach { dest ->
      NavigationBarItem(
        selected = backStackHolder.isSelected(dest),
        onClick = { backStackHolder.switchTo(dest) },
        icon = { Icon(imageVector = dest.icon, contentDescription = dest.label) },
        label = { Text(dest.label) },
      )
    }
  }
}
