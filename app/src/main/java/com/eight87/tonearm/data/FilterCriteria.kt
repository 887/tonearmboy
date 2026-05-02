package com.eight87.tonearm.data

import com.eight87.tonearm.data.model.Album
import com.eight87.tonearm.data.model.Artist
import com.eight87.tonearm.data.model.Genre
import com.eight87.tonearm.data.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * D.18.1 — Filter expression for a custom library tab.
 *
 * Every predicate is independently optional; an empty list / `null`
 * means "no constraint on this axis". Predicates intersect (AND)
 * when matching against tracks. The intersection lives in
 * [matchesTrack] so callers can reuse it across the four content
 * types ([matchesAlbum], [matchesArtist], [matchesGenre]).
 *
 * Filtering runs in Kotlin over the existing all-X Flows from
 * [LibraryRepository]. We deliberately avoid building a SQL DSL —
 * the library is a few thousand rows at most on a typical phone, the
 * predicate set is small, and an in-memory pass keeps the schema
 * simple (criteriaJson is opaque to Room).
 */
@Serializable
data class FilterCriteria(
  val genres: List<String> = emptyList(),
  val artists: List<String> = emptyList(),
  val albums: List<String> = emptyList(),
  val yearMin: Int? = null,
  val yearMax: Int? = null,
  val dateAddedAfter: Long? = null,
  /**
   * D.27.5 — upper bound (exclusive of the comparator semantics: a track
   * matches if `dateAddedSeconds <= dateAddedBefore`). Combined with
   * [dateAddedAfter] this gives the user a "between two dates" range.
   */
  val dateAddedBefore: Long? = null,
  val hasAlbumArt: Boolean? = null,
  val pathContains: String? = null,
  /**
   * D.27.5 — case-insensitive substring match over the track title,
   * artist, album, and album-artist fields (whichever is non-blank).
   * Supplied by the Library filter sheet's "Name" field. A blank string
   * is treated identically to `null`.
   */
  val nameSubstring: String? = null,
) {

  fun isEmpty(): Boolean =
    genres.isEmpty() &&
      artists.isEmpty() &&
      albums.isEmpty() &&
      yearMin == null &&
      yearMax == null &&
      dateAddedAfter == null &&
      dateAddedBefore == null &&
      hasAlbumArt == null &&
      pathContains.isNullOrBlank() &&
      nameSubstring.isNullOrBlank()

  fun matchesTrack(track: Track): Boolean {
    if (genres.isNotEmpty()) {
      val tg = track.genre
      if (tg.isNullOrBlank() || !genres.any { it.equals(tg, ignoreCase = true) }) return false
    }
    if (artists.isNotEmpty()) {
      val candidates = listOfNotNull(
        track.artist?.takeIf { it.isNotBlank() },
        track.albumArtist?.takeIf { it.isNotBlank() },
      )
      if (candidates.isEmpty()) return false
      val ok = artists.any { wanted ->
        candidates.any { it.equals(wanted, ignoreCase = true) }
      }
      if (!ok) return false
    }
    if (albums.isNotEmpty()) {
      val ta = track.album
      if (ta.isNullOrBlank() || !albums.any { it.equals(ta, ignoreCase = true) }) return false
    }
    if (yearMin != null) {
      if (track.year == null || track.year < yearMin) return false
    }
    if (yearMax != null) {
      if (track.year == null || track.year > yearMax) return false
    }
    if (dateAddedAfter != null) {
      if (track.dateAddedSeconds < dateAddedAfter) return false
    }
    if (dateAddedBefore != null) {
      if (track.dateAddedSeconds > dateAddedBefore) return false
    }
    if (!nameSubstring.isNullOrBlank()) {
      val needle = nameSubstring
      val candidates = listOfNotNull(
        track.title,
        track.artist,
        track.album,
        track.albumArtist,
      )
      if (candidates.none { it.contains(needle, ignoreCase = true) }) return false
    }
    if (hasAlbumArt != null) {
      val has = track.mediaStoreAlbumId != null
      if (has != hasAlbumArt) return false
    }
    if (!pathContains.isNullOrBlank()) {
      if (!track.data.contains(pathContains, ignoreCase = true)) return false
    }
    return true
  }

  fun matchesAlbum(album: Album, tracksOfAlbum: List<Track>): Boolean {
    // An album matches if at least one of its tracks does. Empty
    // albums never match (defensive — the rollup shouldn't produce
    // them, but the predicate would otherwise vacuously pass).
    return tracksOfAlbum.any { matchesTrack(it) }
  }

  fun matchesArtist(artist: Artist, tracksOfArtist: List<Track>): Boolean =
    tracksOfArtist.any { matchesTrack(it) }

  fun matchesGenre(genre: Genre, tracksOfGenre: List<Track>): Boolean =
    tracksOfGenre.any { matchesTrack(it) }

  companion object {
    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = false
    }

    fun toJson(criteria: FilterCriteria): String = json.encodeToString(serializer(), criteria)

    fun fromJson(raw: String?): FilterCriteria {
      if (raw.isNullOrBlank()) return FilterCriteria()
      return runCatching { json.decodeFromString(serializer(), raw) }
        .getOrDefault(FilterCriteria())
    }
  }
}
