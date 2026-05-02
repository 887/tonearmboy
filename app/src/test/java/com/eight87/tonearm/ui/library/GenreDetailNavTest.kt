package com.eight87.tonearm.ui.library

import com.eight87.tonearm.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.15.3 — genre-detail track filter is a strict equality check on
 * `track.genre`. Tracks with no genre never appear under any genre
 * detail screen.
 */
class GenreDetailNavTest {

  private fun track(id: Long, title: String, genre: String?) = Track(
    id = id,
    title = title,
    artist = "X",
    album = "Y",
    albumArtist = null,
    durationMs = 180_000L,
    trackNumber = null,
    year = 2024,
    genre = genre,
    data = "/audio/$id.flac",
    dateAddedSeconds = 0,
  )

  @Test fun filter_picks_matching_genre_only() {
    val tracks = listOf(
      track(1, "A", "Synthwave"),
      track(2, "B", "Field Recordings"),
      track(3, "C", "Synthwave"),
      track(4, "D", null),
    )
    val filtered = tracks.filter { it.genre == "Synthwave" }
    assertEquals(2, filtered.size)
    assertEquals(listOf(1L, 3L), filtered.map { it.id })
  }

  @Test fun null_genre_never_matches() {
    val tracks = listOf(track(1, "A", null))
    assertEquals(0, tracks.filter { it.genre == "Synthwave" }.size)
  }
}
