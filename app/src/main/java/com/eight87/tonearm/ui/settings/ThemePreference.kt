package com.eight87.tonearm.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user-configurable theme mode. The default is [System], which means
 * "follow the OS dark/light setting". The app default-on-first-launch is
 * dark, which is what the OS reports for almost everyone these days; if
 * a user explicitly toggles the OS to light we follow.
 */
enum class ThemePreference {
  System,
  Light,
  Dark,
  ;

  companion object {
    /** Default if the user has never set a preference. */
    val Default: ThemePreference = System

    /** Robust parse — falls back to [Default] for unknown / null input. */
    fun fromStored(raw: String?): ThemePreference =
      entries.firstOrNull { it.name == raw } ?: Default
  }
}

/**
 * Persists the [ThemePreference] across launches via DataStore Preferences.
 *
 * Stateless wrapper — the actual `DataStore<Preferences>` is owned by the
 * `Context.tonearmDataStore` extension below, so multiple constructions
 * of this class share the same underlying file (DataStore enforces a
 * single instance per file, so we route through one extension).
 */
class ThemePreferenceStore(private val context: Context) {

  /** Emits the current preference, including initial load. */
  val flow: Flow<ThemePreference> =
    context.tonearmDataStore.data.map { prefs ->
      ThemePreference.fromStored(prefs[KEY_THEME_MODE])
    }

  suspend fun set(value: ThemePreference) {
    context.tonearmDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = value.name }
  }

  companion object {
    internal val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
  }
}

internal val Context.tonearmDataStore: DataStore<Preferences> by preferencesDataStore(name = "tonearm_settings")
