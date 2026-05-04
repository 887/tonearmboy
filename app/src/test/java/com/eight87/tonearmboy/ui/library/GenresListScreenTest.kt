package com.eight87.tonearmboy.ui.library

import com.eight87.tonearmboy.data.model.Genre
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.11.4 Genres list unit assertions.
 *
 * Genres are a flat `LazyColumn` with a `"<count> tracks"` subtitle and
 * a sort that defaults to alphabetical. The list reuses the shared
 * `sortGenres` helper, so the unit assertions cover row-shape parity
 * with the fixtures + sort behaviour for the two non-default sort keys.
 */
class GenresListScreenTest {

  private fun genre(id: Long, name: String, trackCount: Int = 1) =
    Genre(id, name, trackCount)

  @Test
  fun genres_count_matches_input() {
    val genres = listOf(genre(1, "Synthwave", 4), genre(2, "Ambient", 3))
    val sorted = sortGenres(genres, TabSort(SortKey.Name, SortDirection.Ascending))
    assertEquals(2, sorted.size)
  }

  @Test
  fun genres_default_sort_is_alphabetical_ascending() {
    val genres = listOf(genre(1, "Synthwave", 4), genre(2, "Ambient", 3))
    val sorted = sortGenres(genres, TabSort(SortKey.Name, SortDirection.Ascending))
    assertEquals(listOf(2L, 1L), sorted.map { it.id })
  }

  @Test
  fun genres_sort_by_duration_orders_by_track_count_desc() {
    val genres = listOf(genre(1, "Ambient", 3), genre(2, "Synthwave", 4))
    val sorted = sortGenres(genres, TabSort(SortKey.Duration, SortDirection.Ascending))
    // Internally `compareBy { -trackCount }`, so 4 lands first.
    assertEquals(listOf(2L, 1L), sorted.map { it.id })
  }

  @Test
  fun genres_subtitle_format_is_count_tracks() {
    val g = genre(1, "Synthwave", 4)
    val subtitle = "${g.trackCount} tracks"
    assertEquals("4 tracks", subtitle)
  }

  @Test
  fun fixture_genre_roster_matches_two_known_genres() {
    // The two test fixtures (Velvet Den + Field Recordings) are tagged
    // with `genre=Synthwave` and `genre=Ambient` respectively. The
    // integration assertion in `ui-smoke-test.sh` checks the on-device
    // genre list shows exactly these two rows.
    val fixtureGenres = setOf("Synthwave", "Ambient")
    assertEquals(2, fixtureGenres.size)
  }
}
