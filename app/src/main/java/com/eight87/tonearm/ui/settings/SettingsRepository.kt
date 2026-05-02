package com.eight87.tonearm.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
 * by [SettingsSnapshot.albumArtTintEnabled]).
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

    /**
     * The four base-theme variants surfaced by the picker dialog. Used
     * in place of the old `enum.entries` since a sealed class can't
     * enumerate its leaves automatically. The [Custom] entry here is a
     * sentinel — the dialog displays it, and tapping opens the colour
     * picker; the actual stored value carries the user-picked seed.
     */
    val pickerOptions: List<BaseTheme> = listOf(
      DefaultAndroid,
      DefaultColors,
      PureBlack,
      Custom(seedRgb = 0xFF6750A4L and 0xFFFFFFL), // Material 3 default purple
    )
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
 * Aggregated settings snapshot consumed by the UI. Kept as a value type
 * so a screen can render every section from one Flow without subscribing
 * to a dozen individual keys.
 */
data class SettingsSnapshot(
  val theme: ThemePreference,
  val colorScheme: ColorScheme,
  val blackTheme: Boolean,
  val rememberShuffle: Boolean,
  val libraryTabs: List<LibraryTab>,
  val intelligentSorting: Boolean,
  val forceSquareCovers: Boolean,
  val headsetAutoplay: Boolean,
  val rewindBeforeSkipBack: Boolean,
  val rememberPause: Boolean,
  val autoDiscoverAlbumArt: Boolean,
  val customBarAction: CustomBarAction,
  val customNotificationAction: CustomNotificationAction,
  val pauseOnRepeat: Boolean,
  val playFromLibrary: PlayFromLibrary,
  val playFromItemDetails: PlayFromItemDetails,
  val hideCollaborators: Boolean,
  val replayGainStrategy: ReplayGainStrategy,
  val replayGainPreampDb: Float,
  val albumCoversMode: AlbumCoversMode,
  val multiValueSeparators: Set<MultiValueSeparator>,
  /**
   * D.9d.1 — set of persisted SAF tree URIs the library scanner walks.
   * Empty means "use the legacy MediaStore default scope". Stored as
   * Strings so the snapshot stays equality-stable across emissions.
   */
  val musicSourceUris: Set<String>,
  /**
   * D.17.3.1 — chosen music-source strategy. [MusicSourceMode.System]
   * means the scanner queries MediaStore directly with no folder
   * scoping; [MusicSourceMode.FilePicker] means walk the persisted
   * SAF tree URIs in [musicSourceUris]. The two are kept independent
   * — the URI set is preserved when toggling to System so flipping
   * back doesn't lose the user's folder choices.
   */
  val musicSourceMode: MusicSourceMode,
  /**
   * D.9d.2 — when true the [LibraryWatcherService] is running with a
   * sticky low-priority notification, observing MediaStore and each
   * configured source URI for changes and enqueuing a debounced
   * `LibraryRescanWorker` on change. Default is **off** because a
   * foreground service costs battery; the user opts in.
   */
  val automaticReloading: Boolean,
  /**
   * D.20.4 — base-theme foundation, picked in Look and Feel.
   * Default is [BaseTheme.DefaultAndroid] (Material You).
   */
  val baseTheme: BaseTheme,
  /**
   * D.20.4 — when true, the chrome ColorScheme is biased toward the
   * playing track's `darkMutedSwatch` (or `darkVibrantSwatch` if
   * muted is null). Default is **on** because the visual is the
   * point of D.8b. Sits on top of [baseTheme]; turning it off
   * collapses to the foundation.
   */
  val albumArtTintEnabled: Boolean,
) {
  companion object {
    val Default: SettingsSnapshot = SettingsSnapshot(
      theme = ThemePreference.Default,
      colorScheme = ColorScheme.Default,
      blackTheme = false,
      rememberShuffle = false,
      libraryTabs = LibraryTab.DefaultOrder,
      intelligentSorting = true,
      forceSquareCovers = false,
      headsetAutoplay = false,
      rewindBeforeSkipBack = true,
      rememberPause = false,
      autoDiscoverAlbumArt = false,
      customBarAction = CustomBarAction.Default,
      customNotificationAction = CustomNotificationAction.Default,
      pauseOnRepeat = false,
      playFromLibrary = PlayFromLibrary.Default,
      playFromItemDetails = PlayFromItemDetails.Default,
      hideCollaborators = false,
      replayGainStrategy = ReplayGainStrategy.Default,
      replayGainPreampDb = 0f,
      albumCoversMode = AlbumCoversMode.Default,
      multiValueSeparators = MultiValueSeparator.Default,
      musicSourceUris = emptySet(),
      musicSourceMode = MusicSourceMode.Default,
      automaticReloading = false,
      baseTheme = BaseTheme.Default,
      albumArtTintEnabled = true,
    )
  }
}

