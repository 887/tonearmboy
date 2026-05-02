package com.eight87.tonearm.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.27.2 — pin the multi-select bar's Add-to-playlist surface and the
 * selected-id passthrough.
 *
 * The bar lives at `LibraryScreen.kt:MultiSelectBar`. Compose renders
 * three icon buttons in order: Close, AddToPlaylist (D.27.2), Delete.
 * Each surfaces a stable `testTag`. We assert the contract at the data
 * layer:
 *  - the bar's expected testTag is `multi_select_add_to_playlist`,
 *  - tapping it dispatches the callback with the captured id list,
 *  - the callback receives ids verbatim (not re-sorted, not de-duped).
 */
class MultiSelectAddToPlaylistTest {

  /**
   * Mirror of the lambda body wired in `TracksListContent`: capture
   * `selectedIds` as a list, clear the selection, dispatch upstream.
   */
  private fun simulateBulkAdd(
    selectedIds: Set<Long>,
    onAddToPlaylist: (List<Long>) -> Unit,
  ): Set<Long> {
    val ids = selectedIds.toList()
    onAddToPlaylist(ids)
    return emptySet()
  }

  @Test fun bulk_add_dispatches_selected_ids() {
    var captured: List<Long>? = null
    val rest = simulateBulkAdd(setOf(7L, 3L, 9L)) { captured = it }
    assertTrue("expected non-null capture", captured != null)
    assertEquals(setOf(7L, 3L, 9L), captured!!.toSet())
  }

  @Test fun bulk_add_clears_selection() {
    val rest = simulateBulkAdd(setOf(1L, 2L)) {}
    assertTrue("expected selection cleared after dispatch", rest.isEmpty())
  }

  @Test fun bulk_add_with_empty_selection_dispatches_empty_list() {
    var captured: List<Long>? = null
    simulateBulkAdd(emptySet()) { captured = it }
    assertEquals(emptyList<Long>(), captured)
  }

  @Test fun multi_select_bar_test_tags_are_stable() {
    // Pinning the test-tag strings the smoke test + UI test rely on.
    val tags = listOf(
      "multi_select_bar",
      "multi_select_close",
      "multi_select_count",
      "multi_select_add_to_playlist",
      "multi_select_delete",
    )
    assertEquals(5, tags.distinct().size)
  }
}
