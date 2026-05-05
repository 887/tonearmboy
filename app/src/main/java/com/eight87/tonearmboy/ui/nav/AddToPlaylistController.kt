package com.eight87.tonearmboy.ui.nav

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.PlaylistStore
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.library.PlaylistPickerSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * R.E.5 — Add-to-playlist surface lifted out of [TonearmboyApp].
 *
 * Two flows, one sheet:
 * - **single-track**: any track row's "Add to playlist…" action calls
 *   [requestSingle] with the track; the sheet appears on top of
 *   whichever destination is currently rendering.
 * - **bulk**: the multi-select bar's "Add to playlist" icon calls
 *   [requestBulk] with the snapshot of selected ids.
 *
 * Both branches surface the same [PlaylistPickerSheet]; the controller
 * picks the right onPick semantics based on which slot is non-null.
 * Snackbar copy adapts to single vs bulk.
 */
@Stable
interface AddToPlaylistController {
  fun requestSingle(track: Track)
  fun requestBulk(ids: List<Long>)

  @Composable
  fun Overlay()
}

@Composable
fun rememberAddToPlaylistController(
  playlists: PlaylistStore,
  snackbar: SnackbarHostState,
  applicationScope: CoroutineScope,
): AddToPlaylistController {
  var pendingTrack by remember { mutableStateOf<Track?>(null) }
  var pendingIds by remember { mutableStateOf<List<Long>?>(null) }
  val playlistList by playlists.observePlaylists()
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val context = LocalContext.current
  val resources = context.resources

  return remember(playlists, snackbar, applicationScope, context) {
    object : AddToPlaylistController {
      override fun requestSingle(track: Track) { pendingTrack = track }
      override fun requestBulk(ids: List<Long>) {
        if (ids.isNotEmpty()) pendingIds = ids
      }

      @Composable
      override fun Overlay() {
        pendingTrack?.let { track ->
          PlaylistPickerSheet(
            playlists = playlistList,
            onDismiss = { pendingTrack = null },
            onPick = { p ->
              pendingTrack = null
              applicationScope.launch {
                playlists.addTrackToPlaylist(p.id, track.id)
                snackbar.showSnackbar(
                  context.getString(R.string.playlist_added_track, track.title, p.name),
                )
              }
            },
            onCreateAndPick = { name ->
              pendingTrack = null
              applicationScope.launch {
                val id = playlists.createPlaylist(name)
                playlists.addTrackToPlaylist(id, track.id)
                snackbar.showSnackbar(
                  context.getString(R.string.playlist_added_track, track.title, name),
                )
              }
            },
          )
        }
        pendingIds?.let { ids ->
          PlaylistPickerSheet(
            playlists = playlistList,
            onDismiss = { pendingIds = null },
            onPick = { p ->
              pendingIds = null
              applicationScope.launch {
                ids.forEach { tid -> playlists.addTrackToPlaylist(p.id, tid) }
                snackbar.showSnackbar(
                  resources.getQuantityString(
                    R.plurals.playlist_added_tracks_count, ids.size, ids.size, p.name,
                  ),
                )
              }
            },
            onCreateAndPick = { name ->
              pendingIds = null
              applicationScope.launch {
                val id = playlists.createPlaylist(name)
                ids.forEach { tid -> playlists.addTrackToPlaylist(id, tid) }
                snackbar.showSnackbar(
                  resources.getQuantityString(
                    R.plurals.playlist_added_tracks_count, ids.size, ids.size, name,
                  ),
                )
              }
            },
          )
        }
      }
    }
  }
}
