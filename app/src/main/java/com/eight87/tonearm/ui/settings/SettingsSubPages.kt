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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.data.watcher.LibraryWatcherService
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
  var customBarPicker by remember { mutableStateOf(false) }
  var customNotifPicker by remember { mutableStateOf(false) }
  var playFromLibPicker by remember { mutableStateOf(false) }
  var playFromDetailPicker by remember { mutableStateOf(false) }

  val bindings = listOf(
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_LIBRARY_TABS,
      currentLabel = describeLibraryTabs(snapshot.libraryTabs),
      onClick = { showLibraryTabs = true },
    ),
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_CUSTOM_PLAYBACK_BAR_ACTION,
      currentLabel = customBarActionLabel(snapshot.customBarAction),
      onClick = { customBarPicker = true },
    ),
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_CUSTOM_NOTIFICATION_ACTION,
      currentLabel = customNotificationActionLabel(snapshot.customNotificationAction),
      onClick = { customNotifPicker = true },
    ),
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_PLAY_FROM_LIBRARY,
      currentLabel = playFromLibraryLabel(snapshot.playFromLibrary),
      onClick = { playFromLibPicker = true },
    ),
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_PLAY_FROM_ITEM_DETAILS,
      currentLabel = playFromItemDetailsLabel(snapshot.playFromItemDetails),
      onClick = { playFromDetailPicker = true },
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
  if (customBarPicker) {
    RadioPicker(
      title = "Custom playback bar action",
      options = CustomBarAction.entries,
      label = ::customBarActionLabel,
      current = snapshot.customBarAction,
      onPick = { scope.launch { repository.setCustomBarAction(it) }; customBarPicker = false },
      onDismiss = { customBarPicker = false },
    )
  }
  if (customNotifPicker) {
    RadioPicker(
      title = "Custom notification action",
      options = CustomNotificationAction.entries,
      label = ::customNotificationActionLabel,
      current = snapshot.customNotificationAction,
      onPick = { scope.launch { repository.setCustomNotificationAction(it) }; customNotifPicker = false },
      onDismiss = { customNotifPicker = false },
    )
  }
  if (playFromLibPicker) {
    RadioPicker(
      title = "When playing from the library",
      options = PlayFromLibrary.entries,
      label = ::playFromLibraryLabel,
      current = snapshot.playFromLibrary,
      onPick = { scope.launch { repository.setPlayFromLibrary(it) }; playFromLibPicker = false },
      onDismiss = { playFromLibPicker = false },
    )
  }
  if (playFromDetailPicker) {
    RadioPicker(
      title = "When playing from item details",
      options = PlayFromItemDetails.entries,
      label = ::playFromItemDetailsLabel,
      current = snapshot.playFromItemDetails,
      onPick = { scope.launch { repository.setPlayFromItemDetails(it) }; playFromDetailPicker = false },
      onDismiss = { playFromDetailPicker = false },
    )
  }
}

internal fun customBarActionLabel(action: CustomBarAction): String = when (action) {
  CustomBarAction.SkipNext -> "Skip to next"
  CustomBarAction.ShuffleToggle -> "Shuffle toggle"
  CustomBarAction.RepeatToggle -> "Repeat mode toggle"
  CustomBarAction.None -> "None"
}

internal fun customNotificationActionLabel(action: CustomNotificationAction): String = when (action) {
  CustomNotificationAction.RepeatMode -> "Repeat mode"
  CustomNotificationAction.Shuffle -> "Shuffle"
  CustomNotificationAction.None -> "None"
}

internal fun playFromLibraryLabel(value: PlayFromLibrary): String = when (value) {
  PlayFromLibrary.AllSongs -> "Play from all songs"
  PlayFromLibrary.ItemOnly -> "Play from item only"
  PlayFromLibrary.CurrentFilter -> "Play from current filter"
}