/**
 * Single source of truth for user-configurable settings. Backed by the
 * shared `Context.tonearmDataStore` Preferences file so [ThemePreferenceStore]
 * and this repository read/write the same on-disk state.
 *
 * The intent: each screen subscribes once to [snapshot] and writes via
 * the `set*` mutators. Per-tab sort state lives in its own [tabSort] /
 * [setTabSort] pair because the value depends on a tab key.
 */
class SettingsRepository(private val context: Context) {

  private val store: DataStore<Preferences> = context.tonearmDataStore

  val snapshot: Flow<SettingsSnapshot> = store.data.map { prefs -> prefs.toSnapshot() }

  // --- mutators -------------------------------------------------------------

  suspend fun setTheme(value: ThemePreference) {
    store.edit { it[ThemePreferenceStore.KEY_THEME_MODE] = value.name }
  }

  suspend fun setColorScheme(value: ColorScheme) {
    store.edit { it[KEY_COLOR_SCHEME] = value.name }
  }

  suspend fun setBlackTheme(value: Boolean) {
    store.edit { it[KEY_BLACK_THEME] = value }
  }

  suspend fun setRememberShuffle(value: Boolean) {
    store.edit { it[KEY_REMEMBER_SHUFFLE] = value }
  }

  suspend fun setLibraryTabs(value: List<LibraryTab>) {
    store.edit { it[KEY_LIBRARY_TABS] = value.joinToString(",") { tab -> tab.name } }
  }

  suspend fun setIntelligentSorting(value: Boolean) {
    store.edit { it[KEY_INTELLIGENT_SORTING] = value }
  }

  suspend fun setForceSquareCovers(value: Boolean) {
    store.edit { it[KEY_FORCE_SQUARE_COVERS] = value }
  }

  suspend fun setHeadsetAutoplay(value: Boolean) {
    store.edit { it[KEY_HEADSET_AUTOPLAY] = value }
  }

  suspend fun setRewindBeforeSkipBack(value: Boolean) {
    store.edit { it[KEY_REWIND_BEFORE_SKIP] = value }
  }

  suspend fun setRememberPause(value: Boolean) {
    store.edit { it[KEY_REMEMBER_PAUSE] = value }
  }

  suspend fun setAutoDiscoverAlbumArt(value: Boolean) {
    store.edit { it[KEY_AUTO_DISCOVER_ALBUM_ART] = value }
  }

  suspend fun setCustomBarAction(value: CustomBarAction) {
    store.edit { it[KEY_CUSTOM_BAR_ACTION] = value.name }
  }

  suspend fun setCustomNotificationAction(value: CustomNotificationAction) {
    store.edit { it[KEY_CUSTOM_NOTIFICATION_ACTION] = value.name }
  }

  suspend fun setPauseOnRepeat(value: Boolean) {
    store.edit { it[KEY_PAUSE_ON_REPEAT] = value }
  }

