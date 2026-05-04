package com.eight87.tonearmboy.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.28.5 / D.28.6 — pin the math the alphabet rail uses to scroll a
 * tile-mode `LazyGridState`. The grid prepends a full-row header
 * before each letter run (`span = maxLineSpan`) so the index of the
 * letter "B" in the grid is `1 + countOfA + 1` — the existing list
 * `flatIndexFor` logic doesn't translate, hence [tileIndexFor].
 */
class AlphabetScrollerTileTest {

  @Test
  fun empty_section_keys_returns_minus_one_for_any_letter() {
    assertEquals(-1, tileIndexFor(emptyList(), "A"))
  }

  @Test
  fun first_letter_is_at_grid_index_zero() {
    val keys = listOf("A", "A", "B", "B", "C")
    // Layout: [Header A][A][A][Header B][B][B][Header C][C]
    assertEquals(0, tileIndexFor(keys, "A"))
  }

  @Test
  fun second_letter_skips_first_run_and_first_header() {
    val keys = listOf("A", "A", "B", "B", "C")
    // 1 (header A) + 2 (A items) = 3 → header B sits at grid index 3.
    assertEquals(3, tileIndexFor(keys, "B"))
  }

  @Test
  fun third_letter_index_accounts_for_all_prior_runs_and_headers() {
    val keys = listOf("A", "A", "B", "B", "C")
    // 1 + 2 + 1 + 2 = 6 → header C at grid index 6.
    assertEquals(6, tileIndexFor(keys, "C"))
  }

  @Test
  fun unknown_letter_returns_minus_one() {
    val keys = listOf("A", "B", "C")
    assertEquals(-1, tileIndexFor(keys, "Z"))
  }

  @Test
  fun single_letter_run_yields_zero_for_that_letter() {
    val keys = listOf("Q", "Q", "Q")
    assertEquals(0, tileIndexFor(keys, "Q"))
  }

  @Test
  fun buildGroups_collapses_contiguous_runs() {
    val keys = listOf("A", "A", "B", "B", "B", "C")
    val groups = buildGroups(keys)
    assertEquals(3, groups.size)
    assertEquals("A", groups[0].letter); assertEquals(0, groups[0].start); assertEquals(2, groups[0].length)
    assertEquals("B", groups[1].letter); assertEquals(2, groups[1].start); assertEquals(3, groups[1].length)
    assertEquals("C", groups[2].letter); assertEquals(5, groups[2].start); assertEquals(1, groups[2].length)
  }

  @Test
  fun computeFlatIndexFromKeys_matches_list_mode_math() {
    // List mode (no header span; just inline sticky headers within the
    // LazyColumn) uses the same arithmetic — one header item + N rows
    // per group. This guards against the two implementations drifting.
    val keys = listOf("A", "A", "B", "C", "C")
    val ordered = keys.distinct()
    assertEquals(0, computeFlatIndexFromKeys(ordered, keys, "A"))
    assertEquals(3, computeFlatIndexFromKeys(ordered, keys, "B"))
    assertEquals(5, computeFlatIndexFromKeys(ordered, keys, "C"))
  }
}
