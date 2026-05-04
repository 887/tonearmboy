package com.eight87.tonearmboy.data

import com.eight87.tonearmboy.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.18.6 — every predicate independently and several intersections.
 *
 * The contract is "every predicate is AND" — empty predicates pass
 * (no constraint), populated ones narrow.
 */
class FilterCriteriaMatchingTest {

  private fun track(
    id: Long = 1L,
    title: String = "T$id",
    artist: String? = "A$id",
    album: String? = "Al$id",
    albumArtist: String? = null,
    year: Int? = null,
    genre: String? = null,
    dateAddedSeconds: Long = 0L,
    mediaStoreAlbumId: Long? = null,
    data: String = "/storage/emulated/0/Music/$id.mp3",
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = 0,
    trackNumber = null,
    year = year,
    genre = genre,
    data = data,
    dateAddedSeconds = dateAddedSeconds,
    mediaStoreAlbumId = mediaStoreAlbumId,
  )

  @Test fun empty_criteria_matches_everything() {
    val c = FilterCriteria()
    assertTrue(c.isEmpty())
    assertTrue(c.matchesTrack(track()))
  }

  @Test fun genres_filter_is_case_insensitive() {
    val c = FilterCriteria.of(genres = listOf("Synthwave"))
    assertTrue(c.matchesTrack(track(genre = "synthwave")))
    assertFalse(c.matchesTrack(track(genre = "Rock")))
    assertFalse(c.matchesTrack(track(genre = null)))
  }

  @Test fun artists_predicate_considers_album_artist_too() {
    val c = FilterCriteria.of(artists = listOf("Daft Punk"))
    assertTrue(c.matchesTrack(track(artist = "Daft Punk")))
    assertTrue(c.matchesTrack(track(artist = "Other", albumArtist = "Daft Punk")))
    assertFalse(c.matchesTrack(track(artist = "Other", albumArtist = "Other2")))
  }

  @Test fun albums_filter() {
    val c = FilterCriteria.of(albums = listOf("Discovery"))
    assertTrue(c.matchesTrack(track(album = "Discovery")))
    assertFalse(c.matchesTrack(track(album = "Random Access Memories")))
    assertFalse(c.matchesTrack(track(album = null)))
  }

  @Test fun yearMin_and_yearMax_bound_the_range_and_drop_null_year() {
    val c = FilterCriteria.of(yearMin = 2020, yearMax = 2024)
    assertTrue(c.matchesTrack(track(year = 2020)))
    assertTrue(c.matchesTrack(track(year = 2024)))
    assertFalse(c.matchesTrack(track(year = 2019)))
    assertFalse(c.matchesTrack(track(year = 2025)))
    assertFalse(c.matchesTrack(track(year = null)))
  }

  @Test fun dateAddedAfter_inclusive_lower_bound() {
    val c = FilterCriteria.of(dateAddedAfter = 1000L)
    assertTrue(c.matchesTrack(track(dateAddedSeconds = 1000L)))
    assertTrue(c.matchesTrack(track(dateAddedSeconds = 5000L)))
    assertFalse(c.matchesTrack(track(dateAddedSeconds = 999L)))
  }

  @Test fun hasAlbumArt_distinguishes_present_vs_missing() {
    val withArt = FilterCriteria.of(hasAlbumArt = true)
    val withoutArt = FilterCriteria.of(hasAlbumArt = false)
    assertTrue(withArt.matchesTrack(track(mediaStoreAlbumId = 7L)))
    assertFalse(withArt.matchesTrack(track(mediaStoreAlbumId = null)))
    assertFalse(withoutArt.matchesTrack(track(mediaStoreAlbumId = 7L)))
    assertTrue(withoutArt.matchesTrack(track(mediaStoreAlbumId = null)))
  }

  @Test fun pathContains_substring_match_case_insensitive() {
    val c = FilterCriteria.of(pathContains = "live")
    assertTrue(c.matchesTrack(track(data = "/m/Live Album/1.mp3")))
    assertTrue(c.matchesTrack(track(data = "/m/LIVE/2.mp3")))
    assertFalse(c.matchesTrack(track(data = "/m/Studio/3.mp3")))
  }

  @Test fun intersection_of_predicates_narrows() {
    val c = FilterCriteria.of(
      genres = listOf("Synthwave"),
      yearMin = 2020,
    )
    assertTrue(c.matchesTrack(track(genre = "Synthwave", year = 2020)))
    assertFalse(c.matchesTrack(track(genre = "Synthwave", year = 2010)))
    assertFalse(c.matchesTrack(track(genre = "Rock", year = 2020)))
  }

  @Test fun json_round_trip_preserves_every_field() {
    val original = FilterCriteria.of(
      genres = listOf("A", "B"),
      artists = listOf("X"),
      albums = listOf("Y"),
      yearMin = 2010,
      yearMax = 2020,
      dateAddedAfter = 12345L,
      hasAlbumArt = true,
      pathContains = "live",
    )
    val raw = FilterCriteria.toJson(original)
    val parsed = FilterCriteria.fromJson(raw)
    assertEquals(original, parsed)
  }

  @Test fun fromJson_blank_or_invalid_returns_empty_criteria() {
    assertTrue(FilterCriteria.fromJson(null).isEmpty())
    assertTrue(FilterCriteria.fromJson("").isEmpty())
    assertTrue(FilterCriteria.fromJson("{not-json").isEmpty())
  }
}
