package com.eight87.tonearmboy.ui.nav

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.eight87.tonearmboy.data.PlaylistStore
import com.eight87.tonearmboy.data.TrackSource
import com.eight87.tonearmboy.data.playlist.PlaylistBackupCodec
import com.eight87.tonearmboy.data.playlist.PlaylistBackupEnvelope
import com.eight87.tonearmboy.data.playlist.PlaylistImportCollisionPolicy
import com.eight87.tonearmboy.data.playlist.PlaylistImportResolution
import com.eight87.tonearmboy.data.playlist.PlaylistImportSummary
import com.eight87.tonearmboy.data.playlist.applyPlaylistImport
import com.eight87.tonearmboy.data.playlist.buildPlaylistBackup
import com.eight87.tonearmboy.data.playlist.resolvePlaylistImport
import com.eight87.tonearmboy.ui.settings.PlaylistImportCollisionDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * R.E.4 — playlist export/import surface lifted out of [TonearmboyApp].
 *
 * Owns the two SAF launchers (CreateDocument for export, OpenDocument
 * for import), the pending-import name-collision state, and the
 * collision-resolution dialog. The host composable just wires the
 * `onExport` / `onImport` callbacks into its UI and renders
 * [Overlay] once at the app root so the collision dialog can surface
 * regardless of which destination is on top.
 */
@Stable
interface PlaylistBackupController {
  val onExport: () -> Unit
  val onImport: () -> Unit

  @Composable
  fun Overlay()
}

/**
 * Compose factory: produces a [PlaylistBackupController] bound to the
 * given stores + snackbar + application scope. The pending state lives
 * inside the controller (`remember`-scoped) so it survives recomposition
 * but resets on process death along with the SAF launchers themselves
 * (acceptable — an in-flight import has not yet committed any rows).
 */
@Composable
fun rememberPlaylistBackupController(
  playlists: PlaylistStore,
  tracks: TrackSource,
  snackbar: SnackbarHostState,
  applicationScope: CoroutineScope,
): PlaylistBackupController {
  val context = LocalContext.current
  var pendingExport by remember { mutableStateOf<PlaylistBackupEnvelope?>(null) }
  var pendingImport by remember { mutableStateOf<PlaylistImportPending?>(null) }

  val createDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    val envelope = pendingExport
    pendingExport = null
    if (uri == null || envelope == null) return@rememberLauncherForActivityResult
    applicationScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(PlaylistBackupCodec.encode(envelope).toByteArray(Charsets.UTF_8))
          }
        }
      }.fold(
        onSuccess = {
          snackbar.showSnackbar("Exported ${envelope.playlists.size} playlists")
        },
        onFailure = {
          snackbar.showSnackbar("Export failed: ${it.message ?: "unknown error"}")
        },
      )
    }
  }

  val openDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    applicationScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val raw = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader(Charsets.UTF_8).readText()
          } ?: error("Could not read selected file")
          val envelope = PlaylistBackupCodec.decode(raw)
          val libraryTracks = tracks.observeTracks().first()
          val resolution = resolvePlaylistImport(envelope, libraryTracks)
          val existingNames = playlists.observePlaylists().first().map { it.name }.toSet()
          val collisions = resolution.resolved.keys.filter { it in existingNames }
          Triple(envelope, resolution, collisions)
        }
      }.fold(
        onSuccess = { (env, resolution, collisions) ->
          if (collisions.isEmpty()) {
            val summary = applyPlaylistImport(
              repository = playlists,
              envelope = env,
              resolution = resolution,
              collisionPolicy = PlaylistImportCollisionPolicy.Merge,
            )
            snackbar.showSnackbar(importSummaryMessage(summary))
          } else {
            pendingImport = PlaylistImportPending(env, resolution, collisions)
          }
        },
        onFailure = {
          snackbar.showSnackbar("Import failed: ${it.message ?: "unknown error"}")
        },
      )
    }
  }

  return remember(playlists, tracks, snackbar, applicationScope) {
    object : PlaylistBackupController {
      override val onExport: () -> Unit = {
        applicationScope.launch {
          val envelope = buildPlaylistBackup(playlists)
          pendingExport = envelope
          createDocLauncher.launch(PlaylistBackupCodec.defaultFileName())
        }
      }
      override val onImport: () -> Unit = {
        openDocLauncher.launch(arrayOf("application/json"))
      }

      @Composable
      override fun Overlay() {
        pendingImport?.let { pending ->
          PlaylistImportCollisionDialog(
            collidingNames = pending.collisions,
            onPick = { policy ->
              val captured = pending
              pendingImport = null
              applicationScope.launch {
                val summary = applyPlaylistImport(
                  repository = playlists,
                  envelope = captured.envelope,
                  resolution = captured.resolution,
                  collisionPolicy = policy,
                )
                snackbar.showSnackbar(importSummaryMessage(summary))
              }
            },
            onDismiss = { pendingImport = null },
          )
        }
      }
    }
  }
}

private data class PlaylistImportPending(
  val envelope: PlaylistBackupEnvelope,
  val resolution: PlaylistImportResolution,
  val collisions: List<String>,
)

private fun importSummaryMessage(summary: PlaylistImportSummary): String {
  val parts = mutableListOf<String>()
  parts += "Imported ${summary.importedCount} playlists"
  if (summary.skippedCount > 0) parts += "${summary.skippedCount} skipped"
  if (summary.unmatchedTrackCount > 0) {
    parts += "${summary.unmatchedTrackCount} tracks not found"
  }
  return parts.joinToString(", ")
}
