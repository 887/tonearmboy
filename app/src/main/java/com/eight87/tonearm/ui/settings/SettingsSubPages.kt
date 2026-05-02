package com.eight87.tonearm.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Common scaffold used by every settings sub-page: TopAppBar with a back
 * arrow + a `LazyColumn` content slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScaffold(
  title: String,
  testTagName: String,
  onBack: () -> Unit,
  snackbarHostState: SnackbarHostState,
  content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
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
        .semantics { testTag = testTagName },
      content = content,
    )
  }
}

// =========================================================================
// Look and Feel
// =========================================================================

@Composable
fun SettingsLookAndFeelScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val snapshot by repository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
  val scope = rememberCoroutineScope()
  var themePicker by remember { mutableStateOf(false) }
  var schemePicker by remember { mutableStateOf(false) }

  SettingsSubScaffold("Look and Feel", "settings_look_and_feel", onBack, snackbarHostState) {
    item {
      SettingsRow(
        title = "Theme",
        subtitle = themeLabel(snapshot.theme),
        onClick = { themePicker = true },
      )
    }
    item {
      SettingsRow(
        title = "Color scheme",
        subtitle = colorSchemeLabel(snapshot.colorScheme),
        onClick = { schemePicker = true },
      )
    }
    item {
      SettingsToggleRow(
        title = "Black theme",
        subtitle = "Use pure black for the dark theme background.",
        checked = snapshot.blackTheme,
        onCheckedChange = { scope.launch { repository.setBlackTheme(it) } },
      )
    }
    item {
      SettingsToggleRow(
        title = "Round mode",
        subtitle = "Apply rounded corners to additional UI elements.",
        checked = snapshot.roundMode,
        onCheckedChange = { scope.launch { repository.setRoundMode(it) } },
      )
    }
  }

  if (themePicker) {
    RadioPicker(
      title = "Theme",
      options = ThemePreference.entries,
      label = ::themeLabel,
      current = snapshot.theme,
      onPick = { scope.launch { repository.setTheme(it) }; themePicker = false },
      onDismiss = { themePicker = false },
    )
  }
  if (schemePicker) {
    RadioPicker(
      title = "Color scheme",
      options = ColorScheme.entries,
      label = ::colorSchemeLabel,
      current = snapshot.colorScheme,
      onPick = { scope.launch { repository.setColorScheme(it) }; schemePicker = false },
      onDismiss = { schemePicker = false },
    )
  }
}

private fun themeLabel(p: ThemePreference): String = when (p) {
  ThemePreference.System -> "Automatic"
  ThemePreference.Light -> "Light"
  ThemePreference.Dark -> "Dark"
}

private fun colorSchemeLabel(s: ColorScheme): String = when (s) {
  ColorScheme.Dynamic -> "Dynamic (Material You)"
  ColorScheme.Brand -> "Brand palette"
}

// =========================================================================
// Personalize
// =========================================================================

@Composable
fun SettingsPersonalizeScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val snapshot by repository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
  val scope = rememberCoroutineScope()
  var showLibraryTabs by remember { mutableStateOf(false) }

  SettingsSubScaffold("Personalize", "settings_personalize", onBack, snackbarHostState) {
    item { SectionHeader("Display") }
    item {
      SettingsRow(
        title = "Library tabs",
        subtitle = describeLibraryTabs(snapshot.libraryTabs),
        onClick = { showLibraryTabs = true },
      )
    }
    item {
      SettingsRow(
        title = "Custom playback bar action",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("Custom playback bar action") },
      )
    }
    item {
      SettingsRow(
        title = "Custom notification action",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("Custom notification action") },
      )
    }

    item { SectionHeader("Behavior") }
    item {
      SettingsRow(
        title = "When playing from the library",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("When playing from the library") },
      )
    }
    item {
      SettingsRow(
        title = "When playing from item details",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("When playing from item details") },
      )
    }
    item {
      SettingsToggleRow(
        title = "Remember shuffle",
        subtitle = "Restore the previous shuffle state on relaunch.",
        checked = snapshot.rememberShuffle,
        onCheckedChange = { scope.launch { repository.setRememberShuffle(it) } },
      )
    }
  }

  if (showLibraryTabs) {
    LibraryTabsEditor(
      current = snapshot.libraryTabs,
      onDismiss = { showLibraryTabs = false },
      onConfirm = { newOrder ->
        scope.launch { repository.setLibraryTabs(newOrder) }
        showLibraryTabs = false
      },
    )
  }
}

