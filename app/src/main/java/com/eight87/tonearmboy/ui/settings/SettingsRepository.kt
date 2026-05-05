package com.eight87.tonearmboy.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eight87.tonearmboy.data.settings.EnumSetting
import com.eight87.tonearmboy.data.settings.PreferencesSetting
import com.eight87.tonearmboy.data.settings.Setting
import com.eight87.tonearmboy.data.settings.booleanSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A single colour-scheme choice. */
enum class ColorScheme {
  Dynamic,
  Brand,
  ;

  companion object {
    val Default: ColorScheme = Dynamic

    fun fromStored(raw: String?): ColorScheme =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/**
 * D.20.4 / D.25.1 — base theme picker. Originally a three-way enum
 * (`DefaultAndroid` / `DefaultColors` / `PureBlack`); D.25.1 promoted
 * it to a sealed class so `Custom(seedRgb)` can carry a user-picked
 * primary seed colour from which Material 3 derives the full
 * `ColorScheme`. The album-art tint sits *on top* of this (controlled
 * by the `albumArtTintEnabled` setting on [ThemeSettings]).
 *
 *  - [DefaultAndroid] — Material You / dynamic colour on API 31+,
 *    falls back to the static palette below on older devices.
 *  - [DefaultColors] — the static brand palette regardless of API.
 *  - [PureBlack] — true-black surface family for AMOLED screens. The
 *    primary / secondary / tertiary still come from the dynamic or
 *    brand palette underneath; only `surface` / `background` go black.
 *  - [Custom] — user picked a seed colour via the in-app HSV picker;
 *    `lightColorScheme` / `darkColorScheme` are derived from it.
 *
 * Persisted as a string. The first three serialise as their class
 * names ("DefaultAndroid" / "DefaultColors" / "PureBlack"); [Custom]
 * serialises as `Custom:0xRRGGBB`. Unknown / malformed strings fall
 * back to [Default].
 */
sealed class BaseTheme {
  data object DefaultAndroid : BaseTheme()
  data object DefaultColors : BaseTheme()
  data object PureBlack : BaseTheme()

  /**
   * D.25.1 — custom seed-colour theme. [seedRgb] is a 24-bit RGB value
   * (alpha is implied 0xFF). Stored as a `Long` rather than `Int` so
   * the high bit doesn't sign-extend round-tripping through DataStore.
   */
  data class Custom(val seedRgb: Long) : BaseTheme()

  /** Storage form. Inverse of [fromStored]. */
  fun toStored(): String = when (this) {
    is DefaultAndroid -> "DefaultAndroid"
    is DefaultColors -> "DefaultColors"
    is PureBlack -> "PureBlack"
    is Custom -> "Custom:0x${(seedRgb and 0xFFFFFFL).toString(16).padStart(6, '0').uppercase()}"
  }

  companion object {
    val Default: BaseTheme = DefaultAndroid

    fun fromStored(raw: String?): BaseTheme {
      if (raw == null) return Default
      if (raw.startsWith("Custom:")) {
        val hex = raw.removePrefix("Custom:").removePrefix("0x").removePrefix("0X")
        val parsed = runCatching { hex.toLong(16) }.getOrNull() ?: return Default
        return Custom(parsed and 0xFFFFFFL)
      }
      return when (raw) {
        "DefaultAndroid" -> DefaultAndroid
        "DefaultColors" -> DefaultColors
        "PureBlack" -> PureBlack
        else -> Default
      }
    }

  }
}

/**
 * D.9a.1 — long-press action on the mini-player play button.
 * Persisted as the enum name; default is [SkipNext].
 */
enum class CustomBarAction {
  SkipNext,
  ShuffleToggle,
  RepeatToggle,
  None,
  ;

  companion object {
    val Default: CustomBarAction = SkipNext

    fun fromStored(raw: String?): CustomBarAction =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/**
 * D.9a.2 — secondary action surfaced in the `MediaStyle` notification.
 * Persisted as the enum name; default is [RepeatMode].
 */
enum class CustomNotificationAction {
  RepeatMode,
  Shuffle,
  None,
  ;

  companion object {
    val Default: CustomNotificationAction = RepeatMode

    fun fromStored(raw: String?): CustomNotificationAction =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/**
 * D.9a.4 — queue-build strategy when a track is tapped from a flat list
 * inside a library tab (Songs, Genres detail, custom tabs, etc.).
 */
enum class PlayFromLibrary {
  AllSongs,
  ItemOnly,
  CurrentFilter,
  ;

  companion object {
    val Default: PlayFromLibrary = AllSongs

    fun fromStored(raw: String?): PlayFromLibrary =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/**
 * D.9a.5 — queue-build strategy when a track is tapped from a detail
 * surface (album / artist / playlist).
 */
enum class PlayFromItemDetails {
  ShownItem,
  Album,
  Artist,
  ;

  companion object {
    val Default: PlayFromItemDetails = ShownItem

    fun fromStored(raw: String?): PlayFromItemDetails =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/**
 * D.9b.1 — ReplayGain strategy. `Off` is the safe default — never
 * change a user's volume unless they opt in.
 *
 *  - `Off` — no gain change
 *  - `Track` — `REPLAYGAIN_TRACK_GAIN`
 *  - `Album` — `REPLAYGAIN_ALBUM_GAIN`
 *  - `Smart` — album mode when the queue covers ≥75% of an album,
 *    track mode otherwise
 */
enum class ReplayGainStrategy {
  Off,
  Track,
  Album,
  Smart,
  ;

  companion object {
    val Default: ReplayGainStrategy = Off

    fun fromStored(raw: String?): ReplayGainStrategy =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/**
 * D.9b.3 — album cover loading policy.
 *
 *  - `Balanced` (default) — Coil 3 fits the cell size and loads with
 *    a low-priority dispatcher
 *  - `On` — always load full-size covers, eager
 *  - `Off` — never load; render the music-note placeholder for every
 *    cell (text-only fallback)
 */
enum class AlbumCoversMode {
  Balanced,
  On,
  Off,
  ;

  companion object {
    val Default: AlbumCoversMode = Balanced

    fun fromStored(raw: String?): AlbumCoversMode =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/**
 * D.9c.1 — separator characters / strings used to split multi-valued
 * artist / album_artist / genre tags during scan. The user picks any
 * subset; the splitter applies the selected ones with longest-match
 * precedence. Defaults match Auxio: semicolon and forward-slash.
 */
enum class MultiValueSeparator(val token: String) {
  Semicolon(";"),
  Slash("/"),
  Comma(","),
  Ampersand("&"),
  Feat("feat."),
  Ft("ft."),
  ;

  companion object {
    val Default: Set<MultiValueSeparator> = setOf(Semicolon, Slash)

    fun fromStored(raw: String?): Set<MultiValueSeparator> {
      if (raw == null) return Default
      // Empty string means "user explicitly disabled all separators".
      if (raw.isBlank()) return emptySet()
      return raw.split(",")
        .mapNotNull { name -> entries.firstOrNull { it.name == name } }
        .toSet()
    }

    fun toStored(value: Set<MultiValueSeparator>): String =
      value.sortedBy { it.ordinal }.joinToString(",") { it.name }
  }
}

/**
 * D.17.3.1 — top-level music-source strategy. The user toggles between
 * "let MediaStore decide" (the Android-native default — fastest, picks
 * up everything the system index already knows about) and "I will pick
 * folders explicitly" (slower SAF-tree walk, but correct for SD-card
 * music outside the MediaStore default scope).
 *
 * Persisted as the enum name. Default is [System] so a fresh install
 * shows music immediately without UI configuration; the
 * [SettingsRepository.firstLaunchInitialise] hook fixes that on disk
 * on first start.
 */
enum class MusicSourceMode {
  System,
  FilePicker,
  ;

  companion object {
    val Default: MusicSourceMode = System

    fun fromStored(raw: String?): MusicSourceMode =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/** Library tabs in canonical order. */
enum class LibraryTab {
  Songs,
  Albums,
  Artists,
  Genres,
  Playlists,
  ;

  companion object {
    val DefaultOrder: List<LibraryTab> = entries
  }
}

/** Sort axis applied per library tab. */
enum class SortKey {
  Name,
  Artist,
  Album,
  Date,
  Duration,
  DateAdded,
  ;

  companion object {
    val Default: SortKey = Name

    fun fromStored(raw: String?): SortKey =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

enum class SortDirection {
  Ascending,
  Descending,
  ;

  companion object {
    val Default: SortDirection = Ascending

    fun fromStored(raw: String?): SortDirection =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

data class TabSort(val key: SortKey, val direction: SortDirection) {
  companion object {
    val Default: TabSort = TabSort(SortKey.Default, SortDirection.Default)
  }
}

/**
 * D.28.1 — per-tab list-vs-tile view mode. The user toggles this from
 * the top app bar; every library tab keeps its own preference so a
 * tap on Songs doesn't flip Albums. Defaults match the pre-D.28
 * shapes: Songs / Artists / Genres / Playlists rendered as lists,
 * Albums rendered as a tile grid.
 */
enum class ViewMode {
  List,
  Tile,
  // G+ — pinned two-column tile grid. Tile keeps the adaptive
  // 160-dp-min layout (3+ columns on phones); TwoColumn forces exactly
  // two columns for larger artwork on every tab.
  TwoColumn,
  ;

  /** Cycle List → Tile → TwoColumn → List. */
  fun toggle(): ViewMode = when (this) {
    List -> Tile
    Tile -> TwoColumn
    TwoColumn -> List
  }

  companion object {
    fun fromStored(raw: String?): ViewMode? =
      entries.firstOrNull { it.name == raw }

    /**
     * Pre-D.28 shape per tab. Used as the fallback when the per-tab
     * preference key is absent (fresh install, never-toggled tab).
     * Albums was already a tile grid; everything else was a list.
     */
    fun defaultFor(tab: LibraryTab): ViewMode = when (tab) {
      LibraryTab.Albums -> Tile
      else -> List
    }
  }
}

/**
 * Single source of truth for user-configurable settings. Backed by the
 * shared `Context.tonearmboyDataStore` Preferences file so [ThemePreferenceStore]
 * and this repository read/write the same on-disk state.
 *
 * The intent: each screen takes the narrow facet it needs
 * ([ThemeSettings], [PlaybackSettings], [LibrarySettings],
 * [MusicSourcesSettings], [TabLayoutSettings]) and reads / writes
 * one [com.eight87.tonearmboy.data.settings.Setting] handle per key it
 * actually consumes.
 */
class SettingsRepository(private val context: Context) :
  com.eight87.tonearmboy.data.ScanConfigSource,
  ThemeSettings,
  PlaybackSettings,
  LibrarySettings,
  MusicSourcesSettings,
  TabLayoutSettings {

  // R.A.5 — implement ScanConfigSource so the data layer's
  // LibraryRepository can depend on the neutral interface (defined in
  // `data/`) rather than reaching back into `ui.settings`. Both
  // properties project facet Flows into the data-layer shape;
  // storage is unchanged.
  override val multiValueSeparatorTokens: kotlinx.coroutines.flow.Flow<Set<String>>
    get() = multiValueSeparators.flow.map { set -> set.map { it.token }.toSet() }

  override val musicSourceScope: kotlinx.coroutines.flow.Flow<com.eight87.tonearmboy.data.MusicSourceScope>
    get() = kotlinx.coroutines.flow.combine(musicSourceMode.flow, musicSourceUris.flow) { mode, uris ->
      com.eight87.tonearmboy.data.MusicSourceScope(
        useFilePicker = mode == MusicSourceMode.FilePicker,
        treeUris = uris,
      )
    }


  private val store: DataStore<Preferences> = context.tonearmboyDataStore

  // R.B.2 — every flat key expressed as a Setting<T>. R.B.3 marked
  // them `override` to satisfy the five facet interfaces. R.B.5
  // retired the cross-cutting `Flow<SettingsSnapshot>` projection;
  // each consumer now subscribes to the narrow Setting<T>.flow it
  // actually needs.
  //
  // Per-key default fallbacks live alongside each Setting's `read`
  // transform — that's the single source of truth for "what value
  // should consumers see when no key has been written yet."

  // R.B.3 — facet members. Each Setting<T> handle satisfies one
  // facet property; SettingsRepository implements all five facets so
  // a consumer can take the narrow surface they need.

  // --- ThemeSettings ---
  override val theme: Setting<ThemePreference> = EnumSetting(
    store, ThemePreferenceStore.KEY_THEME_MODE, ThemePreference.Companion::fromStored,
  )
  override val colorScheme: Setting<ColorScheme> = EnumSetting(
    store, KEY_COLOR_SCHEME, ColorScheme.Companion::fromStored,
  )
  override val blackTheme: Setting<Boolean> = booleanSetting(
    store, KEY_BLACK_THEME, false,
  )
  override val baseTheme: Setting<BaseTheme> = PreferencesSetting(
    store, KEY_BASE_THEME,
    read = { BaseTheme.fromStored(it) },
    write = { it.toStored() },
  )
  override val albumArtTintEnabled: Setting<Boolean> = booleanSetting(
    store, KEY_ALBUM_ART_TINT_ENABLED, true,
  )

  // --- PlaybackSettings ---
  override val rememberShuffle: Setting<Boolean> = booleanSetting(
    store, KEY_REMEMBER_SHUFFLE, false,
  )
  override val headsetAutoplay: Setting<Boolean> = booleanSetting(
    store, KEY_HEADSET_AUTOPLAY, false,
  )
  override val rewindBeforeSkipBack: Setting<Boolean> = booleanSetting(
    store, KEY_REWIND_BEFORE_SKIP, true,
  )
  override val pauseOnRepeat: Setting<Boolean> = booleanSetting(
    store, KEY_PAUSE_ON_REPEAT, false,
  )
  override val rememberPause: Setting<Boolean> = booleanSetting(
    store, KEY_REMEMBER_PAUSE, false,
  )
  override val customBarAction: Setting<CustomBarAction> = EnumSetting(
    store, KEY_CUSTOM_BAR_ACTION, CustomBarAction.Companion::fromStored,
  )
  override val customNotificationAction: Setting<CustomNotificationAction> = EnumSetting(
    store, KEY_CUSTOM_NOTIFICATION_ACTION, CustomNotificationAction.Companion::fromStored,
  )
  override val playFromLibrary: Setting<PlayFromLibrary> = EnumSetting(
    store, KEY_PLAY_FROM_LIBRARY, PlayFromLibrary.Companion::fromStored,
  )
  override val playFromItemDetails: Setting<PlayFromItemDetails> = EnumSetting(
    store, KEY_PLAY_FROM_ITEM_DETAILS, PlayFromItemDetails.Companion::fromStored,
  )
  override val replayGainStrategy: Setting<ReplayGainStrategy> = EnumSetting(
    store, KEY_REPLAYGAIN_STRATEGY, ReplayGainStrategy.Companion::fromStored,
  )
  /**
   * D.9b.2 — clamping into [-15, +15] dB and snapping to 0.1 dB
   * lives inside the `write` transform so direct writes (e.g. tests)
   * cannot bypass it.
   */
  override val replayGainPreampDb: Setting<Float> = PreferencesSetting(
    store, KEY_REPLAYGAIN_PREAMP,
    read = { (it ?: 0f)
      .coerceIn(REPLAYGAIN_PREAMP_MIN_DB, REPLAYGAIN_PREAMP_MAX_DB) },
    write = { value ->
      val clamped = value.coerceIn(REPLAYGAIN_PREAMP_MIN_DB, REPLAYGAIN_PREAMP_MAX_DB)
      (Math.round(clamped * 10.0).toFloat()) / 10f
    },
  )

  // --- LibrarySettings ---
  override val automaticReloading: Setting<Boolean> = booleanSetting(
    store, KEY_AUTOMATIC_RELOADING, false,
  )
  override val intelligentSorting: Setting<Boolean> = booleanSetting(
    store, KEY_INTELLIGENT_SORTING, true,
  )
  override val hideCollaborators: Setting<Boolean> = booleanSetting(
    store, KEY_HIDE_COLLABORATORS, false,
  )
  override val autoDiscoverAlbumArt: Setting<Boolean> = booleanSetting(
    store, KEY_AUTO_DISCOVER_ALBUM_ART, false,
  )
  override val forceSquareCovers: Setting<Boolean> = booleanSetting(
    store, KEY_FORCE_SQUARE_COVERS, false,
  )
  override val albumCoversMode: Setting<AlbumCoversMode> = EnumSetting(
    store, KEY_ALBUM_COVERS_MODE, AlbumCoversMode.Companion::fromStored,
  )
  /**
   * D.9c.1 — empty set is a legal value (user disables all
   * splitting) and is encoded as the empty string; a missing key
   * falls back to [MultiValueSeparator.Default].
   */
  override val multiValueSeparators: Setting<Set<MultiValueSeparator>> = PreferencesSetting(
    store, KEY_MULTI_VALUE_SEPARATORS,
    read = { MultiValueSeparator.fromStored(it) },
    write = { MultiValueSeparator.toStored(it) },
  )

  // --- MusicSourcesSettings ---
  override val musicSourceMode: Setting<MusicSourceMode> = EnumSetting(
    store, KEY_MUSIC_SOURCE_MODE, MusicSourceMode.Companion::fromStored,
  )
  override val musicSourceUris: Setting<Set<String>> = PreferencesSetting(
    store, KEY_MUSIC_SOURCE_URIS,
    read = { it ?: emptySet() },
    write = { it },
  )

  // --- TabLayoutSettings ---
  override val libraryTabs: Setting<List<LibraryTab>> = PreferencesSetting(
    store, KEY_LIBRARY_TABS,
    read = { LibraryTabOrder.fromStored(it) },
    write = { LibraryTabOrder.toStored(it) },
  )
  /** R.B.3 — Per-tab `TabSort` is a composite of two Preferences keys. */
  override fun tabSortSetting(tab: LibraryTab): Setting<TabSort> = object : Setting<TabSort> {
    override val flow: Flow<TabSort> = store.data.map { prefs ->
      TabSort(
        key = SortKey.fromStored(prefs[sortKeyFor(tab)]),
        direction = SortDirection.fromStored(prefs[sortDirFor(tab)]),
      )
    }
    override suspend fun set(value: TabSort) {
      store.edit {
        it[sortKeyFor(tab)] = value.key.name
        it[sortDirFor(tab)] = value.direction.name
      }
    }
  }
  /** R.B.3 — Per-tab view-mode falls back to the tab's pre-D.28 default. */
  override fun viewModeSetting(tab: LibraryTab): Setting<ViewMode> = PreferencesSetting(
    store = store,
    key = viewModeKeyFor(tab),
    read = { ViewMode.fromStored(it) ?: ViewMode.defaultFor(tab) },
    write = { it.name },
  )
  /** R.B.3 — Per-custom-tab view mode; default supplied by the caller. */
  override fun customTabViewModeSetting(id: Long, default: ViewMode): Setting<ViewMode> =
    PreferencesSetting(
      store = store,
      key = customTabViewModeKey(id),
      read = { ViewMode.fromStored(it) ?: default },
      write = { it.name },
    )

  // --- mutators -------------------------------------------------------------
  // R.B.2 — every flat setter delegates to the matching Setting<T> handle
  // declared above. The hand-rolled `store.edit { it[KEY] = encode(v) }`
  // boilerplate now lives once inside PreferencesSetting / EnumSetting.

  suspend fun setTheme(value: ThemePreference) = theme.set(value)
  suspend fun setColorScheme(value: ColorScheme) = colorScheme.set(value)
  suspend fun setBlackTheme(value: Boolean) = blackTheme.set(value)
  suspend fun setRememberShuffle(value: Boolean) = rememberShuffle.set(value)
  suspend fun setLibraryTabs(value: List<LibraryTab>) = libraryTabs.set(value)
  suspend fun setIntelligentSorting(value: Boolean) = intelligentSorting.set(value)
  suspend fun setForceSquareCovers(value: Boolean) = forceSquareCovers.set(value)
  suspend fun setHeadsetAutoplay(value: Boolean) = headsetAutoplay.set(value)
  suspend fun setRewindBeforeSkipBack(value: Boolean) = rewindBeforeSkipBack.set(value)
  suspend fun setRememberPause(value: Boolean) = rememberPause.set(value)
  suspend fun setAutoDiscoverAlbumArt(value: Boolean) = autoDiscoverAlbumArt.set(value)
  suspend fun setCustomBarAction(value: CustomBarAction) = customBarAction.set(value)
  suspend fun setCustomNotificationAction(value: CustomNotificationAction) =
    customNotificationAction.set(value)
  suspend fun setPauseOnRepeat(value: Boolean) = pauseOnRepeat.set(value)
  suspend fun setPlayFromLibrary(value: PlayFromLibrary) = playFromLibrary.set(value)
  suspend fun setPlayFromItemDetails(value: PlayFromItemDetails) = playFromItemDetails.set(value)
  suspend fun setHideCollaborators(value: Boolean) = hideCollaborators.set(value)
  suspend fun setReplayGainStrategy(value: ReplayGainStrategy) = replayGainStrategy.set(value)
  suspend fun setReplayGainPreampDb(value: Float) = replayGainPreampDb.set(value)
  suspend fun setAlbumCoversMode(value: AlbumCoversMode) = albumCoversMode.set(value)
  suspend fun setMultiValueSeparators(value: Set<MultiValueSeparator>) =
    multiValueSeparators.set(value)
  suspend fun setMusicSourceMode(value: MusicSourceMode) = musicSourceMode.set(value)
  suspend fun setAutomaticReloading(value: Boolean) = automaticReloading.set(value)
  suspend fun setBaseTheme(value: BaseTheme) = baseTheme.set(value)
  suspend fun setAlbumArtTintEnabled(value: Boolean) = albumArtTintEnabled.set(value)
  suspend fun setTabSort(tab: LibraryTab, value: TabSort) = tabSortSetting(tab).set(value)
  suspend fun setViewModeFor(tab: LibraryTab, mode: ViewMode) = viewModeSetting(tab).set(mode)
  suspend fun setCustomTabViewMode(id: Long, mode: ViewMode) =
    customTabViewModeSetting(id, ViewMode.List).set(mode)

  // --- TabLayoutSettings: whole-map projection + legacy Flow factories ----
  /**
   * Hot Flow of every tab's resolved view mode. Tabs without a stored
   * preference fall back to [ViewMode.defaultFor]. Emitted as a `Map`
   * so subscribers can switch tabs without re-subscribing per-tab.
   * Backed by raw store access because the projection depends on the
   * full [LibraryTab.entries] enumeration; the per-tab fallback
   * already lives in [viewModeSetting].
   */
  override val viewModes: Flow<Map<LibraryTab, ViewMode>> = store.data.map { prefs ->
    LibraryTab.entries.associateWith { tab ->
      ViewMode.fromStored(prefs[viewModeKeyFor(tab)]) ?: ViewMode.defaultFor(tab)
    }
  }

  // R.B.3 — legacy Flow factories kept for the LibraryScreen consumers
  // R.B.4 will migrate. Deleted once every caller takes the
  // TabLayoutSettings facet directly.
  fun tabSort(tab: LibraryTab): Flow<TabSort> = tabSortSetting(tab).flow
  fun viewModeFor(tab: LibraryTab): Flow<ViewMode> = viewModeSetting(tab).flow
  fun customTabViewMode(id: Long, default: ViewMode): Flow<ViewMode> =
    customTabViewModeSetting(id, default).flow

  // --- MusicSourcesSettings: helpers ---------------------------------------

  /** Append a tree URI. Idempotent — no-op when the URI is already in the set. */
  override suspend fun addMusicSourceUri(uri: String) {
    store.edit { prefs ->
      val current = prefs[KEY_MUSIC_SOURCE_URIS] ?: emptySet()
      prefs[KEY_MUSIC_SOURCE_URIS] = current + uri
    }
  }

  /** Remove a tree URI. No-op when missing. */
  override suspend fun removeMusicSourceUri(uri: String) {
    store.edit { prefs ->
      val current = prefs[KEY_MUSIC_SOURCE_URIS] ?: emptySet()
      prefs[KEY_MUSIC_SOURCE_URIS] = current - uri
    }
  }

  /** Replace the full set. Used by tests and the deduplicating helper. */
  suspend fun setMusicSourceUris(value: Set<String>) = musicSourceUris.set(value)

  /**
   * D.17.3.6 — first-launch normalisation. Runs once per process
   * start (cheap; idempotent because we only write when the key is
   * absent). The user-facing intent: never show an empty library on a
   * fresh install. By writing [MusicSourceMode.System] explicitly we
   * also avoid the subtle bug where a future code change to the enum
   * default would silently shift existing users. Stays as raw store
   * access because `Setting.set` always writes.
   */
  override suspend fun firstLaunchInitialise() {
    store.edit { prefs ->
      if (prefs[KEY_MUSIC_SOURCE_MODE] == null) {
        prefs[KEY_MUSIC_SOURCE_MODE] = MusicSourceMode.Default.name
      }
    }
  }

  companion object {
    internal val KEY_COLOR_SCHEME = stringPreferencesKey("color_scheme")
    internal val KEY_BLACK_THEME = booleanPreferencesKey("black_theme")
    internal val KEY_REMEMBER_SHUFFLE = booleanPreferencesKey("remember_shuffle")
    internal val KEY_LIBRARY_TABS = stringPreferencesKey("library_tabs")
    internal val KEY_INTELLIGENT_SORTING = booleanPreferencesKey("intelligent_sorting")
    internal val KEY_FORCE_SQUARE_COVERS = booleanPreferencesKey("force_square_covers")
    internal val KEY_HEADSET_AUTOPLAY = booleanPreferencesKey("headset_autoplay")
    internal val KEY_REWIND_BEFORE_SKIP = booleanPreferencesKey("rewind_before_skip")
    internal val KEY_REMEMBER_PAUSE = booleanPreferencesKey("remember_pause")
    internal val KEY_AUTO_DISCOVER_ALBUM_ART = booleanPreferencesKey("auto_discover_album_art")
    internal val KEY_CUSTOM_BAR_ACTION = stringPreferencesKey("custom_bar_action")
    internal val KEY_CUSTOM_NOTIFICATION_ACTION = stringPreferencesKey("custom_notification_action")
    internal val KEY_PAUSE_ON_REPEAT = booleanPreferencesKey("pause_on_repeat")
    internal val KEY_PLAY_FROM_LIBRARY = stringPreferencesKey("play_from_library")
    internal val KEY_PLAY_FROM_ITEM_DETAILS = stringPreferencesKey("play_from_item_details")
    internal val KEY_HIDE_COLLABORATORS = booleanPreferencesKey("hide_collaborators")
    internal val KEY_REPLAYGAIN_STRATEGY = stringPreferencesKey("replaygain_strategy")
    internal val KEY_REPLAYGAIN_PREAMP = floatPreferencesKey("replaygain_preamp_db")
    internal val KEY_ALBUM_COVERS_MODE = stringPreferencesKey("album_covers_mode")
    internal val KEY_MULTI_VALUE_SEPARATORS = stringPreferencesKey("multi_value_separators")
    internal val KEY_MUSIC_SOURCE_URIS = stringSetPreferencesKey("music_source_uris")
    internal val KEY_MUSIC_SOURCE_MODE = stringPreferencesKey("music_source_mode")
    internal val KEY_AUTOMATIC_RELOADING = booleanPreferencesKey("automatic_reloading")
    internal val KEY_BASE_THEME = stringPreferencesKey("base_theme")
    internal val KEY_ALBUM_ART_TINT_ENABLED = booleanPreferencesKey("album_art_tint_enabled")

    /** D.9b.2 — pre-amp slider bounds, fixed at [-15, +15] dB. */
    const val REPLAYGAIN_PREAMP_MIN_DB: Float = -15f
    const val REPLAYGAIN_PREAMP_MAX_DB: Float = 15f
    /** Granularity of the slider — one step is 0.1 dB. */
    const val REPLAYGAIN_PREAMP_STEP_DB: Float = 0.1f

    internal fun sortKeyFor(tab: LibraryTab) = stringPreferencesKey("sort_key_${tab.name}")
    internal fun sortDirFor(tab: LibraryTab) = stringPreferencesKey("sort_dir_${tab.name}")
    internal fun viewModeKeyFor(tab: LibraryTab) = stringPreferencesKey("view_mode_${tab.name}")
    internal fun customTabViewModeKey(id: Long) = stringPreferencesKey("view_mode_custom_$id")
  }
}
