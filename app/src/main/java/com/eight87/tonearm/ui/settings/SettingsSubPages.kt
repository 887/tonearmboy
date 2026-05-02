package com.eight87.tonearm.ui.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.eight87.tonearm.ui.settings.catalog.Section
import com.eight87.tonearm.ui.settings.catalog.SettingsCatalog
import com.eight87.tonearm.ui.settings.catalog.SettingsCatalogPage
import com.eight87.tonearm.ui.settings.catalog.SettingsRowBinding
import kotlinx.coroutines.launch

// =============================================================================
// Shared sub-page scaffold: back arrow + section title + catalog-driven body.
// Sub-pages do NOT host their own search bar — search is global, lives only at
// the Settings root.
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScaffold(
  title: String,
  testTagName: String,
  onBack: () -> Unit,
  snackbarHostState: SnackbarHostState,
  body: @Composable (Modifier) -> Unit,
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
    body(
      Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .semantics { testTag = testTagName },
    )
  }
}

// =============================================================================
// Look and Feel
// =============================================================================

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

  val bindings = listOf(
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_THEME,
      currentLabel = themeLabel(snapshot.theme),
      onClick = { themePicker = true },
    ),
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_COLOR_SCHEME,
      currentLabel = colorSchemeLabel(snapshot.colorScheme),
      onClick = { schemePicker = true },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_BLACK_THEME,
      checked = snapshot.blackTheme,
      onCheckedChange = { scope.launch { repository.setBlackTheme(it) } },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_ROUND_MODE,
      checked = snapshot.roundMode,
      onCheckedChange = { scope.launch { repository.setRoundMode(it) } },
    ),
  )

  SettingsSubScaffold("Look and Feel", "settings_look_and_feel", onBack, snackbarHostState) { mod ->
    SettingsCatalogPage(
      testTagName = "settings_look_and_feel_body",
      section = Section.LookAndFeel,
      bindings = bindings,
      modifier = mod,
    )
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

// =============================================================================
// Personalize
// =============================================================================

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

  val bindings = listOf(
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_LIBRARY_TABS,
      currentLabel = describeLibraryTabs(snapshot.libraryTabs),
      onClick = { showLibraryTabs = true },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_CUSTOM_PLAYBACK_BAR_ACTION,
      onClick = { onComingSoon("Custom playback bar action") },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_CUSTOM_NOTIFICATION_ACTION,
      onClick = { onComingSoon("Custom notification action") },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_PLAY_FROM_LIBRARY,
      onClick = { onComingSoon("When playing from the library") },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_PLAY_FROM_ITEM_DETAILS,
      onClick = { onComingSoon("When playing from item details") },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_REMEMBER_SHUFFLE,
      checked = snapshot.rememberShuffle,
      onCheckedChange = { scope.launch { repository.setRememberShuffle(it) } },
    ),
  )

  SettingsSubScaffold("Personalize", "settings_personalize", onBack, snackbarHostState) { mod ->
    SettingsCatalogPage(
      testTagName = "settings_personalize_body",
      section = Section.Personalize,
      bindings = bindings,
      modifier = mod,
    )
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

// =============================================================================
// Content
// =============================================================================

@Composable
fun SettingsContentScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val snapshot by repository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
  val scope = rememberCoroutineScope()

  val bindings = listOf(
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_AUTOMATIC_RELOADING,
      onClick = { onComingSoon("Automatic reloading") },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_MULTI_VALUE_SEPARATORS,
      onClick = { onComingSoon("Multi-value separators") },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_INTELLIGENT_SORTING,
      checked = snapshot.intelligentSorting,
      onCheckedChange = { scope.launch { repository.setIntelligentSorting(it) } },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_HIDE_COLLABORATORS,
      onClick = { onComingSoon("Hide collaborators") },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_AUTO_DISCOVER_ALBUM_ART,
      checked = snapshot.autoDiscoverAlbumArt,
      onCheckedChange = { value ->
        scope.launch { repository.setAutoDiscoverAlbumArt(value) }
        scope.launch {
          snackbarHostState.showSnackbar(
            "Coming in v1.1 — for now, manual cover-art import only.",
          )
        }
      },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_ALBUM_COVERS,
      onClick = { onComingSoon("Album covers") },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_FORCE_SQUARE_COVERS,
      checked = snapshot.forceSquareCovers,
      onCheckedChange = { scope.launch { repository.setForceSquareCovers(it) } },
    ),
  )

  SettingsSubScaffold("Content", "settings_content", onBack, snackbarHostState) { mod ->
    SettingsCatalogPage(
      testTagName = "settings_content_body",
      section = Section.Content,
      bindings = bindings,
      modifier = mod,
    )
  }
}

// =============================================================================
// Audio
// =============================================================================

@Composable
fun SettingsAudioScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val snapshot by repository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
  val scope = rememberCoroutineScope()

  val bindings = listOf(
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_HEADSET_AUTOPLAY,
      checked = snapshot.headsetAutoplay,
      onCheckedChange = { scope.launch { repository.setHeadsetAutoplay(it) } },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_REWIND_BEFORE_SKIP,
      checked = snapshot.rewindBeforeSkipBack,
      onCheckedChange = { scope.launch { repository.setRewindBeforeSkipBack(it) } },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_PAUSE_ON_REPEAT,
      onClick = { onComingSoon("Pause on repeat") },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_REMEMBER_PAUSE,
      checked = snapshot.rememberPause,
      onCheckedChange = { scope.launch { repository.setRememberPause(it) } },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_REPLAYGAIN_STRATEGY,
      onClick = { onComingSoon("ReplayGain strategy") },
    ),
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_REPLAYGAIN_PREAMP,
      onClick = { onComingSoon("ReplayGain pre-amp") },
    ),
  )

  SettingsSubScaffold("Audio", "settings_audio", onBack, snackbarHostState) { mod ->
    SettingsCatalogPage(
      testTagName = "settings_audio_body",
      section = Section.Audio,
      bindings = bindings,
      modifier = mod,
    )
  }
}

// =============================================================================
// Generic radio-list picker dialog used across sub-pages.
// =============================================================================

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
