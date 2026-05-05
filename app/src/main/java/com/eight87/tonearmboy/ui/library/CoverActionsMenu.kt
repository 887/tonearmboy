package com.eight87.tonearmboy.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.AlbumCoverChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * album-art-rows R1/R3/R4/R5 — one cover-action menu fragment, four
 * call sites (track / album / artist / playlist rows + tiles +
 * multi-select bar).
 *
 * The four actions are the same shape across every entity kind: pin a
 * SAF URI ("Replace…"), trigger an online lookup ("Search MusicBrainz"),
 * write the intentionally-empty sentinel ("Set to no cover"), or drop
 * the override row entirely ("Reset to default"). Keeping the menu
 * fragment shared means a future fifth action lands in one place
 * instead of four.
 *
 * Reset only renders when the choice is non-`NoChoice` — there's no
 * row to delete otherwise, and the menu item would be a no-op.
 */
data class CoverActionHandlers(
  val onPickedUri: (uri: String) -> Unit,
  val onSearchOnline: () -> Unit,
  val onSetEmpty: () -> Unit,
  val onReset: () -> Unit,
  val showSearchOnline: Boolean = true,
  val showReset: Boolean = true,
)

/**
 * Render the four cover-action items inside a parent `DropdownMenu`.
 * Caller controls dismissal: each item invokes [onDismiss] before the
 * handler fires so the parent menu folds away cleanly.
 *
 * The `OpenDocument` launcher is owned by this composable so each call
 * site doesn't have to build its own — there's no harm in N launchers
 * across the screen since each fires only when its menu item is
 * tapped.
 */
@Composable
fun CoverActionsMenuItems(
  choice: AlbumCoverChoice,
  handlers: CoverActionHandlers,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val pickLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
      handlers.onPickedUri(uri.toString())
    }
  }

  HorizontalDivider()
  DropdownMenuItem(
    text = { Text(stringResource(R.string.library_cover_action_replace)) },
    onClick = {
      onDismiss()
      pickLauncher.launch(arrayOf("image/*"))
    },
  )
  if (handlers.showSearchOnline) {
    DropdownMenuItem(
      text = { Text(stringResource(R.string.library_cover_action_search)) },
      onClick = {
        onDismiss()
        handlers.onSearchOnline()
      },
    )
  }
  DropdownMenuItem(
    text = { Text(stringResource(R.string.library_cover_action_clear)) },
    onClick = {
      onDismiss()
      handlers.onSetEmpty()
    },
  )
  if (handlers.showReset && choice !is AlbumCoverChoice.NoChoice) {
    DropdownMenuItem(
      text = { Text(stringResource(R.string.library_cover_action_reset)) },
      onClick = {
        onDismiss()
        handlers.onReset()
      },
    )
  }
}

/**
 * Convenience: build [CoverActionHandlers] for a track row that calls
 * the [com.eight87.tonearmboy.data.TrackSource] cover methods directly.
 * The row only has to wire its own `onSearchOnline` (which currently
 * routes through MusicBrainz at the album level since MB recording
 * lookups are too noisy without an MBID hint — see
 * `albumart-rows.md` R1.4).
 */
@Composable
fun rememberTrackCoverActions(
  trackSource: com.eight87.tonearmboy.data.TrackSource,
  trackId: Long,
  onSearchOnline: () -> Unit,
): CoverActionHandlers {
  val scope: CoroutineScope = rememberCoroutineScope()
  return remember(trackSource, trackId, onSearchOnline) {
    CoverActionHandlers(
      onPickedUri = { uri -> scope.launch { trackSource.setTrackCoverUri(trackId, uri) } },
      onSearchOnline = onSearchOnline,
      onSetEmpty = { scope.launch { trackSource.clearTrackCoverIntentional(trackId) } },
      onReset = { scope.launch { trackSource.resetTrackCover(trackId) } },
    )
  }
}

@Composable
fun rememberArtistCoverActions(
  artistSource: com.eight87.tonearmboy.data.ArtistSource,
  artistName: String,
  onSearchOnline: () -> Unit,
): CoverActionHandlers {
  val scope: CoroutineScope = rememberCoroutineScope()
  return remember(artistSource, artistName, onSearchOnline) {
    CoverActionHandlers(
      onPickedUri = { uri -> scope.launch { artistSource.setArtistCoverUri(artistName, uri) } },
      onSearchOnline = onSearchOnline,
      onSetEmpty = { scope.launch { artistSource.clearArtistCoverIntentional(artistName) } },
      onReset = { scope.launch { artistSource.resetArtistCover(artistName) } },
    )
  }
}

@Composable
fun rememberAlbumCoverActions(
  albumSource: com.eight87.tonearmboy.data.AlbumSource,
  albumKey: String,
  onSearchOnline: () -> Unit,
): CoverActionHandlers {
  val scope: CoroutineScope = rememberCoroutineScope()
  return remember(albumSource, albumKey, onSearchOnline) {
    CoverActionHandlers(
      onPickedUri = { uri -> scope.launch { albumSource.setAlbumCoverUri(albumKey, uri) } },
      onSearchOnline = onSearchOnline,
      onSetEmpty = { scope.launch { albumSource.clearAlbumCoverIntentional(albumKey) } },
      onReset = { scope.launch { albumSource.resetAlbumCover(albumKey) } },
    )
  }
}

/**
 * Playlist variant — playlists are user constructs so there's no
 * MusicBrainz path. Reset clears the override on
 * [com.eight87.tonearmboy.data.PlaylistStore.setPlaylistCoverUri] by
 * passing `null`; "intentionally empty" maps to the same null because
 * the playlist tile already falls back to the first-track album-art
 * if there's no pinned cover.
 */
@Composable
fun rememberPlaylistCoverActions(
  playlistStore: com.eight87.tonearmboy.data.PlaylistStore,
  playlistId: Long,
): CoverActionHandlers {
  val scope: CoroutineScope = rememberCoroutineScope()
  return remember(playlistStore, playlistId) {
    CoverActionHandlers(
      onPickedUri = { uri -> scope.launch { playlistStore.setPlaylistCoverUri(playlistId, uri) } },
      onSearchOnline = {},
      onSetEmpty = { scope.launch { playlistStore.setPlaylistCoverUri(playlistId, null) } },
      onReset = { scope.launch { playlistStore.setPlaylistCoverUri(playlistId, null) } },
      showSearchOnline = false,
      showReset = false,
    )
  }
}