  suspend fun setPlayFromLibrary(value: PlayFromLibrary) {
    store.edit { it[KEY_PLAY_FROM_LIBRARY] = value.name }
  }

  suspend fun setPlayFromItemDetails(value: PlayFromItemDetails) {
    store.edit { it[KEY_PLAY_FROM_ITEM_DETAILS] = value.name }
  }

  suspend fun setHideCollaborators(value: Boolean) {
    store.edit { it[KEY_HIDE_COLLABORATORS] = value }
  }

  suspend fun setReplayGainStrategy(value: ReplayGainStrategy) {
    store.edit { it[KEY_REPLAYGAIN_STRATEGY] = value.name }
  }

  /**
   * D.9b.2 — clamp into [-15, +15] dB and round to the nearest 0.1 dB
   * step before persisting, so the slider can use the same step grid
   * without a separate normalization pass.
   */
  suspend fun setReplayGainPreampDb(value: Float) {
    val clamped = value.coerceIn(REPLAYGAIN_PREAMP_MIN_DB, REPLAYGAIN_PREAMP_MAX_DB)
    val snapped = (Math.round(clamped * 10.0).toFloat()) / 10f
    store.edit { it[KEY_REPLAYGAIN_PREAMP] = snapped }
  }

  suspend fun setAlbumCoversMode(value: AlbumCoversMode) {
    store.edit { it[KEY_ALBUM_COVERS_MODE] = value.name }
  }

  /**
   * D.9c.1 — persist the selected separator set. The empty set is a
   * legal value (user disables all splitting) and is encoded as the
   * empty string; a missing key falls back to [MultiValueSeparator.Default].
   */
  suspend fun setMultiValueSeparators(value: Set<MultiValueSeparator>) {
    store.edit { it[KEY_MULTI_VALUE_SEPARATORS] = MultiValueSeparator.toStored(value) }
  }

  /**
   * Hot Flow of the separator set. Used by [com.eight87.tonearm.data.LibraryRepository]
   * during a scan; the scan reads the latest value once, splits, then
   * persists. Toggling at runtime does *not* auto-rescan — the UI
   * surfaces a snackbar prompting the user to run "Rescan music".
   */
  val multiValueSeparators: Flow<Set<MultiValueSeparator>> = store.data.map {
    MultiValueSeparator.fromStored(it[KEY_MULTI_VALUE_SEPARATORS])
  }

  // --- D.9d.1: music sources -----------------------------------------------

  /**
   * Hot Flow of the configured tree URIs (as Strings). The scanner reads
   * this on every scan; subscribers in the UI render the list of rows.
   * An empty set means "scan MediaStore using the legacy default" (the
   * existing behaviour, preserved for backward compatibility).
   */
  val musicSourceUris: Flow<Set<String>> = store.data.map {
    it[KEY_MUSIC_SOURCE_URIS] ?: emptySet()
  }

  /** Append a tree URI. Idempotent — no-op when the URI is already in the set. */
  suspend fun addMusicSourceUri(uri: String) {
    store.edit { prefs ->
      val current = prefs[KEY_MUSIC_SOURCE_URIS] ?: emptySet()
      prefs[KEY_MUSIC_SOURCE_URIS] = current + uri
    }
  }

  /** Remove a tree URI. No-op when missing. */
  suspend fun removeMusicSourceUri(uri: String) {
    store.edit { prefs ->
      val current = prefs[KEY_MUSIC_SOURCE_URIS] ?: emptySet()
      prefs[KEY_MUSIC_SOURCE_URIS] = current - uri
    }
  }

  /** Replace the full set. Used by tests and the deduplicating helper. */
  suspend fun setMusicSourceUris(value: Set<String>) {
    store.edit { it[KEY_MUSIC_SOURCE_URIS] = value }
  }

  // --- D.17.3.1: music-source mode (System vs FilePicker) ------------------

