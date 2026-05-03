package com.eight87.tonearm.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eight87.tonearm.data.LibraryScanner
import kotlinx.coroutines.launch

/**
 * D.17.3 — Auxio-style Music sources dialog state machine.
 *
 * Pure data class. The dialog defers all DataStore writes until Save is
 * tapped, so Cancel discards the working copy without side effects.
 * The state machine has three operations:
 *
 *   - [setMode]      — toggle the segmented File picker / System chooser.
 *   - [addFolder]    — append a tree URI string from the SAF picker.
 *   - [removeFolder] — remove a tree URI string by exact match.
 *
 * Adding / removing folders is allowed in either mode — toggling to
 * System does NOT discard the saved folder list, so flipping back to
 * File picker preserves the user's existing folder choices.
 */
data class MusicSourcesDialogState(
  val mode: MusicSourceMode,
  val folders: List<String>,
  val moreSettingsExpanded: Boolean = false,
) {
  fun setMode(mode: MusicSourceMode): MusicSourcesDialogState =
    copy(mode = mode)

  fun addFolder(uri: String): MusicSourcesDialogState =
    if (uri in folders) this else copy(folders = folders + uri)

  fun removeFolder(uri: String): MusicSourcesDialogState =
    if (uri !in folders) this else copy(folders = folders - uri)

  fun toggleMoreSettings(): MusicSourcesDialogState =
    copy(moreSettingsExpanded = !moreSettingsExpanded)

  companion object {
    fun fromPersisted(mode: MusicSourceMode, folders: Set<String>): MusicSourcesDialogState =
      MusicSourcesDialogState(mode = mode, folders = folders.toList())
  }
}

/**
 * D.17.3 — modal dialog that replaces the navigated Music sources
 * sub-page. Cancel pops the dialog without writing; Save persists the
 * working state and triggers a rescan.
 *
 * The dialog mirrors Auxio's pattern:
 *   - Top: "Music sources" title + close (X).
 *   - Segmented control: [ File picker | System ].
 *   - Subtitle changes per choice.
 *   - "Folders to Load" list with `+` add button (File picker only).
 *   - "More settings" expandable at the bottom.
 *   - Cancel / Save buttons.
 */
@Composable
fun MusicSourcesDialog(
  settings: MusicSourcesSettings,
  scanner: LibraryScanner,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val persistedMode by settings.musicSourceMode.flow.collectAsState(initial = MusicSourceMode.Default)
  val persistedFolders by settings.musicSourceUris.flow.collectAsState(initial = emptySet())

  // Working copy. Re-seeded any time the persisted snapshot changes
  // before Save (so opening the dialog reflects the current persisted
  // state). After the user starts editing, persisted-state changes are
  // ignored until Save / Cancel completes.
  var state by remember(persistedMode, persistedFolders) {
    mutableStateOf(MusicSourcesDialogState.fromPersisted(persistedMode, persistedFolders))
  }

  // SAF directory picker. Adds the tree URI to the working state and
  // persists the read permission so the URI survives restart.
  val pickDirectory = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    state = state.addFolder(uri.toString())
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
    ),
  ) {
    Surface(
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .fillMaxWidth()
        .semantics { testTag = "music_sources_dialog" },
      shape = MaterialTheme.shapes.large,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier
          .padding(top = 8.dp, bottom = 8.dp)
          .heightIn(max = 600.dp),
      ) {
        // Title + close.
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Music sources",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
          }
        }
        HorizontalDivider()

        Column(
          modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .animateContentSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          LoadFromSection(
            mode = state.mode,
            onModeChange = { state = state.setMode(it) },
          )

          if (state.mode == MusicSourceMode.FilePicker) {
            HorizontalDivider()
            FoldersSection(
              folders = state.folders,
              onAddClick = { pickDirectory.launch(null) },
              onRemove = { state = state.removeFolder(it) },
            )
          } else {
            HorizontalDivider()
            ImplicitSystemSection()
          }

          HorizontalDivider()
          MoreSettingsSection(
            expanded = state.moreSettingsExpanded,
            onToggle = { state = state.toggleMoreSettings() },
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { testTag = "music_sources_cancel" },
          ) { Text("Cancel") }
          TextButton(
            onClick = {
              // D.17.3.5 — persist + rescan on Save. We release SAF
              // permissions for any folder removed in this session
              // so the system doesn't accumulate stale grants.
              val toRelease = persistedFolders - state.folders.toSet()
              scope.launch {
                settings.musicSourceMode.set(state.mode)
                settings.musicSourceUris.set(state.folders.toSet())
                toRelease.forEach { uri ->
                  runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                      Uri.parse(uri),
                      Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                  }
                }
                scanner.rescanNow()
              }
              onDismiss()
            },
            modifier = Modifier.semantics { testTag = "music_sources_save" },
          ) { Text("Save") }
        }
      }
    }
  }
}

