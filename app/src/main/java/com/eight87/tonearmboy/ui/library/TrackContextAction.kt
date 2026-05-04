package com.eight87.tonearmboy.ui.library

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

/**
 * R.F.1 — the canonical context-menu action for a track row. Replaces
 * the pre-R.F.1 duplicated enums (`TrackRowAction` in
 * `tabs/TracksTabScreen.kt` and `AlbumDetailTrackAction` in
 * `DetailScreens.kt`) which had the same 6 variants in lock-step.
 * Future actions land as one new variant here, not two parallel adds.
 * (UI-F5.)
 *
 * **Scope note:** `QueueRow` (in `playing/QueueSection.kt`) is
 * intentionally NOT folded behind this enum. It has a fundamentally
 * different shape — drag handle, leading remove `X`, active-track
 * highlight, no overflow menu — and forcing it through one composable
 * would require a feature-flag mux that obscured rather than collapsed
 * the difference. The TrackRow / DetailTrackRow pair shares this enum
 * and the overflow menu below; QueueRow keeps its own separate row.
 */
enum class TrackContextAction { Play, AddToQueue, AddToPlaylist, GoToAlbum, GoToArtist, Delete }

/**
 * Shared overflow `DropdownMenu` used by both `TrackRow` (library
 * Songs tab) and `DetailTrackRow` (album / artist / genre detail
 * screens). The 6 menu items are identical between the two; only the
 * test-tag namespace differs (so test code can target a specific row
 * surface).
 */
@Composable
internal fun TrackContextMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onAction: (TrackContextAction) -> Unit,
  deleteTestTag: String,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    DropdownMenuItem(
      text = { Text("Play") },
      onClick = { onDismiss(); onAction(TrackContextAction.Play) },
    )
    DropdownMenuItem(
      text = { Text("Add to queue") },
      onClick = { onDismiss(); onAction(TrackContextAction.AddToQueue) },
    )
    DropdownMenuItem(
      text = { Text("Add to playlist…") },
      onClick = { onDismiss(); onAction(TrackContextAction.AddToPlaylist) },
    )
    DropdownMenuItem(
      text = { Text("Go to album") },
      onClick = { onDismiss(); onAction(TrackContextAction.GoToAlbum) },
    )
    DropdownMenuItem(
      text = { Text("Go to artist") },
      onClick = { onDismiss(); onAction(TrackContextAction.GoToArtist) },
    )
    DropdownMenuItem(
      text = { Text("Delete file…") },
      onClick = { onDismiss(); onAction(TrackContextAction.Delete) },
      modifier = Modifier.semantics { testTag = deleteTestTag },
    )
  }
}
