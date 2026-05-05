package com.eight87.tonearmboy.ui.library

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.LibraryScanner
import com.eight87.tonearmboy.data.delete.DeleteRequest
import com.eight87.tonearmboy.data.delete.TrackDeleter
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.playback.PlaybackUiController
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
  scanner: LibraryScanner,
  playback: PlaybackUiController,
  snackbarHostState: SnackbarHostState,
  applicationScope: CoroutineScope,
  trackDeleter: TrackDeleter,
): (List<Track>) -> Unit {
  val state = remember { DeleteFlowState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val appContext = context.applicationContext

  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    val pending = state.pendingTracks
    state.pendingTracks = emptyList()
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      finalizeDelete(
        deleted = pending,
        scanner = scanner,
        playback = playback,
        snackbarHostState = snackbarHostState,
        applicationScope = applicationScope,
        context = appContext,
      )
    } else {
      applicationScope.launch {
        snackbarHostState.showSnackbar(appContext.getString(R.string.library_delete_snackbar_cancelled))
      }
    }
  }

  state.confirming?.let { tracks ->
    val title = if (tracks.size == 1) {
      stringResource(R.string.library_delete_dialog_title_single, tracks.first().title)
    } else {
      stringResource(R.string.library_delete_dialog_title_multi, tracks.size)
    }
    val body = if (tracks.size == 1) {
      stringResource(R.string.library_delete_dialog_body_single)
    } else {
      stringResource(R.string.library_delete_dialog_body_multi, tracks.size)
    }
    val unableToStartConsent = stringResource(R.string.library_delete_failure_consent_dialog)
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
                    scanner = scanner,
                    playback = playback,
                    snackbarHostState = snackbarHostState,
                    applicationScope = applicationScope,
                    context = appContext,
                  )
                }
                is DeleteRequest.Consent -> {
                  state.pendingTracks = toDelete
                  val intentRequest = IntentSenderRequest
                    .Builder(request.intentSender)
                    .build()
                  runCatching { launcher.launch(intentRequest) }.onFailure { t ->
                    state.pendingTracks = emptyList()
                    val reason = t.message ?: unableToStartConsent
                    snackbarHostState.showSnackbar(
                      appContext.getString(R.string.library_delete_snackbar_failure_with_reason, reason),
                    )
                  }
                }
                is DeleteRequest.Failure -> {
                  snackbarHostState.showSnackbar(
                    appContext.getString(R.string.library_delete_snackbar_failure_with_reason, request.reason),
                  )
                }
              }
            }
          },
          modifier = Modifier.semantics { testTag = "delete_dialog_confirm" },
        ) { Text(stringResource(R.string.library_delete_dialog_confirm)) }
      },
      dismissButton = {
        TextButton(
          onClick = { state.confirming = null },
          modifier = Modifier.semantics { testTag = "delete_dialog_cancel" },
        ) { Text(stringResource(R.string.library_delete_dialog_cancel)) }
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
  scanner: LibraryScanner,
  playback: PlaybackUiController,
  snackbarHostState: SnackbarHostState,
  applicationScope: CoroutineScope,
  context: android.content.Context,
) {
  if (deleted.isEmpty()) return
  val uris = deleted.map { trackUri(it) }
  val mediaIds = deleted.map { it.id.toString() }.toSet()
  applicationScope.launch {
    scanner.onTracksDeleted(uris)
    playback.removeQueueItemsByMediaIds(mediaIds)
    val message = if (deleted.size == 1) {
      context.getString(R.string.library_delete_snackbar_done_single, deleted.first().title)
    } else {
      context.getString(R.string.library_delete_snackbar_done_multi, deleted.size)
    }
    snackbarHostState.showSnackbar(message)
  }
}

private class DeleteFlowState {
  var confirming: List<Track>? by mutableStateOf(null)
  var pendingTracks: List<Track> = emptyList()
}
