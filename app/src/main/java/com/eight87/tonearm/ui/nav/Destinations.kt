package com.eight87.tonearm.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destinations for tonearm.
 *
 * The app has **one** root destination — [LibraryRoot] — which renders
 * the library top-tabs (Songs / Albums / Artists / Genres / Playlists)
 * under a `TopAppBar`. Everything else is pushed onto the same single
 * stack: search, settings root + sub-pages, the now-playing surface,
 * and per-playlist detail. There is no bottom navigation; the
 * mini-player is the only persistent bottom UI element.
 *
 * All keys are `@Serializable` because [androidx.navigation3.runtime.rememberNavBackStack]
 * round-trips the stack through `SavedStateHandle` for config-change /
 * process-death survival, which requires kotlinx.serialization support.
 */
sealed interface Destination : NavKey

/** Root: library top-tabs under the `tonearm` top app bar. */
@Serializable
data object LibraryRoot : Destination

/** Search surface (entered from the top-bar search icon). */
@Serializable
data object Search : Destination

/** Full-screen now-playing surface (also reachable from the mini-player). */
@Serializable
data object NowPlaying : Destination

/** Detail screen for a single user playlist. */
@Serializable
data class PlaylistDetail(val playlistId: Long) : Destination

// --- Settings -------------------------------------------------------------

/** Settings root with the eight Auxio-style entries. */
@Serializable
data object SettingsRootDest : Destination

@Serializable
data object SettingsLookAndFeel : Destination

@Serializable
data object SettingsPersonalize : Destination

@Serializable
data object SettingsContent : Destination

@Serializable
data object SettingsAudio : Destination

/** Full-screen global settings search overlay (entered from the Settings root). */
@Serializable
data object SettingsSearch : Destination
