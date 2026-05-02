package com.eight87.tonearm.data.sort

/**
 * D.9c.2 — strip a leading article from a title for sort purposes.
 * Display strings are unchanged; only the *sort key* is modified.
 *
 * Languages handled when the user enables intelligent sorting:
 *   - English: `the`, `a`, `an`
 *   - French: `le`, `la`, `les`, `l'`
 *   - German: `der`, `die`, `das`, `den`, `dem`, `des`
 *   - Spanish: `el`, `la`, `los`, `las`
 *   - Italian: `il`, `lo`, `la`, `i`, `gli`, `le`, `l'`
 *   - Dutch: `de`, `het`, `'t`
 *
 * Only the **first** article is stripped — `"The The"` → `"The"`,
 * never the empty string. Apostrophe-suffixed articles (`l'`, `'t`)
 * may join directly to the next word with no space (`L'Estate` →
 * `Estate`), so the matcher tolerates both `l'X` and `l' X`.
 *
 * Comparison is case-insensitive but the returned key preserves the
 * original casing of the body of the string so callers that uppercase
 * downstream get a clean comparison space.
 */
object IntelligentSort {

  /**
   * Articles separated by category. Sorted by descending length so the
   * matcher prefers `"les"` over `"le"` at the same position. Plain
   * (space-terminated) articles are listed once; apostrophe-suffixed
   * articles live in their own list because they don't require a
   * trailing space.
   */
  private val SPACE_ARTICLES: List<String> = listOf(
    // 4-letter
    "het",
    // 3-letter
    "the", "les", "der", "die", "das", "den", "dem", "des", "los", "las", "gli",
    // 2-letter
    "an", "le", "la", "el", "il", "lo", "de",
    // 1-letter
    "a", "i",
  ).sortedByDescending { it.length }

  private val APOSTROPHE_ARTICLES: List<String> = listOf("l'", "'t")

  /**
   * Strip the first leading article from [name]. Returns the remainder
   * trimmed of whitespace. If no article matches, returns [name]
   * trimmed but otherwise unchanged.
   *
   * The function never returns an empty string — if stripping would
   * leave nothing, the original trimmed name is returned (this is what
   * keeps `"The"` (the band) and `"The The"` from collapsing).
   */
  fun stripLeadingArticle(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return trimmed
    val lower = trimmed.lowercase()

    // Apostrophe-suffixed: `l'amour`, `l' Estate`, `'t Hooft` etc.
    for (article in APOSTROPHE_ARTICLES) {
      if (lower.startsWith(article)) {
        val rest = trimmed.substring(article.length).trimStart()
        if (rest.isNotEmpty()) return rest
      }
    }

    // Plain articles followed by whitespace.
    for (article in SPACE_ARTICLES) {
      if (lower.length <= article.length) continue
      if (!lower.startsWith(article)) continue
      val nextChar = trimmed[article.length]
      if (!nextChar.isWhitespace()) continue
      val rest = trimmed.substring(article.length + 1).trimStart()
      if (rest.isNotEmpty()) return rest
    }

    return trimmed
  }
}