internal fun playFromItemDetailsLabel(value: PlayFromItemDetails): String = when (value) {
  PlayFromItemDetails.ShownItem -> "Play from shown item"
  PlayFromItemDetails.Album -> "Play from album"
  PlayFromItemDetails.Artist -> "Play from artist"
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

@OptIn(UnstableApi::class)
@Composable
fun SettingsContentScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val snapshot by repository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
  val scope = rememberCoroutineScope()
  var albumCoversPicker by remember { mutableStateOf(false) }
  var separatorsPicker by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val bindings = listOf(
    // D.9d.2 — Automatic reloading toggle. Persists immediately and
    // starts / stops the foreground watcher service in the same gesture
    // so the user sees the sticky notification appear within ~1 frame.
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_AUTOMATIC_RELOADING,
      checked = snapshot.automaticReloading,
      onCheckedChange = { value ->
        scope.launch { repository.setAutomaticReloading(value) }
        if (value) {
          LibraryWatcherService.start(context)
        } else {
          LibraryWatcherService.stop(context)
        }
      },
    ),
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_MULTI_VALUE_SEPARATORS,
      currentLabel = multiValueSeparatorsLabel(snapshot.multiValueSeparators),
      onClick = { separatorsPicker = true },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_INTELLIGENT_SORTING,
      checked = snapshot.intelligentSorting,
      onCheckedChange = { scope.launch { repository.setIntelligentSorting(it) } },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_HIDE_COLLABORATORS,
      checked = snapshot.hideCollaborators,
      onCheckedChange = { scope.launch { repository.setHideCollaborators(it) } },
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
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_ALBUM_COVERS,
      currentLabel = albumCoversLabel(snapshot.albumCoversMode),
      onClick = { albumCoversPicker = true },
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

  if (albumCoversPicker) {
    RadioPicker(
      title = "Album covers",
      options = AlbumCoversMode.entries,
      label = ::albumCoversLabel,
      current = snapshot.albumCoversMode,
      onPick = {
        scope.launch { repository.setAlbumCoversMode(it) }
        albumCoversPicker = false
      },
      onDismiss = { albumCoversPicker = false },
    )
  }

  if (separatorsPicker) {
    MultiSelectPicker(
      title = "Multi-value separators",
      options = MultiValueSeparator.entries,
      label = ::multiValueSeparatorOptionLabel,
      currentSelection = snapshot.multiValueSeparators,
      onSave = { newSelection ->
        scope.launch { repository.setMultiValueSeparators(newSelection) }
        separatorsPicker = false
        if (newSelection != snapshot.multiValueSeparators) {
          scope.launch {
            snackbarHostState.showSnackbar(
              "Multi-value separator change applied. Run Settings > Library > " +
                "Rescan music to pick up existing tracks.",
            )
          }
        }
      },
      onDismiss = { separatorsPicker = false },
    )
  }
}

internal fun multiValueSeparatorOptionLabel(s: MultiValueSeparator): String = when (s) {
  MultiValueSeparator.Semicolon -> "Semicolon  ;"
  MultiValueSeparator.Slash -> "Slash  /"
  MultiValueSeparator.Comma -> "Comma  ,"
  MultiValueSeparator.Ampersand -> "Ampersand  &"
  MultiValueSeparator.Feat -> "feat."
  MultiValueSeparator.Ft -> "ft."
}

internal fun multiValueSeparatorsLabel(selection: Set<MultiValueSeparator>): String {
  if (selection.isEmpty()) return "Off"
  // Surface the literal tokens so the row preview matches what the user
  // selected. Sort by enum order to keep the preview deterministic.
  return selection.sortedBy { it.ordinal }.joinToString("  ") { it.token }
}

