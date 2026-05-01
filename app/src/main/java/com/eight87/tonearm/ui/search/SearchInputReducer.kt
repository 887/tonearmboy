package com.eight87.tonearm.ui.search

/**
 * Pure search-input reducer — trims whitespace and ignores degenerate
 * inputs that would otherwise hammer the FTS index. Pure to keep it
 * easy to unit-test (see `SearchInputReducerTest`).
 *
 * The "reduction" here is intentionally cheap: full-blown debounce
 * happens at the Compose layer via `produceState`'s key change. This
 * class normalises the input string and applies the cheap rules:
 *  - drop a query that is shorter than [MIN_LENGTH] characters,
 *  - collapse internal whitespace,
 *  - return the empty string for blank input.
 */
class SearchInputReducer {

  fun reduce(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length < MIN_LENGTH) return ""
    return trimmed.replace(WHITESPACE_RUN, " ")
  }

  companion object {
    const val MIN_LENGTH = 2
    private val WHITESPACE_RUN = "\\s+".toRegex()
  }
}
