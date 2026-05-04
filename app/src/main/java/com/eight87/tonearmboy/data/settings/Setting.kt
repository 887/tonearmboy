package com.eight87.tonearmboy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * R.B.1 — narrow contract for a single user setting.
 *
 * One [Setting] binds one storage key to one [Flow] read + one
 * suspending mutator. UI consumers depend on `Setting<T>`, not on a
 * fat repository wholesale. The interface segregates the two
 * operations a setting actually has — a hot-Flow read and a write —
 * which is everything a Compose screen + a per-key bridge needs.
 */
interface Setting<T> {
  val flow: Flow<T>
  suspend fun set(value: T)
}

/**
 * R.B.1 — generic [Setting] backed by a [Preferences] key plus a pair
 * of transforms. Storage type [S] (typically `Boolean`, `String`,
 * `Float`, `Set<String>`) is decoupled from the consumer-facing type
 * [T] (a domain value, an enum, etc.) so the boilerplate of
 * "Flow.map { read }" + "edit { it[key] = write }" lives in one place.
 *
 * The [read] callback is given the raw stored value (or `null` when
 * the key is absent) and is responsible for any default fallback.
 */
class PreferencesSetting<T, S>(
  private val store: DataStore<Preferences>,
  private val key: Preferences.Key<S>,
  private val read: (S?) -> T,
  private val write: (T) -> S,
) : Setting<T> {
  override val flow: Flow<T> = store.data.map { read(it[key]) }
  override suspend fun set(value: T) {
    store.edit { it[key] = write(value) }
  }
}

/**
 * R.B.1 — [Setting] for an enum whose companion exposes
 * `fromStored(raw: String?): E`. The on-disk encoding is the enum's
 * `.name`; defaults are encoded once in `fromStored` and reused both
 * on read and on first-launch absence.
 */
class EnumSetting<E : Enum<E>>(
  private val store: DataStore<Preferences>,
  private val key: Preferences.Key<String>,
  private val fromStored: (String?) -> E,
) : Setting<E> {
  override val flow: Flow<E> = store.data.map { fromStored(it[key]) }
  override suspend fun set(value: E) {
    store.edit { it[key] = value.name }
  }
}

/**
 * R.B.2 — boolean [Setting] convenience. The hand-rolled `it ?: default`
 * is the most common shape across the repository; this factory removes
 * the per-key boilerplate.
 */
fun booleanSetting(
  store: DataStore<Preferences>,
  key: Preferences.Key<Boolean>,
  default: Boolean,
): Setting<Boolean> = PreferencesSetting(
  store = store,
  key = key,
  read = { it ?: default },
  write = { it },
)
