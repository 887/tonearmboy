package com.eight87.tonearmboy.ui.nav.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.eight87.tonearmboy.ui.nav.LocalSectionTitle
import com.eight87.tonearmboy.ui.nav.RouteScope
import com.eight87.tonearmboy.ui.nav.Search
import com.eight87.tonearmboy.ui.search.SearchScreen

/**
 * R.E.2 — `Register` extension for the [Search] destination.
 *
 * `NowPlaying` no longer has a `Register` extension. As of G+ it's not
 * a nav route — it's a draggable sheet rendered at the app root, opened
 * via [RouteScope.onOpenNowPlayingSheet] from anywhere.
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
        onOpenNowPlayingSheet()
      },
      onBack = { backStack.pop() },
    )
  }
}
