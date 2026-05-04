package com.eight87.tonearmboy.ui.library

import com.eight87.tonearmboy.data.FilterCondition
import com.eight87.tonearmboy.data.FilterCriteria
import com.eight87.tonearmboy.data.db.CustomTabContentType
import com.eight87.tonearmboy.data.db.CustomTabEntity
import com.eight87.tonearmboy.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.18.6 — wires the FilterCriteria predicate into the same shape the
 * `CustomTabContent` composable exercises in production: feed a list
 * of tracks through the filter and assert the surviving subset.
 *
 * Replaces the D.11.7 placeholder that was deferred until D.8c/d
 * (now D.18) shipped.
 */
class CustomTabRenderingTest {

  private fun track(
    id: Long,
    artist: String? = null,
    album: String? = null,
    genre: String? = null,
    year: Int? = null,
  ) = Track(
    id = id,
    title = "T$id",
    artist = artist,
    album = album,
    albumArtist = null,
    durationMs = 0,
    trackNumber = null,
    year = year,
    genre = genre,
    data = "/m/$id.mp3",
    dateAddedSeconds = 0,
  )

  @Test fun criteria_with_genres_synthwave_returns_only_synthwave_tracks() {
    val tracks = listOf(
      track(1, genre = "Synthwave"),
      track(2, genre = "Rock"),
      track(3, genre = "synthwave"),
    )
    val matched = tracks.filter {
      FilterCriteria.of(genres = listOf("Synthwave")).matchesTrack(it)
    }
    assertEquals(listOf(1L, 3L), matched.map { it.id })
  }

  @Test fun criteria_with_yearMin_2025_returns_only_2025_or_later() {
    val tracks = listOf(
      track(1, year = 2024),
      track(2, year = 2025),
      track(3, year = 2026),
      track(4, year = null),
    )
    val matched = tracks.filter {
      FilterCriteria.of(yearMin = 2025).matchesTrack(it)
    }
    assertEquals(listOf(2L, 3L), matched.map { it.id })
  }

  @Test fun criteria_intersection_narrows_results() {
    val tracks = listOf(
      track(1, genre = "Synthwave", year = 2025),
      track(2, genre = "Synthwave", year = 2010),
      track(3, genre = "Rock", year = 2025),
    )
    val matched = tracks.filter {
      FilterCriteria.of(genres = listOf("Synthwave"), yearMin = 2025).matchesTrack(it)
    }
    assertEquals(listOf(1L), matched.map { it.id })
  }

  @Test fun custom_tab_entity_round_trips_through_json_storage() {
    val tab = CustomTabEntity(
      id = 0,
      name = "Synthwave 2025",
      position = 0,
      contentType = CustomTabContentType.SONGS,
      criteriaJson = FilterCriteria.toJson(
        FilterCriteria.of(genres = listOf("Synthwave"), yearMin = 2025),
      ),
    )
    val recovered = FilterCriteria.fromJson(tab.criteriaJson)
    val genreCond = recovered.conditions.firstOrNull { it is FilterCondition.GenreIn } as? FilterCondition.GenreIn
    val yearCond = recovered.conditions.firstOrNull { it is FilterCondition.YearBetween } as? FilterCondition.YearBetween
    assertEquals(listOf("Synthwave"), genreCond?.values)
    assertEquals(2025, yearCond?.min)
    assertTrue(recovered.matchesTrack(track(1, genre = "Synthwave", year = 2025)))
  }
}
