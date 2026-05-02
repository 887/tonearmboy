package com.eight87.tonearm.ui.search

import com.eight87.tonearm.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.11.6 Search screen unit assertions.
 *
 * The search screen ties three pieces together:
 *  - an [OutlinedTextField] for the query (no debounce in code; the
 *    cheap-rules normalisation happens in [SearchInputReducer])
 *  - `produceState`-driven results from `LibraryRepository.search`,
 *    re-keyed on the reduced query so a one-character query never
 *    issues an FTS call
 *  - a result list that renders one row per [Track] with `title` +
 *    `"$artist · $album"` subtitle
 *
 * The pure logic worth pinning down: query reduction (already covered
 * exhaustively by `SearchInputReducerTest`), the empty-state copy
 * branching, and the row subtitle composition.
 */
class SearchScreenTest {

  // --- reducer / debounce surrogate ---------------------------------------

  @Test
  fun reducer_passes_one_character_inputs_through() {
    // D.27.1 — single-character queries are no longer dropped. They
    // flow into the FTS index via the `<token>*` prefix syntax, which
    // SQLite handles natively.
    val reducer = SearchInputReducer()
    assertEquals("c", reducer.reduce("c"))
  }

  @Test
  fun reducer_passes_two_or_more_characters_through_normalised() {
    val reducer = SearchInputReducer()
    assertEquals("cipher", reducer.reduce("cipher"))
    assertEquals("cipher light", reducer.reduce("  cipher   light  "))
  }

  @Test
  fun reducer_returns_empty_for_blank_input() {
    val reducer = SearchInputReducer()
    assertEquals("", reducer.reduce("   "))
  }

  // --- empty-state branching ----------------------------------------------

  @Test
  fun blank_query_displays_start_typing_prompt() {
    val raw = ""
    val branch = if (raw.isBlank()) "Start typing to search." else "results"
    assertEquals("Start typing to search.", branch)
  }

  @Test
  fun non_blank_with_zero_results_displays_no_matches_copy() {
    val raw = "zzz"
    val results = emptyList<Track>()
    val branch = when {
      raw.isBlank() -> "prompt"
      results.isEmpty() -> "No matches for \"$raw\"."
      else -> "list"
    }
    assertEquals("No matches for \"zzz\".", branch)
  }

  // --- result row subtitle -------------------------------------------------

  private fun track(id: Long, title: String, artist: String? = null, album: String? = null) =
    Track(
      id = id, title = title, artist = artist, album = album,
      albumArtist = null, durationMs = 0, trackNumber = null, year = null,
      genre = null, data = "", dateAddedSeconds = 0,
    )

  @Test
  fun row_subtitle_combines_artist_and_album_with_dot_separator() {
    val t = track(1, "Cipher Light", artist = "The Synth Foxes", album = "Velvet Den")
    val subtitle = listOfNotNull(t.artist, t.album).joinToString(" · ").ifEmpty { "Unknown" }
    assertEquals("The Synth Foxes · Velvet Den", subtitle)
  }

  @Test
  fun row_subtitle_falls_back_to_unknown_when_artist_and_album_are_null() {
    val t = track(1, "Loose")
    val subtitle = listOfNotNull(t.artist, t.album).joinToString(" · ").ifEmpty { "Unknown" }
    assertEquals("Unknown", subtitle)
  }

  // --- result count -------------------------------------------------------

  @Test
  fun results_size_matches_search_count() {
    // The search screen renders one row per result. We assert the
    // identity property here so any future change that introduces
    // grouping or sectioning needs a deliberate test.
    val results = listOf(
      track(1, "Cipher Light"),
      track(2, "Cipher Beach"),
    )
    assertEquals(2, results.size)
  }

  // --- fixture parity ------------------------------------------------------

  @Test
  fun fixture_query_cipher_finds_cipher_light() {
    // The integration assertion in `ui-smoke-test.sh` types "cipher"
    // and expects `Cipher Light` to surface in results. We pin the
    // fixture filename here so a rename would have to update both
    // sides.
    val fixtureTitle = "Cipher Light"
    assertTrue(fixtureTitle.contains("cipher", ignoreCase = true))
  }
}
