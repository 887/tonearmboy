package com.eight87.tonearm.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destinations for tonearm.
 *
 * - [TopLevelDestination] is the marker interface for entries that show
 *   up in the bottom navigation bar (Home, Library, Search, Settings).
 *   Each has its own back stack via [TonearmBackStack].
 * - [Detail] entries are pushed onto the currently selected tab's stack.
 *   They include `NowPlaying` (the full-screen player) and
 *   `PlaylistDetail` (one playlist's track list).
 *
 * All keys are `@Serializable` because [androidx.navigation3.runtime.rememberNavBackStack]
 * round-trips the stack through `SavedStateHandle` for config-change /
 * process-death survival, which requires kotlinx.serialization support.
 */
sealed interface Destination : NavKey

sealed interface TopLevelDestination : Destination {
  val icon: ImageVector
  val label: String
}

@Serializable
data object Home : TopLevelDestination {
  override val icon: ImageVector get() = Icons.Filled.Home
  override val label: String get() = "Home"
}

@Serializable
data object Library : TopLevelDestination {
  override val icon: ImageVector get() = Icons.Filled.LibraryMusic
  override val label: String get() = "Library"
}

@Serializable
data object Search : TopLevelDestination {
  override val icon: ImageVector get() = Icons.Filled.Search
  override val label: String get() = "Search"
}

@Serializable
data object Settings : TopLevelDestination {
  override val icon: ImageVector get() = Icons.Filled.Settings
  override val label: String get() = "Settings"
}

/** Full-screen now-playing surface (also reachable from the mini-player). */
@Serializable
data object NowPlaying : Destination

/** Detail screen for a single user playlist. */
@Serializable
data class PlaylistDetail(val playlistId: Long) : Destination

/** Canonical ordering for the bottom navigation bar. */
val TopLevelDestinations: List<TopLevelDestination> = listOf(Home, Library, Search, Settings)
