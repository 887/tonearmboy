package com.eight87.tonearmboy.data.mediastore

/**
 * D.9c.1 — split a multi-value tag (artist / album_artist / genre)
 * into individual values according to the user-enabled separators.
 *
 * Scanning is left-to-right. At each cursor position the splitter
 * picks the **longest** separator that matches there, so `"feat."`
 * (5 chars) wins over a hypothetical `"feat"` (4) and over the single
 * characters `'.'` / `'f'`. Letter-bearing separators (`feat.`, `ft.`)
 * additionally require a non-letter (or string-start) before them,
 * which keeps `"Featherweight"` and `"Liftoff"` from being split — the
 * trailing dot already keeps `"feature"` safe on the other side.
 *
 * Each fragment is whitespace-trimmed; empty fragments are discarded.
 * A blank or null input returns an empty list. A non-blank input that
 * never hits a separator returns a singleton list with the trimmed
 * value unchanged.
 */
object MultiValueSplitter {

  fun split(raw: String?, separators: Set<String>): List<String> {
    if (raw == null) return emptyList()
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (separators.isEmpty()) return listOf(trimmed)

    // Order by descending length so the longest match at any given
    // position wins. Lexicographic tie-break is irrelevant for our six
    // separators but makes the order deterministic for tests.
    val ordered = separators.sortedWith(
      compareByDescending<String> { it.length }.thenBy { it },
    )
    val lower = trimmed.lowercase()
    val len = trimmed.length

    val out = ArrayList<String>(4)
    var cursor = 0
    var i = 0
    while (i < len) {
      val sep = matchSeparatorAt(lower, i, ordered)
      if (sep == null) {
        i++
        continue
      }
      out += trimmed.substring(cursor, i).trim()
      i += sep.length
      cursor = i
    }
    out += trimmed.substring(cursor).trim()
    return out.filter { it.isNotEmpty() }
  }

  /**
   * Return the longest separator that matches [lower] at index [at],
   * with letter-boundary protection for letter-bearing separators.
   * Returns null when no separator matches.
   */
  private fun matchSeparatorAt(lower: String, at: Int, ordered: List<String>): String? {
    for (sep in ordered) {
      val sepLower = sep.lowercase()
      if (at + sepLower.length > lower.length) continue
      if (!lower.regionMatches(at, sepLower, 0, sepLower.length)) continue
      val needsLeadingBoundary = sepLower.first().isLetter()
      if (needsLeadingBoundary && at > 0 && lower[at - 1].isLetter()) continue
      return sepLower
    }
    return null
  }
}
