package com.eight87.tonearmboy.ui.settings

/**
 * R.B.6 — value type for the persisted library-tab order.
 *
 * Wraps `List<LibraryTab>` with the parse / encode pair the
 * `KEY_LIBRARY_TABS` setting uses. The visible subset is encoded by
 * the `_hidden_` token — anything after that prefix is hidden.
 *
 * Storage format example:
 *   `Songs,Albums,Artists,_hidden_,Genres,Playlists`
 *
 * The parsed list always contains every [LibraryTab] entry exactly
 * once: persisted (visible-and-ordered) entries first, then any new
 * tabs that weren't yet in storage. That way a future tab doesn't
 * disappear when the user upgrades.
 *
 * Lives outside `SettingsRepository` because the parsing rule is
 * UI-flavoured (which tabs are user-visible / hidden) and the
 * repository should not own that vocabulary.
 */
object LibraryTabOrder {
  /** Marker token in storage that separates visible from hidden tabs. */
  internal const val HIDDEN_MARKER: String = "_hidden_"

  /** Parse the persisted library-tab order. Tolerates unknown tokens. */
  fun fromStored(raw: String?): List<LibraryTab> {
    if (raw.isNullOrBlank()) return LibraryTab.DefaultOrder
    val parts = raw.split(",")
    val resolved = parts.mapNotNull { p ->
      if (p == HIDDEN_MARKER) null
      else LibraryTab.entries.firstOrNull { it.name == p }
    }
    val seen = resolved.toSet()
    val tail = LibraryTab.entries.filter { it !in seen }
    return resolved + tail
  }

  /** Encode a tab order to storage form. */
  fun toStored(value: List<LibraryTab>): String =
    value.joinToString(",") { it.name }
}
