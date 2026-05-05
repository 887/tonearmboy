package com.eight87.tonearmboy.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.AppGraph
import com.eight87.tonearmboy.data.FilterCriteria
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.playback.PlaybackUiController
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.PlayFromItemDetails
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary
import kotlinx.coroutines.CoroutineScope

/**
 * R.E.1 — bundle of dependencies handed to every per-destination
 * [com.eight87.tonearmboy.ui.nav.Destination].`Register(scope)` extension.
 *
 * Replaces the "every entry block reaches into TonearmboyApp's locals"
 * pattern. With a single scope per render pass, adding a new destination
 * is one new file with one new `Register` extension — the navigation
 * dispatcher in [TonearmboyApp] is closed against modification.
 *
 * Convention:
 * - one-shot navigation actions (push/pop/popToFirstOrPush) come off [backStack].
 * - cross-cutting overlays (add-to-playlist, playlist-import collisions)
 *   are exposed as their own controllers ([addToPlaylist], [playlistBackup]).
 * - settings reads are projected into plain values ([albumCoversMode],
 *   [playFromLibrary], etc.) — Register implementations don't need to
 *   pull a `collectAsStateWithLifecycle` themselves.
 */
@OptIn(UnstableApi::class)
interface RouteScope {
  val graph: AppGraph
  val backStack: TonearmboyBackStack
  val snackbar: SnackbarHostState
  val applicationScope: CoroutineScope
  val playback: PlaybackUiController

  // Settings projections (point-in-time values for the current render).
  val playFromLibrary: PlayFromLibrary
  val playFromItemDetails: PlayFromItemDetails
  val albumCoversMode: AlbumCoversMode

  // Cross-cutting overlay controllers.
  val addToPlaylist: AddToPlaylistController
  val playlistBackup: PlaylistBackupController

  // App-root state shared across destinations.
  // (sectionTitle + highlightedSettingId come via CompositionLocals.)
  val libraryFilter: FilterCriteria
  val onLibraryFilterChange: (FilterCriteria) -> Unit

  // Side-effect callbacks routed back to the host.
  val onComingSoon: (String) -> Unit
  val onDeleteTracks: (List<Track>) -> Unit
  val onShowMusicSourcesDialog: () -> Unit
  val onRefreshMusic: () -> Unit
  val onRescanMusic: () -> Unit
  /** album-art Phase C — drop Coil's caches so the next CoverArt
   *  render reloads from disk (covers / pinned overrides). */
  val onRefreshAlbumArt: () -> Unit

  /**
   * Open the NowPlaying sheet (animate sheet progress 0 → 1). Replaces
   * the pre-G+ pattern of `backStack.push(NowPlaying)` — NowPlaying is
   * no longer a nav route, it's an overlay rendered above the library.
   */
  val onOpenNowPlayingSheet: () -> Unit
}
