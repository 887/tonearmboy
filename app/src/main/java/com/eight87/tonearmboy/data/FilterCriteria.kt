package com.eight87.tonearmboy.data

import com.eight87.tonearmboy.data.model.Album
import com.eight87.tonearmboy.data.model.Artist
import com.eight87.tonearmboy.data.model.Genre
import com.eight87.tonearmboy.data.model.Track
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * D.30 — composable filter conditions for a custom library tab.
 *
 * The custom-tab editor lets the user stack independent conditions
 * (e.g. "year between 2000 and 2010" + "added in the last 30 days").
 * Stacked conditions intersect: a track matches the criteria iff every
 * condition matches it (AND-only — confirmed by the user during D.30
 * design).
 *
 * Within a single multi-value condition (Genre / Artist / Album) the
 * supplied values OR together (a track passes if its genre is in the
 * configured set) — the user picks multiple values from one picker
 * rather than stacking N single-value conditions.
 */
@Serializable
sealed interface FilterCondition {

  fun matches(track: Track): Boolean

  /** True when this condition has no effect (no values supplied). */
  fun isEmpty(): Boolean

  @Serializable
  @SerialName("genre")
  data class GenreIn(val values: List<String>) : FilterCondition {
    override fun isEmpty(): Boolean = values.isEmpty()
    override fun matches(track: Track): Boolean {
      if (values.isEmpty()) return true
      val tg = track.genre
      if (tg.isNullOrBlank()) return false
      return values.any { it.equals(tg, ignoreCase = true) }
    }
  }

  @Serializable
  @SerialName("artist")
  data class ArtistIn(val values: List<String>) : FilterCondition {
    override fun isEmpty(): Boolean = values.isEmpty()
    override fun matches(track: Track): Boolean {
      if (values.isEmpty()) return true
      val candidates = listOfNotNull(
        track.artist?.takeIf { it.isNotBlank() },
        track.albumArtist?.takeIf { it.isNotBlank() },
      )
      if (candidates.isEmpty()) return false
      return values.any { wanted -> candidates.any { it.equals(wanted, ignoreCase = true) } }
    }
  }

  @Serializable
  @SerialName("album")
  data class AlbumIn(val values: List<String>) : FilterCondition {
    override fun isEmpty(): Boolean = values.isEmpty()
    override fun matches(track: Track): Boolean {
      if (values.isEmpty()) return true
      val ta = track.album
      if (ta.isNullOrBlank()) return false
      return values.any { it.equals(ta, ignoreCase = true) }
    }
  }

  @Serializable
  @SerialName("year_between")
  data class YearBetween(val min: Int? = null, val max: Int? = null) : FilterCondition {
    override fun isEmpty(): Boolean = min == null && max == null
    override fun matches(track: Track): Boolean {
      if (isEmpty()) return true
      val y = track.year ?: return false
      if (min != null && y < min) return false
      if (max != null && y > max) return false
      return true
    }
  }

  @Serializable
  @SerialName("date_added_between")
  data class DateAddedBetween(
    val afterEpochSeconds: Long? = null,
    val beforeEpochSeconds: Long? = null,
  ) : FilterCondition {
    override fun isEmpty(): Boolean = afterEpochSeconds == null && beforeEpochSeconds == null
    override fun matches(track: Track): Boolean {
      if (isEmpty()) return true
      if (afterEpochSeconds != null && track.dateAddedSeconds < afterEpochSeconds) return false
      if (beforeEpochSeconds != null && track.dateAddedSeconds > beforeEpochSeconds) return false
      return true
    }
  }

  @Serializable
  @SerialName("has_album_art")
  data class HasAlbumArt(val value: Boolean) : FilterCondition {
    override fun isEmpty(): Boolean = false
    override fun matches(track: Track): Boolean =
      (track.mediaStoreAlbumId != null) == value
  }

  @Serializable
  @SerialName("path_contains")
  data class PathContains(val needle: String) : FilterCondition {
    override fun isEmpty(): Boolean = needle.isBlank()
    override fun matches(track: Track): Boolean {
      if (isEmpty()) return true
      return track.data.contains(needle, ignoreCase = true)
    }
  }

