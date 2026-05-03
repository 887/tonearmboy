package com.eight87.tonearm.data

import com.eight87.tonearm.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.27.5 — pin the filter contract introduced in Phase D.27.
 *
 * Asserts:
 *  1. AND-combination across name / year / date-added.
 *  2. The new `nameSubstring` field hits title, artist, album, and
 *     albumArtist.
 *  3. The new `dateAddedBefore` field gives an inclusive upper bound
 *     so combined with `dateAddedAfter` we get a between-two-dates
 *     range.
 *  4. The "any field non-null → filter active" badge predicate.
 */
class LibraryFilterTest {

  private fun track(
    id: Long,
    title: String = "T$id",
    artist: String? = null,
    album: String? = null,
    albumArtist: String? = null,
    year: Int? = null,
    dateAddedSeconds: Long = 0,
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = 0,
    trackNumber = null,
    year = year,
    genre = null,
    data = "",
    dateAddedSeconds = dateAddedSeconds,
  )

  // --- AND-combination ------------------------------------------------------

  @Test fun and_combination_of_name_year_date_filters_the_track() {
    val library = listOf(
      track(1, title = "Cipher Light", year = 2018, dateAddedSeconds = 1_000_000),
      track(2, title = "Velvet Pulse", year = 2020, dateAddedSeconds = 2_000_000),
      track(3, title = "Dust Loop", year = 2018, dateAddedSeconds = 3_000_000),
      track(4, title = "Cipher Beat", year = 2018, dateAddedSeconds = 1_500_000),
    )
    val criteria = FilterCriteria.of(
      nameSubstring = "cipher",
      yearMin = 2018,
      yearMax = 2018,
      dateAddedAfter = 1_000_000,
      dateAddedBefore = 1_700_000,
    )
    val matched = library.filter { criteria.matchesTrack(it) }
    assertEquals(listOf(1L, 4L), matched.map { it.id })
  }

  // --- nameSubstring scope --------------------------------------------------

  @Test fun nameSubstring_hits_title_artist_album_albumArtist() {
    val needle = "beatles"
    val byTitle = track(1, title = "The Beatles Theme")
    val byArtist = track(2, title = "X", artist = "The Beatles")
    val byAlbum = track(3, title = "X", album = "Beatles 1962-66")
    val byAlbumArtist = track(4, title = "X", albumArtist = "The Beatles")
    val miss = track(5, title = "Stones Roll")

    val criteria = FilterCriteria.of(nameSubstring = needle)
    val library = listOf(byTitle, byArtist, byAlbum, byAlbumArtist, miss)
    val matched = library.filter { criteria.matchesTrack(it) }
    assertEquals(setOf(1L, 2L, 3L, 4L), matched.map { it.id }.toSet())
  }

  @Test fun nameSubstring_is_case_insensitive() {
    val criteria = FilterCriteria.of(nameSubstring = "BEATLES")
    assertTrue(criteria.matchesTrack(track(1, artist = "the beatles")))
  }

  // --- dateAddedBefore semantics --------------------------------------------

  @Test fun dateAddedBefore_is_inclusive_upper_bound() {
    val criteria = FilterCriteria.of(dateAddedBefore = 1000L)
    assertTrue(criteria.matchesTrack(track(1, dateAddedSeconds = 1000L)))
    assertTrue(criteria.matchesTrack(track(1, dateAddedSeconds = 999L)))
    assertFalse(criteria.matchesTrack(track(1, dateAddedSeconds = 1001L)))
  }

  @Test fun between_two_dates_uses_after_and_before() {
    val criteria = FilterCriteria.of(dateAddedAfter = 100L, dateAddedBefore = 200L)
    assertFalse(criteria.matchesTrack(track(1, dateAddedSeconds = 99L)))
    assertTrue(criteria.matchesTrack(track(1, dateAddedSeconds = 100L)))
    assertTrue(criteria.matchesTrack(track(1, dateAddedSeconds = 150L)))
    assertTrue(criteria.matchesTrack(track(1, dateAddedSeconds = 200L)))
    assertFalse(criteria.matchesTrack(track(1, dateAddedSeconds = 201L)))
  }

  // --- isEmpty / badge predicate -------------------------------------------

  @Test fun isEmpty_is_true_for_default_criteria() {
    assertTrue(FilterCriteria().isEmpty())
  }

  @Test fun isEmpty_is_false_when_nameSubstring_set() {
    assertFalse(FilterCriteria.of(nameSubstring = "x").isEmpty())
  }

  @Test fun isEmpty_is_false_when_dateAddedBefore_set() {
    assertFalse(FilterCriteria.of(dateAddedBefore = 1L).isEmpty())
  }

  @Test fun isEmpty_is_false_when_year_range_set() {
    assertFalse(FilterCriteria.of(yearMin = 2000).isEmpty())
    assertFalse(FilterCriteria.of(yearMax = 2020).isEmpty())
  }

  @Test fun isEmpty_treats_blank_nameSubstring_as_unset() {
    assertTrue(FilterCriteria.of(nameSubstring = "").isEmpty())
    assertTrue(FilterCriteria.of(nameSubstring = "   ").isEmpty())
  }

  // --- year-range matching --------------------------------------------------

  @Test fun yearRange_includes_endpoints_and_excludes_outside() {
    val criteria = FilterCriteria.of(yearMin = 2000, yearMax = 2010)
    assertTrue(criteria.matchesTrack(track(1, year = 2000)))
    assertTrue(criteria.matchesTrack(track(1, year = 2010)))
    assertTrue(criteria.matchesTrack(track(1, year = 2005)))
    assertFalse(criteria.matchesTrack(track(1, year = 1999)))
    assertFalse(criteria.matchesTrack(track(1, year = 2011)))
  }

  @Test fun yearRange_drops_tracks_with_null_year_when_min_set() {
    val criteria = FilterCriteria.of(yearMin = 2000)
    assertFalse(criteria.matchesTrack(track(1, year = null)))
  }
}
