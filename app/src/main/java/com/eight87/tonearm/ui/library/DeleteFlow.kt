package com.eight87.tonearm.ui.library

import android.content.ContentUris
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.delete.DeleteRequest
import com.eight87.tonearm.data.delete.TrackDeleter
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.playback.PlaybackUiController
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Phase F — host the file-deletion flow at the app root.
 *
 * Returns a `(List<Track>) -> Unit` lambda; UI surfaces (track-row
 * "Delete file…", multi-select bar) call it to start the flow. The
 * [DeleteFlowHost] composable installs:
 *
 * - the confirm `AlertDialog` (so the user can back out before any
 *   system UI shows),
 * - the `StartIntentSenderForResult` launcher that drives the system
 *   consent dialog on API 29+,
 * - the post-delete cache invalidation + queue cleanup,
 * - the snackbar messages for every error path the spec calls out
 *   (Phase F.5).
 *
 * Hosted at the app root rather than per-screen because the consent
 * launcher needs to outlive a navigation pop (the user might tap
 * "Delete file…" from the album-detail screen, but the result comes
 * back asynchronously, sometimes after the screen has dismissed).
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberDeleteFlow(
  repository: LibraryRepository,
  playback: PlaybackUiController,
  snackbarHostState: SnackbarHostState,
  applicationScope: CoroutineScope,
  trackDeleter: TrackDeleter,
): (List<Track>) -> Unit {
  val state = remember { DeleteFlowState() }
  val scope = rememberCoroutineScope()

  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    val pending = state.pendingTracks
    state.pendingTracks = emptyList()
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      finalizeDelete(
        deleted = pending,
        repository = repository,
        playback = playback,
        snackbarHostState = snackbarHostState,
        applicationScope = applicationScope,
      )
    } else {
      applicationScope.launch {
        snackbarHostState.showSnackbar("Deletion cancelled.")
      }
    }
  }

  state.confirming?.let { tracks ->
    val title = if (tracks.size == 1) "Delete ${tracks.first().title}?"
    else "Delete ${tracks.size} tracks?"
    val body = if (tracks.size == 1)
      "This will permanently delete the file from your device."
    else
      "This will permanently delete ${tracks.size} files from your device."
    AlertDialog(
      onDismissRequest = { state.confirming = null },
      title = { Text(title, modifier = Modifier.semantics { testTag = "delete_dialog_title" }) },
      text = { Text(body) },
      confirmButton = {
        TextButton(
          onClick = {
            val toDelete = tracks
            state.confirming = null
            scope.launch {
              val uris = toDelete.map { trackUri(it) }
              val request = trackDeleter.requestDelete(uris)
              when (request) {
                is DeleteRequest.Immediate -> {
                  finalizeDelete(
                    deleted = toDelete,
                    repository = repository,
                    playback = playback,
                    snackbarHostState = snackbarHostState,
                    applicationScope = applicationScope,
                  )
                }
                is DeleteRequest.Consent -> {
                  state.pendingTracks = toDelete
                  val intentRequest = IntentSenderRequest
                    .Builder(request.intentSender)
                    .build()
                  runCatching { launcher.launch(intentRequest) }.onFailure { t ->
                    state.pendingTracks = emptyList()
                    snackbarHostState.showSnackbar(
                      "Couldn't delete: ${t.message ?: "unable to start consent dialog"}",
                    )
                  }
                }
                is DeleteRequest.Failure -> {
                  snackbarHostState.showSnackbar("Couldn't delete: ${request.reason}")
                }
              }
            }
          },
          modifier = Modifier.semantics { testTag = "delete_dialog_confirm" },
        ) { Text("Delete") }
      },
      dismissButton = {
        TextButton(
          onClick = { state.confirming = null },
          modifier = Modifier.semantics { testTag = "delete_dialog_cancel" },
        ) { Text("Cancel") }
      },
      modifier = Modifier.semantics { testTag = "delete_dialog" },
    )
  }

  return { tracks ->
    if (tracks.isNotEmpty()) state.confirming = tracks
  }
}

/**
 * Build the canonical MediaStore audio URI for a [Track]. `Track.id`
 * already carries the MediaStore primary key (assigned during the
 * scan), so we round-trip through [ContentUris.withAppendedId].
 */
internal fun trackUri(track: Track) =
  ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)

@OptIn(UnstableApi::class)
private fun finalizeDelete(
  deleted: List<Track>,
  repository: LibraryRepository,
  playback: PlaybackUiController,
  snackbarHostState: SnackbarHostState,
  applicationScope: CoroutineScope,
) {
  if (deleted.isEmpty()) return
  val uris = deleted.map { trackUri(it) }
  val mediaIds = deleted.map { it.id.toString() }.toSet()
  applicationScope.launch {
    repository.onTracksDeleted(uris)
    playback.removeQueueItemsByMediaIds(mediaIds)
    val message = if (deleted.size == 1) "Deleted ${deleted.first().title}"
    else "Deleted ${deleted.size} tracks"
    snackbarHostState.showSnackbar(message)
  }
}

private class DeleteFlowState {
  var confirming: List<Track>? by mutableStateOf(null)
  var pendingTracks: List<Track> = emptyList()
}