@Composable
private fun LoadFromSection(
  mode: MusicSourceMode,
  onModeChange: (MusicSourceMode) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Load From",
      style = MaterialTheme.typography.titleMedium,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      SegmentedButton(
        selected = mode == MusicSourceMode.FilePicker,
        onClick = { onModeChange(MusicSourceMode.FilePicker) },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        modifier = Modifier.semantics { testTag = "music_sources_segment_filepicker" },
      ) { Text("File picker") }
      SegmentedButton(
        selected = mode == MusicSourceMode.System,
        onClick = { onModeChange(MusicSourceMode.System) },
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        modifier = Modifier.semantics { testTag = "music_sources_segment_system" },
      ) { Text("System") }
    }
    Text(
      text = when (mode) {
        MusicSourceMode.FilePicker ->
          "Load music from the folders that you select. Slower, but more reliable. " +
            "Requires the vanilla file manager app to be installed."
        MusicSourceMode.System ->
          "Scan the system MediaStore index. Faster, automatic."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun FoldersSection(
  folders: List<String>,
  onAddClick: () -> Unit,
  onRemove: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Folders to Load",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = onAddClick,
        modifier = Modifier.semantics { testTag = "music_sources_add" },
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Add folder")
      }
    }
    if (folders.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp)
          .semantics { testTag = "music_sources_empty" },
      ) {
        Text(
          text = "No folders configured. Tap + to add one.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      folders.forEach { uri ->
        FolderRow(uri = uri, onRemove = { onRemove(uri) })
      }
    }
  }
}

@Composable
private fun FolderRow(uri: String, onRemove: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .semantics { testTag = "music_source_row_$uri" },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      Icons.Filled.Folder,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = displayNameForUri(uri),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f),
    )
    IconButton(onClick = onRemove) {
      Icon(Icons.Filled.Delete, contentDescription = "Remove folder")
    }
  }
}

@Composable
private fun ImplicitSystemSection() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .semantics { testTag = "music_sources_implicit_system" },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      Icons.Filled.Folder,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "Internal shared storage",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
  }
}

/**
 * Pull a human-readable display name out of a SAF tree URI. The
 * convention is `content://.../tree/<volume>%3A<relative-path>`; we
 * decode the document id and surface its trailing path segment, or
 * fall back to the raw URI's last segment when the format isn't
 * recognised.
 */
internal fun displayNameForUri(uriStr: String): String {
  val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return uriStr
  val last = uri.lastPathSegment ?: return uriStr
  val decoded = runCatching { Uri.decode(last) }.getOrNull() ?: last
  val sep = decoded.indexOf(':')
  val tail = if (sep < 0) decoded else decoded.substring(sep + 1)
  return when {
    tail.isBlank() && sep >= 0 -> decoded.substring(0, sep)
    tail.isBlank() -> decoded
    else -> tail
  }
}

@Composable
private fun MoreSettingsSection(
  expanded: Boolean,
  onToggle: () -> Unit,
) {
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .semantics { testTag = "music_sources_more_settings" },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "More settings",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onToggle) {
        Icon(
          Icons.Filled.ExpandMore,
          contentDescription = if (expanded) "Collapse" else "Expand",
          modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
        )
      }
    }
    if (expanded) {
      Column(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Multi-value separators are configured in " +
            "Settings › Personalize › Multi-value separators.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
