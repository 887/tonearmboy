package com.eight87.tonearmboy.ui.nav.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.ui.nav.LocalSectionTitle
import com.eight87.tonearmboy.ui.nav.NowPlaying
import com.eight87.tonearmboy.ui.nav.RouteScope
import com.eight87.tonearmboy.ui.nav.Search
import com.eight87.tonearmboy.ui.playing.NowPlayingScreen
import com.eight87.tonearmboy.ui.search.SearchScreen

/**
 * R.E.2 — `Register` extensions for the [Search] and [NowPlaying]
 * destinations.
 */

@Composable
fun Search.Register(scope: RouteScope) {
  val sectionTitle = LocalSectionTitle.current
  LaunchedEffect(Unit) { sectionTitle.value = "Search" }
  with(scope) {
    SearchScreen(
      repository = graph.tracks,
      onTrackClick = { track ->
        playback.playTrack(track)
        backStack.push(NowPlaying)
      },
      onBack = { backStack.pop() },
    )
  }
}

@OptIn(UnstableApi::class)
@Composable
fun NowPlaying.Register(scope: RouteScope) {
  val sectionTitle = LocalSectionTitle.current
  LaunchedEffect(Unit) { sectionTitle.value = "Now Playing" }
  with(scope) {
    NowPlayingScreen(
      nowPlayingState = playback,
      transport = playback,
      queueCommands = playback,
      onBack = { backStack.pop() },
      albumCoversMode = albumCoversMode,
      onSaveQueueAsPlaylist = { mediaIds ->
        // D.29.1 — feed the queue's track ids into the same bulk
        // playlist-picker overlay multi-select uses, so the user
        // can append to an existing playlist or create a new one.
        val trackIds = mediaIds.mapNotNull { it.toLongOrNull() }
        addToPlaylist.requestBulk(trackIds)
      },
    )
  }
}