  /**
   * Hot Flow of the persisted [MusicSourceMode]. A missing key returns
   * [MusicSourceMode.Default] — the [firstLaunchInitialise] hook
   * normally writes the default on first start so this fallback only
   * matters for tests / repos that bypass the activity.
   */
  val musicSourceMode: Flow<MusicSourceMode> = store.data.map {
    MusicSourceMode.fromStored(it[KEY_MUSIC_SOURCE_MODE])
  }

  suspend fun setMusicSourceMode(value: MusicSourceMode) {
    store.edit { it[KEY_MUSIC_SOURCE_MODE] = value.name }
  }

  /**
   * D.17.3.6 — first-launch normalisation. Runs once per process
   * start (cheap; idempotent because we only write when the key is
   * absent). The user-facing intent: never show an empty library on a
   * fresh install. By writing [MusicSourceMode.System] explicitly we
   * also avoid the subtle bug where a future code change to the enum
   * default would silently shift existing users.
   */
  suspend fun firstLaunchInitialise() {
    store.edit { prefs ->
      if (prefs[KEY_MUSIC_SOURCE_MODE] == null) {
        prefs[KEY_MUSIC_SOURCE_MODE] = MusicSourceMode.Default.name
      }
    }
  }

  // --- D.9d.2: automatic reloading toggle ----------------------------------

  val automaticReloading: Flow<Boolean> = store.data.map {
    it[KEY_AUTOMATIC_RELOADING] ?: SettingsSnapshot.Default.automaticReloading
  }

  suspend fun setAutomaticReloading(value: Boolean) {
    store.edit { it[KEY_AUTOMATIC_RELOADING] = value }
  }

  // --- D.20.4: base theme + album-art tint --------------------------------

  suspend fun setBaseTheme(value: BaseTheme) {
    store.edit { it[KEY_BASE_THEME] = value.toStored() }
  }

  suspend fun setAlbumArtTintEnabled(value: Boolean) {
    store.edit { it[KEY_ALBUM_ART_TINT_ENABLED] = value }
  }

  /**
   * Hot Flow of [SettingsSnapshot.hideCollaborators]; used by
   * [com.eight87.tonearm.data.LibraryRepository] to filter the artists
   * Flow without re-scanning the library when the user flips the toggle.
   */
  val hideCollaborators: Flow<Boolean> = store.data.map {
    it[KEY_HIDE_COLLABORATORS] ?: SettingsSnapshot.Default.hideCollaborators
  }

  // --- per-tab sort ---------------------------------------------------------

  fun tabSort(tab: LibraryTab): Flow<TabSort> = store.data.map { prefs ->
    TabSort(
      key = SortKey.fromStored(prefs[sortKeyFor(tab)]),
      direction = SortDirection.fromStored(prefs[sortDirFor(tab)]),
    )
  }

  suspend fun setTabSort(tab: LibraryTab, value: TabSort) {
    store.edit {
      it[sortKeyFor(tab)] = value.key.name
      it[sortDirFor(tab)] = value.direction.name
    }
  }

  // --- internals ------------------------------------------------------------

