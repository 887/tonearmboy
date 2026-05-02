package com.eight87.tonearm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
  var baseThemePicker by remember { mutableStateOf(false) }
  var colorPicker by remember { mutableStateOf(false) }

  // D.25.1 — when Custom is the active base theme, render a coloured
  // swatch trailing the row so the user sees what they picked at a
  // glance. Uses the seed RGB straight, alpha 1.
  val customSwatch: (@Composable () -> Unit)? = (snapshot.baseTheme as? BaseTheme.Custom)?.let { custom ->
    {
      Box(
        modifier = Modifier
          .size(24.dp)
          .clip(CircleShape)
          .background(Color(0xFF000000L or custom.seedRgb))
          .semantics { testTag = "base_theme_custom_swatch" },
      )
    }
  }

  val bindings = listOf(
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_THEME,
      currentLabel = themeLabel(snapshot.theme),
      onClick = { themePicker = true },
    ),
    // D.20.4 / D.25.1 — base-theme picker. Custom-color is a fourth
    // option that opens a real colour picker; the trailing swatch
    // surfaces the picked seed when Custom is active.
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_BASE_THEME,
      currentLabel = baseThemeLabel(snapshot.baseTheme),
      onClick = { baseThemePicker = true },
      trailing = customSwatch,
    ),
    SettingsRowBinding.Toggle(
      id = SettingsCatalog.ID_ALBUM_ART_TINT,
      checked = snapshot.albumArtTintEnabled,
      onCheckedChange = { scope.launch { repository.setAlbumArtTintEnabled(it) } },
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
  if (baseThemePicker) {
    // D.25.1 — the picker offers four options. Tapping Custom closes
    // the radio dialog and opens the colour picker; the other three
    // commit immediately like before.
    RadioPicker(
      title = "Base theme",
      options = BaseTheme.pickerOptions,
      label = ::baseThemeLabel,
      // Match by *kind* — the radio dialog's Custom sentinel carries
      // a placeholder seed, but the snapshot's Custom carries the
      // user's saved seed. We still want the bullet to land on Custom
      // when the user has previously picked one.
      current = baseThemeMatch(snapshot.baseTheme),
      onPick = { picked ->
        baseThemePicker = false
        if (picked is BaseTheme.Custom) {
          colorPicker = true
        } else {
          scope.launch { repository.setBaseTheme(picked) }
        }
      },
      onDismiss = { baseThemePicker = false },
    )
  }
  if (colorPicker) {
    // Re-open from the saved seed when the user already had Custom
    // selected; otherwise fall back to the placeholder Material 3
    // purple so the picker has a sensible starting point.
    val initialSeed = (snapshot.baseTheme as? BaseTheme.Custom)?.seedRgb ?: 0x6750A4L
    ColorPickerDialog(
      initialRgb = initialSeed,
      onConfirm = { rgb ->
        scope.launch { repository.setBaseTheme(BaseTheme.Custom(rgb)) }
        colorPicker = false
      },
      onDismiss = { colorPicker = false },
    )
  }
}

/**
 * D.25.1 — collapse a stored [BaseTheme] onto one of the four picker
 * options so the radio dialog can highlight the active row. Any
 * `Custom(...)` value (whatever the seed) maps to the picker's
 * `Custom` sentinel.
 */
private fun baseThemeMatch(stored: BaseTheme): BaseTheme = when (stored) {
  is BaseTheme.DefaultAndroid -> BaseTheme.DefaultAndroid
  is BaseTheme.DefaultColors -> BaseTheme.DefaultColors
  is BaseTheme.PureBlack -> BaseTheme.PureBlack
  is BaseTheme.Custom -> BaseTheme.pickerOptions.last()
}

private fun themeLabel(p: ThemePreference): String = when (p) {
  ThemePreference.System -> "Automatic"
  ThemePreference.Light -> "Light"
  ThemePreference.Dark -> "Dark"
}

internal fun baseThemeLabel(b: BaseTheme): String = when (b) {
  is BaseTheme.DefaultAndroid -> "Default Android (Material You)"
  is BaseTheme.DefaultColors -> "Default colors"
  is BaseTheme.PureBlack -> "Pure black"
  is BaseTheme.Custom -> "Custom color"
}

// =============================================================================
// Personalize
// =============================================================================

