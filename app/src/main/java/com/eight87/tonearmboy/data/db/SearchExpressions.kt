package com.eight87.tonearmboy.data.db

/**
 * Helpers for translating user input into safe SQLite FTS / LIKE
 * expressions. Lives next to the DAO interfaces — Room would reject a
 * DAO that mixes annotated queries with plain helpers like these.
 */
internal object SearchExpressions {

  /**
   * Build a safe FTS4 MATCH expression. Splits on whitespace, strips
   * FTS metacharacters from each token, and appends `*` to the last
   * surviving token to enable prefix search. Returns null when no
   * usable token survives — callers should fall back to LIKE.
   */
  fun ftsMatch(input: String): String? {
    val tokens = input.split(Regex("\\s+"))
      .map { it.replace(Regex("[\"'^*()\\-]"), "") }
      .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    val head = tokens.dropLast(1).joinToString(" ")
    val tail = tokens.last() + "*"
    return if (head.isEmpty()) tail else "$head $tail"
  }

  /** Escape `%` and `_` so a user query can be used inside a LIKE pattern. */
  fun likePattern(input: String): String {
    val escaped = input.replace("\\", "\\\\")
      .replace("%", "\\%")
      .replace("_", "\\_")
    return "%${escaped}%"
  }
}