internal fun albumCoversLabel(mode: AlbumCoversMode): String = when (mode) {
  AlbumCoversMode.Balanced -> "Balanced"
  AlbumCoversMode.On -> "Always load"
  AlbumCoversMode.Off -> "Never load"
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
  var rgStrategyPicker by remember { mutableStateOf(false) }
  var rgPreampDialog by remember { mutableStateOf(false) }

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
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_PAUSE_ON_REPEAT,
      checked = snapshot.pauseOnRepeat,
      onCheckedChange = { scope.launch { repository.setPauseOnRepeat(it) } },
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_REMEMBER_PAUSE,
      checked = snapshot.rememberPause,
      onCheckedChange = { scope.launch { repository.setRememberPause(it) } },
    ),
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_REPLAYGAIN_STRATEGY,
      currentLabel = replayGainStrategyLabel(snapshot.replayGainStrategy),
      onClick = { rgStrategyPicker = true },
    ),
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_REPLAYGAIN_PREAMP,
      currentLabel = formatPreampDb(snapshot.replayGainPreampDb),
      onClick = { rgPreampDialog = true },
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

  if (rgStrategyPicker) {
    RadioPicker(
      title = "ReplayGain strategy",
      options = ReplayGainStrategy.entries,
      label = ::replayGainStrategyLabel,
      current = snapshot.replayGainStrategy,
      onPick = {
        scope.launch { repository.setReplayGainStrategy(it) }
        rgStrategyPicker = false
      },
      onDismiss = { rgStrategyPicker = false },
    )
  }
  if (rgPreampDialog) {
    ReplayGainPreampDialog(
      currentDb = snapshot.replayGainPreampDb,
      onConfirm = { newDb ->
        scope.launch { repository.setReplayGainPreampDb(newDb) }
        rgPreampDialog = false
      },
      onDismiss = { rgPreampDialog = false },
    )
  }
}

internal fun replayGainStrategyLabel(s: ReplayGainStrategy): String = when (s) {
  ReplayGainStrategy.Off -> "Off"
  ReplayGainStrategy.Track -> "Track"
  ReplayGainStrategy.Album -> "Album"
  ReplayGainStrategy.Smart -> "Smart"
}

/** Format a pre-amp dB value with one decimal and an explicit sign. */
internal fun formatPreampDb(dB: Float): String {
  val rounded = (Math.round(dB * 10.0).toFloat()) / 10f
  val sign = if (rounded > 0) "+" else if (rounded < 0) "-" else ""
  val mag = kotlin.math.abs(rounded)
  return "$sign${"%.1f".format(mag)} dB"
}

/**
 * D.9b.2 — slider dialog for the pre-amp. Shows the current value
 * live as the user drags. "Reset" snaps back to 0 dB. Save persists
 * via [SettingsRepository.setReplayGainPreampDb], which clamps and
 * snaps to the 0.1 dB grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplayGainPreampDialog(
  currentDb: Float,
  onConfirm: (Float) -> Unit,
  onDismiss: () -> Unit,
) {
  var value by remember(currentDb) { mutableStateOf(currentDb) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("ReplayGain pre-amp") },
    text = {
      Column(modifier = Modifier.semantics { testTag = "rg_preamp_dialog" }) {
        Text(
          text = formatPreampDb(value),
          style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = "Adds a constant offset on top of the strategy gain. " +
            "Negative values attenuate; positive values clamp at 0 dB " +
            "(Player.volume cannot amplify above unity).",
          style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 4.dp),
        )
        Slider(
          value = value,
          onValueChange = { value = it },
          valueRange = SettingsRepository.REPLAYGAIN_PREAMP_MIN_DB..SettingsRepository.REPLAYGAIN_PREAMP_MAX_DB,
          // (max - min) / step - 1 internal stops; 0.1 dB grid over
          // 30 dB == 299 internal steps.
          steps = ((SettingsRepository.REPLAYGAIN_PREAMP_MAX_DB - SettingsRepository.REPLAYGAIN_PREAMP_MIN_DB) /
            SettingsRepository.REPLAYGAIN_PREAMP_STEP_DB).toInt() - 1,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics { testTag = "rg_preamp_slider" },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
          TextButton(onClick = { value = 0f }) { Text("Reset to 0 dB") }
        }
      }
    },
    confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
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

// =============================================================================
// D.9c.1 — Generic multi-select dialog. Multiple options can be enabled
// simultaneously; tapping Save commits the new set, Cancel discards.
// =============================================================================

@Composable
private fun <T> MultiSelectPicker(
  title: String,
  options: Iterable<T>,
  label: (T) -> String,
  currentSelection: Set<T>,
  onSave: (Set<T>) -> Unit,
  onDismiss: () -> Unit,
) {
  // Local draft selection so the user can tick / untick without committing
  // until they tap Save. Cancel discards everything.
  var draft by remember(currentSelection) { mutableStateOf(currentSelection) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        options.forEach { option ->
          val checked = option in draft
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .selectable(
                selected = checked,
                onClick = {
                  draft = if (checked) draft - option else draft + option
                },
              )
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(label(option), modifier = Modifier.padding(start = 12.dp))
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