@Composable
fun SettingsPersonalizeScreen(
  repository: SettingsRepository,
  libraryRepository: com.eight87.tonearm.data.LibraryRepository,
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
    val customTabs by libraryRepository.customTabs().collectAsState(initial = emptyList())
    val genres by libraryRepository.observeGenres().collectAsState(initial = emptyList())
    val artists by libraryRepository.observeArtists().collectAsState(initial = emptyList())
    val albums by libraryRepository.observeAlbums().collectAsState(initial = emptyList())
    val tracks by libraryRepository.observeTracks().collectAsState(initial = emptyList())
    var editorTarget by remember {
      mutableStateOf<com.eight87.tonearm.data.db.CustomTabEntity?>(null)
    }
    var showEditor by remember { mutableStateOf(false) }
    LibraryTabsDialog(
      model = LibraryTabsDialogModel(
        builtIns = LibraryTab.entries.toList(),
        visibleSet = snapshot.libraryTabs.toSet(),
        customTabs = customTabs,
      ),
      onDismiss = { showLibraryTabs = false },
      onSetBuiltInVisibility = { tab, visible ->
        // Reflect visibility into snapshot.libraryTabs while keeping
        // the saved order. Add to / remove from the persisted list.
        val current = snapshot.libraryTabs.toMutableList()
        if (visible) {
          if (tab !in current) current.add(tab)
        } else {
          current.remove(tab)
        }
        scope.launch { repository.setLibraryTabs(current) }
      },
      onReorderBuiltIns = { newOrder ->
        scope.launch { repository.setLibraryTabs(newOrder) }
      },
      onReorderCustomTabs = { orderedIds ->
        scope.launch { libraryRepository.reorderCustomTabs(orderedIds) }
      },
      onAddCustomTab = {
        editorTarget = null
        showEditor = true
      },
      onEditCustomTab = { tab ->
        editorTarget = tab
        showEditor = true
      },
      onDeleteCustomTab = { tab ->
        scope.launch { libraryRepository.deleteCustomTab(tab.id) }
      },
    )
    if (showEditor) {
      val universe = remember(genres, artists, albums, tracks) {
        com.eight87.tonearm.ui.library.FilterUniverse(
          genres = genres.map { it.name }.distinct().sorted(),
          artists = artists.map { it.name }.distinct().sorted(),
          albums = albums.map { it.name }.distinct().sorted(),
          minYear = tracks.mapNotNull { it.year }.minOrNull(),
          maxYear = tracks.mapNotNull { it.year }.maxOrNull(),
        )
      }
      com.eight87.tonearm.ui.library.CustomTabEditorSheet(
        existing = editorTarget,
        universe = universe,
        onDismiss = { showEditor = false },
        onSave = { name, ct, criteria ->
          scope.launch {
            val entity = (editorTarget ?: com.eight87.tonearm.data.db.CustomTabEntity(
              name = name,
              position = 0,
              contentType = ct,
              criteriaJson = "",
            )).copy(
              name = name,
              contentType = ct,
              criteriaJson = com.eight87.tonearm.data.FilterCriteria.toJson(criteria),
            )
            libraryRepository.upsertCustomTab(entity)
          }
          showEditor = false
        },
      )
    }
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

// D.18.3 — `LibraryTabsEditor` was replaced by [LibraryTabsDialog]
// in `LibraryTabsDialog.kt`, which adds custom-tab CRUD plus
// drag-and-drop reorder.

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

@OptIn(UnstableApi::class)
@Composable
fun SettingsAudioScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  onComingSoon: (String) -> Unit,
  snackbarHostState: SnackbarHostState,
  // Phase H.3 / H.4 — wired from `TonearmApp` so the Audio sub-page can
  // host the Sleep timer dialog and launch the System equalizer intent.
  // Optional so existing tests / preview surfaces can keep instantiating
  // without the playback controller; nullable means "fall back to a
  // 'Coming in v1.1' style snackbar".
  playback: com.eight87.tonearm.playback.PlaybackUiController? = null,
) {
  val snapshot by repository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var rgStrategyPicker by remember { mutableStateOf(false) }
  var rgPreampDialog by remember { mutableStateOf(false) }
  var sleepDialog by remember { mutableStateOf(false) }

  // Phase H.3 — observe the timer state so the dialog (and the row
  // subtitle) can render the live countdown without polling.
  val sleepState by (playback?.sleepTimer?.state
    ?: kotlinx.coroutines.flow.MutableStateFlow(
      com.eight87.tonearm.playback.SleepTimerState.Idle,
    )).collectAsState(initial = com.eight87.tonearm.playback.SleepTimerState.Idle)

  val sleepRowSubtitle = when (val s = sleepState) {
    is com.eight87.tonearm.playback.SleepTimerState.Running -> {
      val minutes = (s.remainingMs / 60_000L).coerceAtLeast(0)
      "Active — about ${minutes} min remaining"
    }
    com.eight87.tonearm.playback.SleepTimerState.WaitingForTrackEnd ->
      "Waiting for end of song"
    com.eight87.tonearm.playback.SleepTimerState.Idle ->
      "Pause playback after a delay."
  }

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
    // Phase H.3 — sleep timer row.
    SettingsRowBinding.Picker(
      id = SettingsCatalog.ID_SLEEP_TIMER,
      currentLabel = sleepRowSubtitle,
      onClick = { sleepDialog = true },
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
    // Phase H.4 — system equalizer row.
    SettingsRowBinding.Action(
      id = SettingsCatalog.ID_SYSTEM_EQUALIZER,
      onClick = {
        val sessionId = playback?.audioSessionId?.value
          ?: androidx.media3.common.C.AUDIO_SESSION_ID_UNSET
        val intent = SystemEqualizer.buildIntent(sessionId, context.packageName)
        if (SystemEqualizer.resolves(context, intent)) {
          runCatching { context.startActivity(intent) }
            .onFailure {
              scope.launch {
                snackbarHostState.showSnackbar(
                  "No system equalizer available on this device.",
                )
              }
            }
        } else {
          scope.launch {
            snackbarHostState.showSnackbar(
              "No system equalizer available on this device.",
            )
          }
        }
      },
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

  if (sleepDialog) {
    SleepTimerDialog(
      state = sleepState,
      onStart = { durationMs, waitForEndOfTrack ->
        playback?.sleepTimer?.start(durationMs, waitForEndOfTrack)
        sleepDialog = false
        scope.launch {
          val mins = durationMs / 60_000L
          snackbarHostState.showSnackbar("Sleep timer set for $mins min")
        }
      },
      onCancel = {
        playback?.sleepTimer?.cancel()
      },
      onDismiss = { sleepDialog = false },
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