  private fun Preferences.toSnapshot(): SettingsSnapshot = SettingsSnapshot(
    theme = ThemePreference.fromStored(this[ThemePreferenceStore.KEY_THEME_MODE]),
    colorScheme = ColorScheme.fromStored(this[KEY_COLOR_SCHEME]),
    blackTheme = this[KEY_BLACK_THEME] ?: SettingsSnapshot.Default.blackTheme,
    rememberShuffle = this[KEY_REMEMBER_SHUFFLE] ?: SettingsSnapshot.Default.rememberShuffle,
    libraryTabs = parseLibraryTabs(this[KEY_LIBRARY_TABS]),
    intelligentSorting = this[KEY_INTELLIGENT_SORTING] ?: SettingsSnapshot.Default.intelligentSorting,
    forceSquareCovers = this[KEY_FORCE_SQUARE_COVERS] ?: SettingsSnapshot.Default.forceSquareCovers,
    headsetAutoplay = this[KEY_HEADSET_AUTOPLAY] ?: SettingsSnapshot.Default.headsetAutoplay,
    rewindBeforeSkipBack = this[KEY_REWIND_BEFORE_SKIP] ?: SettingsSnapshot.Default.rewindBeforeSkipBack,
    rememberPause = this[KEY_REMEMBER_PAUSE] ?: SettingsSnapshot.Default.rememberPause,
    autoDiscoverAlbumArt = this[KEY_AUTO_DISCOVER_ALBUM_ART] ?: SettingsSnapshot.Default.autoDiscoverAlbumArt,
    customBarAction = CustomBarAction.fromStored(this[KEY_CUSTOM_BAR_ACTION]),
    customNotificationAction = CustomNotificationAction.fromStored(this[KEY_CUSTOM_NOTIFICATION_ACTION]),
    pauseOnRepeat = this[KEY_PAUSE_ON_REPEAT] ?: SettingsSnapshot.Default.pauseOnRepeat,
    playFromLibrary = PlayFromLibrary.fromStored(this[KEY_PLAY_FROM_LIBRARY]),
    playFromItemDetails = PlayFromItemDetails.fromStored(this[KEY_PLAY_FROM_ITEM_DETAILS]),
    hideCollaborators = this[KEY_HIDE_COLLABORATORS] ?: SettingsSnapshot.Default.hideCollaborators,
    replayGainStrategy = ReplayGainStrategy.fromStored(this[KEY_REPLAYGAIN_STRATEGY]),
    replayGainPreampDb = (this[KEY_REPLAYGAIN_PREAMP] ?: SettingsSnapshot.Default.replayGainPreampDb)
      .coerceIn(REPLAYGAIN_PREAMP_MIN_DB, REPLAYGAIN_PREAMP_MAX_DB),
    albumCoversMode = AlbumCoversMode.fromStored(this[KEY_ALBUM_COVERS_MODE]),
    multiValueSeparators = MultiValueSeparator.fromStored(this[KEY_MULTI_VALUE_SEPARATORS]),
    musicSourceUris = this[KEY_MUSIC_SOURCE_URIS] ?: emptySet(),
    musicSourceMode = MusicSourceMode.fromStored(this[KEY_MUSIC_SOURCE_MODE]),
    automaticReloading = this[KEY_AUTOMATIC_RELOADING] ?: SettingsSnapshot.Default.automaticReloading,
    baseTheme = BaseTheme.fromStored(this[KEY_BASE_THEME]),
    albumArtTintEnabled = this[KEY_ALBUM_ART_TINT_ENABLED] ?: SettingsSnapshot.Default.albumArtTintEnabled,
  )

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

    /**
     * Parse the persisted library-tab order. Always returns a list that
     * contains every [LibraryTab] entry exactly once: persisted
     * (visible-and-ordered) entries first, then any new tabs that
     * weren't yet in storage. The visible subset is encoded by the
     * `_hidden_` token — anything after that prefix is hidden.
     *
     * Storage format example:
     *   "Songs,Albums,Artists,_hidden_,Genres,Playlists"
     */
    internal fun parseLibraryTabs(raw: String?): List<LibraryTab> {
      if (raw.isNullOrBlank()) return LibraryTab.DefaultOrder
      val parts = raw.split(",")
      val resolved = parts.mapNotNull { p ->
        if (p == HIDDEN_MARKER) null
        else LibraryTab.entries.firstOrNull { it.name == p }
      }
      // append unknown / new entries at the end so a future tab
      // doesn't disappear when the user upgrades.
      val seen = resolved.toSet()
      val tail = LibraryTab.entries.filter { it !in seen }
      return resolved + tail
    }

    internal const val HIDDEN_MARKER = "_hidden_"
  }
}
