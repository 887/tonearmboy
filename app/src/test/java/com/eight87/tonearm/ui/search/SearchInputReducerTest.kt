package com.eight87.tonearm.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchInputReducerTest {

  private val reducer = SearchInputReducer()

  @Test fun reduce_blankInput_returnsEmpty() {
    assertEquals("", reducer.reduce(""))
    assertEquals("", reducer.reduce("   "))
    assertEquals("", reducer.reduce("\t\n"))
  }

  @Test fun reduce_belowMinLength_returnsEmpty() {
    // D.27.1 — MIN_LENGTH dropped to 1. Only the truly-empty input is
    // rejected; single characters now flow through to the FTS index.
    assertEquals("", reducer.reduce(""))
    assertEquals("", reducer.reduce(" "))
  }

  @Test fun reduce_singleCharacter_returnsCharacter() {
    // D.27.1 (regression guard): a single-character query — the user
    // typing one letter — must reach the search backend untouched.
    assertEquals("a", reducer.reduce("a"))
    assertEquals("b", reducer.reduce(" b "))
    assertEquals("z", reducer.reduce("z"))
  }

  @Test fun reduce_atOrAboveMinLength_returnsTrimmedQuery() {
    assertEquals("ab", reducer.reduce("ab"))
    assertEquals("ab", reducer.reduce("  ab  "))
    assertEquals("hello world", reducer.reduce("  hello world "))
  }

  @Test fun reduce_collapsesWhitespaceRuns() {
    assertEquals("hello world", reducer.reduce("hello   world"))
    assertEquals("a b c", reducer.reduce("a  b   c"))
  }

  @Test fun reduce_preservesPunctuationAndCase() {
    assertEquals("Beatles - Abbey", reducer.reduce("  Beatles - Abbey  "))
  }
}
