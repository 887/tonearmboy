package com.eight87.tonearmboy.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * R.F.5 — pre-D.30 flat-field migration helpers (Data-F8). Lifted out
 * of [FilterCriteria]'s companion object so the live type stops carrying
 * frozen migration concerns.
 *
 * - [fromJson] decodes the pre-D.30 wire shape (no `conditions` key)
 *   into a current-shape [FilterCriteria]. Called by
 *   [FilterCriteria.fromJson] when the parsed envelope lacks the
 *   sealed-condition list.
 * - [conditionsFromFlatFields] composes a `FilterCondition` list from
 *   the same flat-field set. Called by [FilterCriteria.of] (still
 *   exposed on the live type because callers and tests use it).
 *
 * Lives in the same package so the live type's `internal` references
 * stay visible without re-exporting.
 */
internal object FilterCriteriaLegacy {

  fun fromJson(obj: JsonObject): FilterCriteria {
    fun stringList(key: String): List<String> =
      (obj[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?: emptyList()
    fun intOrNull(key: String): Int? = (obj[key] as? JsonPrimitive)?.intOrNull
    fun longOrNull(key: String): Long? = (obj[key] as? JsonPrimitive)?.longOrNull
    fun booleanOrNull(key: String): Boolean? = (obj[key] as? JsonPrimitive)?.booleanOrNull
    fun stringOrNull(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull

    return FilterCriteria(conditionsFromFlatFields(
      genres = stringList("genres"),
      artists = stringList("artists"),
      albums = stringList("albums"),
      yearMin = intOrNull("yearMin"),
      yearMax = intOrNull("yearMax"),
      dateAddedAfter = longOrNull("dateAddedAfter"),
      dateAddedBefore = longOrNull("dateAddedBefore"),
      hasAlbumArt = booleanOrNull("hasAlbumArt"),
      pathContains = stringOrNull("pathContains"),
      nameSubstring = stringOrNull("nameSubstring"),
    ))
  }

  fun conditionsFromFlatFields(
    genres: List<String>,
    artists: List<String>,
    albums: List<String>,
    yearMin: Int?,
    yearMax: Int?,
    dateAddedAfter: Long?,
    dateAddedBefore: Long?,
    hasAlbumArt: Boolean?,
    pathContains: String?,
    nameSubstring: String?,
  ): List<FilterCondition> = buildList {
    if (genres.isNotEmpty()) add(FilterCondition.GenreIn(genres))
    if (artists.isNotEmpty()) add(FilterCondition.ArtistIn(artists))
    if (albums.isNotEmpty()) add(FilterCondition.AlbumIn(albums))
    if (yearMin != null || yearMax != null) {
      add(FilterCondition.YearBetween(min = yearMin, max = yearMax))
    }
    if (dateAddedAfter != null || dateAddedBefore != null) {
      add(FilterCondition.DateAddedBetween(
        afterEpochSeconds = dateAddedAfter,
        beforeEpochSeconds = dateAddedBefore,
      ))
    }
    if (hasAlbumArt != null) add(FilterCondition.HasAlbumArt(hasAlbumArt))
    if (!pathContains.isNullOrBlank()) add(FilterCondition.PathContains(pathContains))
    if (!nameSubstring.isNullOrBlank()) add(FilterCondition.TitleContains(nameSubstring))
  }
}
