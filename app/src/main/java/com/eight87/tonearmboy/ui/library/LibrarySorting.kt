package com.eight87.tonearmboy.ui.library

import androidx.annotation.StringRes
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.model.Album
import com.eight87.tonearmboy.data.model.Artist
import com.eight87.tonearmboy.data.model.Genre
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.LibraryTab
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort

/**
 * R.D.1 — pure sort + section-key helpers extracted from
 * `LibraryScreen.kt`. All testable without spinning a Compose host.
 */

/**
 * T.A.3 — string-resource id for the rail label / dynamic top-bar title
 * of a built-in [LibraryTab]. Resolved by callers via `stringResource`
 * (Composable scope) or `Context.getString` (e.g. inside
 * `LaunchedEffect`).
 */
@StringRes
internal fun tabLabelRes(tab: LibraryTab): Int = when (tab) {
  LibraryTab.Songs -> R.string.library_tab_songs
  LibraryTab.Albums -> R.string.library_tab_albums
  LibraryTab.Artists -> R.string.library_tab_artists
  LibraryTab.Genres -> R.string.library_tab_genres
  LibraryTab.Playlists -> R.string.library_tab_playlists
}

internal fun sortNameKey(name: String, intelligentSorting: Boolean): String {
  if (!intelligentSorting) return name.uppercase()
  // D.9c.2 — drop a leading article in any of the supported languages.
  // The article list lives in `data/sort/IntelligentSort.kt`; see the
  // multi-language test (`IntelligentSortMultiLanguageTest`).
  return com.eight87.tonearmboy.data.sort.IntelligentSort
    .stripLeadingArticle(name)
    .uppercase()
}

private fun <T> applyDirection(
  items: List<T>,
  direction: SortDirection,
  comparator: Comparator<T>,
): List<T> {
  val sorted = items.sortedWith(comparator)
  return if (direction == SortDirection.Descending) sorted.reversed() else sorted
}

internal fun sortTracks(tracks: List<Track>, sort: TabSort, intelligentSorting: Boolean): List<Track> {
  val comparator: Comparator<Track> = when (sort.key) {
    SortKey.Name -> compareBy { sortNameKey(it.title, intelligentSorting) }
    SortKey.Artist -> compareBy { sortNameKey(it.artist ?: "", intelligentSorting) }
    SortKey.Album -> compareBy { sortNameKey(it.album ?: "", intelligentSorting) }
    SortKey.Date -> compareBy { it.year ?: Int.MIN_VALUE }
    SortKey.DateAdded -> compareBy { it.dateAddedSeconds }
    SortKey.Duration -> compareBy { it.durationMs }
  }
  return applyDirection(tracks, sort.direction, comparator)
}

internal fun sortAlbums(albums: List<Album>, sort: TabSort, intelligentSorting: Boolean): List<Album> {
  val comparator: Comparator<Album> = when (sort.key) {
    SortKey.Name -> compareBy { sortNameKey(it.name, intelligentSorting) }
    SortKey.Artist -> compareBy { sortNameKey(it.artist ?: "", intelligentSorting) }
    SortKey.Album -> compareBy { sortNameKey(it.name, intelligentSorting) }
    SortKey.Date -> compareBy { it.year ?: Int.MIN_VALUE }
    SortKey.DateAdded -> compareBy { it.id }
    SortKey.Duration -> compareBy { -it.trackCount }
  }
  return applyDirection(albums, sort.direction, comparator)
}

internal fun sortArtists(artists: List<Artist>, sort: TabSort, intelligentSorting: Boolean): List<Artist> {
  val comparator: Comparator<Artist> = when (sort.key) {
    SortKey.Artist, SortKey.Name -> compareBy { sortNameKey(it.name, intelligentSorting) }
    SortKey.Album -> compareBy { -it.albumCount }
    SortKey.Duration -> compareBy { -it.trackCount }
    SortKey.Date, SortKey.DateAdded -> compareBy { it.id }
  }
  return applyDirection(artists, sort.direction, comparator)
}