  @Serializable
  @SerialName("title_contains")
  data class TitleContains(val needle: String) : FilterCondition {
    override fun isEmpty(): Boolean = needle.isBlank()
    override fun matches(track: Track): Boolean {
      if (isEmpty()) return true
      val haystacks = listOfNotNull(track.title, track.artist, track.album, track.albumArtist)
      return haystacks.any { it.contains(needle, ignoreCase = true) }
    }
  }
}

/**
 * D.18.1 / D.30 — Filter expression for a custom library tab.
 *
 * Stored as a JSON list of [FilterCondition]s. AND-intersection across
 * conditions; OR within a multi-value condition (see
 * [FilterCondition.GenreIn] et al.). Filtering runs in Kotlin over the
 * existing all-X Flows from [LibraryRepository] — see
 * [LibraryRepository.tracksMatching] etc.
 *
 * The pre-D.30 shape was a flat record (`genres`, `yearMin`, …); the
 * companion's [fromJson] still parses that legacy shape and translates
 * it into a conditions list, so existing custom tabs survive the
 * refactor without a Room migration. New writes always use the
 * conditions shape.
 */
@Serializable
data class FilterCriteria(val conditions: List<FilterCondition> = emptyList()) {

  fun isEmpty(): Boolean = conditions.all { it.isEmpty() }

  fun matchesTrack(track: Track): Boolean = conditions.all { it.matches(track) }

  fun matchesAlbum(album: Album, tracksOfAlbum: List<Track>): Boolean =
    tracksOfAlbum.any { matchesTrack(it) }

  fun matchesArtist(artist: Artist, tracksOfArtist: List<Track>): Boolean =
    tracksOfArtist.any { matchesTrack(it) }

  fun matchesGenre(genre: Genre, tracksOfGenre: List<Track>): Boolean =
    tracksOfGenre.any { matchesTrack(it) }

  companion object {
    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = false
      classDiscriminator = "type"
    }

    fun toJson(criteria: FilterCriteria): String = json.encodeToString(serializer(), criteria)

    fun fromJson(raw: String?): FilterCriteria {
      if (raw.isNullOrBlank()) return FilterCriteria()
      return runCatching {
        val element = json.parseToJsonElement(raw)
        val obj = element as? JsonObject ?: return@runCatching FilterCriteria()
        if (obj.containsKey("conditions")) {
          json.decodeFromString(serializer(), raw)
        } else {
          fromLegacyJson(obj)
        }
      }.getOrDefault(FilterCriteria())
    }

    /**
     * Compose a [FilterCriteria] from the pre-D.30 flat-field shape.
     * Used by the library top-level filter sheet (which is still a
     * single-page form, not the composable list) and by tests written
     * against the old API.
     */
    fun of(
      genres: List<String> = emptyList(),
      artists: List<String> = emptyList(),
      albums: List<String> = emptyList(),
      yearMin: Int? = null,
      yearMax: Int? = null,
      dateAddedAfter: Long? = null,
      dateAddedBefore: Long? = null,
      hasAlbumArt: Boolean? = null,
      pathContains: String? = null,
      nameSubstring: String? = null,
    ): FilterCriteria = FilterCriteria(buildLegacyConditions(
      genres, artists, albums,
      yearMin, yearMax,
      dateAddedAfter, dateAddedBefore,
      hasAlbumArt, pathContains, nameSubstring,
    ))

    private fun buildLegacyConditions(
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

    private fun fromLegacyJson(obj: JsonObject): FilterCriteria {
      fun stringList(key: String): List<String> =
        (obj[key] as? kotlinx.serialization.json.JsonArray)
          ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
          ?: emptyList()
      fun intOrNull(key: String): Int? = (obj[key] as? JsonPrimitive)?.intOrNull
      fun longOrNull(key: String): Long? = (obj[key] as? JsonPrimitive)?.longOrNull
      fun booleanOrNull(key: String): Boolean? = (obj[key] as? JsonPrimitive)?.booleanOrNull
      fun stringOrNull(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull

      return FilterCriteria(buildLegacyConditions(
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
  }
}
