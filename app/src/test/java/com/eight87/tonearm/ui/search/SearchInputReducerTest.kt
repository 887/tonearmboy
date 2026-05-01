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
    // MIN_LENGTH = 2; single character is too short.
    assertEquals("", reducer.reduce("a"))
    assertEquals("", reducer.reduce(" b "))
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
