package com.eight87.tonearm.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

/**
 * Settings root, mirroring Auxio's eight-entry layout:
 *
 *   1. Look and Feel
 *   2. Personalize
 *   3. Content
 *   4. Audio
 *   --- Library ---
 *   5. Music sources (stub)
 *   6. Refresh music
 *   7. Rescan music
 *
 * Each top-of-list entry navigates to its sub-page; the Library section
 * exposes the music-source / refresh / rescan actions inline because
 * they are leaf actions, not sub-pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onLookAndFeel: () -> Unit,
  onPersonalize: () -> Unit,
  onContent: () -> Unit,
  onAudio: () -> Unit,
  onMusicSources: () -> Unit,
  onRefreshMusic: () -> Unit,
  onRescanMusic: () -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  var confirmRescan by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .semantics { testTag = "settings_screen" },
    ) {
      item { SettingsRow("Look and Feel", "Theme, colour scheme, black mode, round mode.", onClick = onLookAndFeel) }
      item { SettingsRow("Personalize", "Library tabs, behaviour, custom actions.", onClick = onPersonalize) }
      item { SettingsRow("Content", "Sorting, separators, album covers.", onClick = onContent) }
      item { SettingsRow("Audio", "Playback, volume normalization.", onClick = onAudio) }

      item { SectionHeader("Library") }
      item { SettingsRow("Music sources", "Manage where music is loaded from.", onClick = onMusicSources) }
      item {
        SettingsRow(
          title = "Refresh music",
          subtitle = "Reload the library, using cached tags when possible.",
          onClick = onRefreshMusic,
        )
      }
      item {
        SettingsRow(
          title = "Rescan music",
          subtitle = "Clear the cache and re-read everything. Slower but more complete.",
          onClick = { confirmRescan = true },
        )
      }
    }
  }

  if (confirmRescan) {
    AlertDialog(
      onDismissRequest = { confirmRescan = false },
      title = { Text("Rescan library?") },
      text = { Text("This clears cached metadata and re-reads MediaStore. Playback is not interrupted.") },
      confirmButton = {
        TextButton(onClick = { onRescanMusic(); confirmRescan = false }) { Text("Rescan") }
      },
      dismissButton = {
        TextButton(onClick = { confirmRescan = false }) { Text("Cancel") }
      },
    )
  }
}

// ---- shared row primitives ------------------------------------------------

@Composable
internal fun SectionHeader(label: String) {
  Text(
    text = label,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
  )
}

@Composable
internal fun SettingsRow(
  title: String,
  subtitle: String? = null,
  onClick: (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .let { if (onClick != null) it.clickable(onClick = onClick) else it }
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (trailing != null) trailing()
  }
  HorizontalDivider()
}

@Composable
internal fun SettingsToggleRow(
  title: String,
  subtitle: String?,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  SettingsRow(
    title = title,
    subtitle = subtitle,
    onClick = { onCheckedChange(!checked) },
    trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
  )
}
