package com.eight87.tonearm.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
  val roundMode: Boolean,
  val rememberShuffle: Boolean,
  val libraryTabs: List<LibraryTab>,
  val intelligentSorting: Boolean,
  val forceSquareCovers: Boolean,
  val headsetAutoplay: Boolean,
  val rewindBeforeSkipBack: Boolean,
  val rememberPause: Boolean,
) {
  companion object {
    val Default: SettingsSnapshot = SettingsSnapshot(
      theme = ThemePreference.Default,
      colorScheme = ColorScheme.Default,
      blackTheme = false,
      roundMode = true,
      rememberShuffle = false,
      libraryTabs = LibraryTab.DefaultOrder,
      intelligentSorting = true,
      forceSquareCovers = false,
      headsetAutoplay = false,
      rewindBeforeSkipBack = true,
      rememberPause = false,
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

  suspend fun setRoundMode(value: Boolean) {
    store.edit { it[KEY_ROUND_MODE] = value }
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
    roundMode = this[KEY_ROUND_MODE] ?: SettingsSnapshot.Default.roundMode,
    rememberShuffle = this[KEY_REMEMBER_SHUFFLE] ?: SettingsSnapshot.Default.rememberShuffle,
    libraryTabs = parseLibraryTabs(this[KEY_LIBRARY_TABS]),
    intelligentSorting = this[KEY_INTELLIGENT_SORTING] ?: SettingsSnapshot.Default.intelligentSorting,
    forceSquareCovers = this[KEY_FORCE_SQUARE_COVERS] ?: SettingsSnapshot.Default.forceSquareCovers,
    headsetAutoplay = this[KEY_HEADSET_AUTOPLAY] ?: SettingsSnapshot.Default.headsetAutoplay,
    rewindBeforeSkipBack = this[KEY_REWIND_BEFORE_SKIP] ?: SettingsSnapshot.Default.rewindBeforeSkipBack,
    rememberPause = this[KEY_REMEMBER_PAUSE] ?: SettingsSnapshot.Default.rememberPause,
  )

  companion object {
    internal val KEY_COLOR_SCHEME = stringPreferencesKey("color_scheme")
    internal val KEY_BLACK_THEME = booleanPreferencesKey("black_theme")
    internal val KEY_ROUND_MODE = booleanPreferencesKey("round_mode")
    internal val KEY_REMEMBER_SHUFFLE = booleanPreferencesKey("remember_shuffle")
    internal val KEY_LIBRARY_TABS = stringPreferencesKey("library_tabs")
    internal val KEY_INTELLIGENT_SORTING = booleanPreferencesKey("intelligent_sorting")
    internal val KEY_FORCE_SQUARE_COVERS = booleanPreferencesKey("force_square_covers")
    internal val KEY_HEADSET_AUTOPLAY = booleanPreferencesKey("headset_autoplay")
    internal val KEY_REWIND_BEFORE_SKIP = booleanPreferencesKey("rewind_before_skip")
    internal val KEY_REMEMBER_PAUSE = booleanPreferencesKey("remember_pause")

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