internal fun sortGenres(genres: List<Genre>, sort: TabSort): List<Genre> {
  val comparator: Comparator<Genre> = when (sort.key) {
    SortKey.Duration -> compareBy { -it.trackCount }
    else -> compareBy { it.name.uppercase() }
  }
  return applyDirection(genres, sort.direction, comparator)
}

/**
 * Section-key initial: the uppercased first letter, or `#` for
 * names that don't start with a letter.
 */
internal fun initialKey(name: String): String {
  val ch = name.firstOrNull()?.uppercaseChar() ?: '#'
  return if (ch.isLetter()) ch.toString() else "#"
}

/**
 * Section key when items are sorted by a year-bearing column. Plain
 * year as a string ("2024", "1999"); items with a missing year roll
 * up under "?" so they cluster together at the end of the list.
 */
internal fun yearSectionKey(year: Int?): String =
  year?.takeIf { it > 0 }?.toString() ?: "?"

/**
 * Section key when items are sorted by an epoch-seconds column
 * (typically `date_added` from MediaStore). Unlike [yearSectionKey]
 * the epoch needs decoding to a calendar year.
 */
internal fun yearSectionKeyForEpochSeconds(seconds: Long): String =
  if (seconds <= 0) "?"
  else java.time.Instant.ofEpochSecond(seconds)
    .atZone(java.time.ZoneId.systemDefault())
    .year
    .toString()

/**
 * Section key when items are sorted by track duration. Buckets in
 * minute ranges so the sort produces visible groupings on the
 * scrollbar bubble + section banners.
 */
internal fun durationBucket(durationMs: Long): String {
  val minutes = durationMs / 60_000L
  return when {
    minutes < 2 -> "< 2 min"
    minutes < 5 -> "2–5 min"
    minutes < 10 -> "5–10 min"
    else -> "10+ min"
  }
}

/**
 * Section key when items are sorted by a count column (track count,
 * album count). The sort comparator on these is descending; the
 * bucket layout matches.
 */
internal fun countBucket(count: Int): String = when {
  count >= 100 -> "100+"
  count >= 50 -> "50–99"
  count >= 10 -> "10–49"
  count >= 1 -> "1–9"
  else -> "0"
}

/**
 * R.A.Q — given a flat LazyColumn index, return the section letter
 * that contains it (or null if no sections exist). Used by the
 * FastScrollbar bubble.
 */
internal fun letterForFlatIndex(
  orderedKeys: List<String>,
  perItemKeys: List<String>,
  flatIndex: Int,
): String? {
  if (orderedKeys.isEmpty()) return null
  var flat = 0
  for (key in orderedKeys) {
    flat += 1 // header
    flat += perItemKeys.count { it == key }
    if (flatIndex < flat) return key
  }
  return orderedKeys.last()
}

/**
 * R.A.Q — variant for the `grouped: Map<key, items>` layout used by
 * Tracks tab. Avoids re-counting per-item-keys by reading directly
 * from group sizes.
 */
internal fun letterForFlatIndexInGrouped(
  orderedKeys: List<String>,
  grouped: Map<String, List<*>>,
  flatIndex: Int,
): String? {
  if (orderedKeys.isEmpty()) return null
  var flat = 0
  for (key in orderedKeys) {
    flat += 1 // header
    flat += grouped[key]?.size ?: 0
    if (flatIndex < flat) return key
  }
  return orderedKeys.last()
}

/**
 * D.28.5 — generic flat-index helper for tabs that group by an
 * ordered list of section keys (one entry per item). Walks
 * [orderedKeys] in order, advancing past one header + the count of
 * matching items per section. Returns -1 when the letter is unknown.
 *
 * Visible for unit tests.
 */
internal fun computeFlatIndexFromKeys(
  orderedKeys: List<String>,
  perItemKeys: List<String>,
  letter: String,
): Int {
  var flat = 0
  for (key in orderedKeys) {
    if (key == letter) return flat
    flat += 1 // header
    flat += perItemKeys.count { it == key }
  }
  return -1
}
