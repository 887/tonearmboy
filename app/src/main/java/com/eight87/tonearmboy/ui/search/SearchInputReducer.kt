package com.eight87.tonearmboy.ui.search

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
    /**
     * D.27.1 — single-character searches are now allowed. Previously
     * `MIN_LENGTH = 2` silently dropped one-char queries to the empty
     * string, which the UI then rendered as "No matches for 'a'." while
     * the FTS index was never even consulted. The user had no way to
     * tell why a one-character query never returned anything. SQLite
     * FTS4's prefix syntax (`a*`) handles single-character queries
     * just fine, so we no longer need a client-side floor.
     */
    const val MIN_LENGTH = 1
    private val WHITESPACE_RUN = "\\s+".toRegex()
  }
}
