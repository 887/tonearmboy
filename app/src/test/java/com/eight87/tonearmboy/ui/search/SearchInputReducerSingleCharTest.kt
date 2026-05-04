package com.eight87.tonearmboy.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.27.1 — pin the single-character search regression. The previous
 * `MIN_LENGTH = 2` silently dropped one-letter queries to "" so the
 * UI rendered "No matches for 'a'." while the backend never even saw
 * the query. This test guards against that regression for every ASCII
 * lowercase letter so a future refactor can't sneak the floor back in.
 */
class SearchInputReducerSingleCharTest {

  private val reducer = SearchInputReducer()

  @Test fun reduce_singleAsciiLetter_returnsLetter() {
    for (ch in 'a'..'z') {
      assertEquals("expected $ch to round-trip", ch.toString(), reducer.reduce(ch.toString()))
    }
  }

  @Test fun reduce_singleAsciiDigit_returnsDigit() {
    for (ch in '0'..'9') {
      assertEquals("expected $ch to round-trip", ch.toString(), reducer.reduce(ch.toString()))
    }
  }

  @Test fun reduce_singleCharSurroundedByWhitespace_returnsTrimmedChar() {
    assertEquals("a", reducer.reduce("   a   "))
    assertEquals("z", reducer.reduce("\tz\n"))
  }

  @Test fun reduce_minLengthIs1_perPlanD27() {
    // Direct constant assertion: pinning the literal so a future
    // re-bump trips this test, not just the substring tests above.
    assertEquals(1, SearchInputReducer.MIN_LENGTH)
  }
}
