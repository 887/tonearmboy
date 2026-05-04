package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.runtime.Composable
import com.eight87.tonearmboy.ui.library.TileItem
import com.eight87.tonearmboy.ui.settings.TabSort

/**
 * R.D.2 — strategy interface that collapses the five near-duplicate
 * library-tab dispatchers behind one renderer ([LibraryTabRenderer]).
 *
 * One implementation per content type (Tracks / Albums / Artists /
 * Genres / Playlists). Each impl knows:
 *
 *  - how to produce a stable item id for `LazyColumn.items(key = …)`
 *  - how to project an item onto a section letter (sticky headers +
 *    fast-scroll bubble); null when sections aren't applicable for
 *    the current sort
 *  - how to build a `TileItem` for tile-mode rendering, when the
 *    tab supports it
 *  - the list-mode row composable (with optional selection state
 *    threaded through for the multi-select-aware Tracks tab)
 *  - test-tag + empty-state copy + tile-mode availability
 *
 * The renderer walks any `TabSpec<T>` against a pre-sorted list and
 * produces the chrome (LazyColumn or LazyVerticalGrid + FastScrollbar
 * + sticky headers when grouped). The per-tab data + sort + filter
 * fetch lives in the surrounding `XxxTabScreen` composable.
 */
interface TabSpec<T : Any> {
  /** `Modifier.semantics { testTag = … }` value for the outer Row. */
  val testTag: String

  /** T.A.3 — string-resource id for [EmptyState] when the data list is empty. */
  @get:androidx.annotation.StringRes
  val emptyMessageRes: Int

  /** Tile-mode availability — list-only tabs (e.g. Playlists) return false. */
  val supportsTileMode: Boolean

  /** Stable id used as the `LazyColumn.items(key = …)` argument. */
  fun id(item: T): Long

  /**
   * Section letter for [item], or null when the current [sort]
   * doesn't lend itself to alphabetical grouping (e.g. sort by
   * Date, Duration, etc.). The renderer uses this both for
   * sticky headers in list mode and for the alphabet bubble
   * overlay on the FastScrollbar.
   */
  fun sectionKey(item: T, sort: TabSort, intelligentSorting: Boolean): String?

  /**
   * Project [item] onto a [TileItem] for tile-grid rendering.
   * Implementations that don't support tile mode (or for which a
   * particular item has no tile representation) return null. The
   * renderer skips items whose `toTile` returns null.
   */
  fun toTile(item: T): TileItem?

  /**
   * List-mode row composable. [selected] / [inSelectionMode] are
   * consumed by multi-select-aware tabs (Tracks); other impls can
   * ignore them and just render a plain row.
   */
  @Composable
  fun ListRow(
    item: T,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
  )
}