private fun describeLibraryTabs(tabs: List<LibraryTab>): String {
  if (tabs.isEmpty()) return "Default"
  return tabs.joinToString(" · ") { it.name }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTabsEditor(
  current: List<LibraryTab>,
  onDismiss: () -> Unit,
  onConfirm: (List<LibraryTab>) -> Unit,
) {
  // Working copy: ordered list of tabs with a "visible" flag. Hidden
  // tabs disappear from the strip; visible ones render in the order of
  // the list. Reordering is via up/down arrows for accessibility — a
  // drag handle would need extra deps.
  val initialState = remember(current) {
    val seen = current.toMutableSet()
    val ordered = current.toMutableList()
    LibraryTab.entries.forEach { if (it !in seen) { ordered += it; seen += it } }
    ordered.map { tab -> tab to (tab in current) }.toMutableList()
  }
  var rows by remember { mutableStateOf(initialState) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Library tabs") },
    text = {
      LazyColumn(modifier = Modifier.fillMaxWidth().semantics { testTag = "library_tabs_editor" }) {
        items(rows.size) { index ->
          val (tab, visible) = rows[index]
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Switch(
              checked = visible,
              onCheckedChange = { v ->
                rows = rows.toMutableList().also { it[index] = tab to v }
              },
            )
            Text(tabLabel(tab), modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
            IconButton(
              onClick = {
                if (index > 0) rows = rows.toMutableList().also {
                  val tmp = it[index - 1]; it[index - 1] = it[index]; it[index] = tmp
                }
              },
              enabled = index > 0,
            ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
            IconButton(
              onClick = {
                if (index < rows.size - 1) rows = rows.toMutableList().also {
                  val tmp = it[index + 1]; it[index + 1] = it[index]; it[index] = tmp
                }
              },
              enabled = index < rows.size - 1,
            ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        // Visible tabs first (in order), then hidden tabs (preserved
        // for round-trip but not surfaced).
        val visibleTabs = rows.filter { it.second }.map { it.first }
        onConfirm(visibleTabs)
      }) { Text("OK") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun tabLabel(tab: LibraryTab): String = when (tab) {
  LibraryTab.Songs -> "Songs"
  LibraryTab.Albums -> "Albums"
  LibraryTab.Artists -> "Artists"
  LibraryTab.Genres -> "Genres"
  LibraryTab.Playlists -> "Playlists"
}

// =========================================================================
// Content
// =========================================================================

@Composable
fun SettingsContentScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val snapshot by repository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
  val scope = rememberCoroutineScope()

  SettingsSubScaffold("Content", "settings_content", onBack, snackbarHostState) {
    item { SectionHeader("Music") }
    item {
      SettingsRow(
        title = "Automatic reloading",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("Automatic reloading") },
      )
    }
    item {
      SettingsRow(
        title = "Multi-value separators",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("Multi-value separators") },
      )
    }
    item {
      SettingsToggleRow(
        title = "Intelligent sorting",
        subtitle = "Ignore leading 'the', 'a', 'an' when computing sort order.",
        checked = snapshot.intelligentSorting,
        onCheckedChange = { scope.launch { repository.setIntelligentSorting(it) } },
      )
    }
    item {
      SettingsRow(
        title = "Hide collaborators",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("Hide collaborators") },
      )
    }

    item { SectionHeader("Images") }
    item {
      SettingsRow(
        title = "Album covers",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("Album covers") },
      )
    }
    item {
      SettingsToggleRow(
        title = "Force square album covers",
        subtitle = "Render covers as squares instead of rounded rectangles.",
        checked = snapshot.forceSquareCovers,
        onCheckedChange = { scope.launch { repository.setForceSquareCovers(it) } },
      )
    }
    item {
      // D.8e: persists the user's intent for the Phase H.7 fetch flow.
      // Toggling it on (or tapping the row) shows an informational
      // snackbar — the actual cover-art fetch lands in Phase H.
      SettingsToggleRow(
        title = "Auto-discover missing album art",
        subtitle = "Fetch covers from MusicBrainz Cover Art Archive for albums missing local art (Phase H).",
        checked = snapshot.autoDiscoverAlbumArt,
        onCheckedChange = { value ->
          scope.launch { repository.setAutoDiscoverAlbumArt(value) }
          scope.launch {
            snackbarHostState.showSnackbar(
              "Coming in v1.1 — for now, manual cover-art import only.",
            )
          }
        },
      )
    }
  }
}

// =========================================================================
// Audio
// =========================================================================

@Composable
fun SettingsAudioScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val snapshot by repository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
  val scope = rememberCoroutineScope()

  SettingsSubScaffold("Audio", "settings_audio", onBack, snackbarHostState) {
    item { SectionHeader("Playback") }
    item {
      SettingsToggleRow(
        title = "Headset autoplay",
        subtitle = "Begin playback automatically when headphones connect.",
        checked = snapshot.headsetAutoplay,
        onCheckedChange = { scope.launch { repository.setHeadsetAutoplay(it) } },
      )
    }
    item {
      SettingsToggleRow(
        title = "Rewind before skipping back",
        subtitle = "Tap previous within a few seconds rewinds; otherwise jumps to the previous track.",
        checked = snapshot.rewindBeforeSkipBack,
        onCheckedChange = { scope.launch { repository.setRewindBeforeSkipBack(it) } },
      )
    }
    item {
      SettingsRow(
        title = "Pause on repeat",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("Pause on repeat") },
      )
    }
    item {
      SettingsToggleRow(
        title = "Remember pause",
        subtitle = "Restore the paused position on relaunch.",
        checked = snapshot.rememberPause,
        onCheckedChange = { scope.launch { repository.setRememberPause(it) } },
      )
    }

    item { SectionHeader("Volume normalization") }
    item {
      SettingsRow(
        title = "ReplayGain strategy",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("ReplayGain strategy") },
      )
    }
    item {
      SettingsRow(
        title = "ReplayGain pre-amp",
        subtitle = "Coming in v1.1.",
        onClick = { onComingSoon("ReplayGain pre-amp") },
      )
    }
  }
}

// =========================================================================
// Generic radio-list picker dialog used across sub-pages.
// =========================================================================

@Composable
private fun <T> RadioPicker(
  title: String,
  options: Iterable<T>,
  label: (T) -> String,
  current: T,
  onPick: (T) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        options.forEach { option ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .selectable(selected = option == current, onClick = { onPick(option) })
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = option == current, onClick = null)
            Text(label(option), modifier = Modifier.padding(start = 12.dp))
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}
