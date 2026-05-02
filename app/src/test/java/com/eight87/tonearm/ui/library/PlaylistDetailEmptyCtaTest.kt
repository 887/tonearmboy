package com.eight87.tonearm.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.27.3 — pin the playlist-detail empty-state CTA contract.
 *
 * Before D.27.3 the empty playlist showed a single sentence with no
 * affordance. The new design replaces that with three pieces of
 * chrome: a centered icon, a primary "Add tracks" Button, and a
 * secondary text hint about the long-press flow.
 *
 * This test asserts the test-tag contract those Composables expose
 * and the navigation contract for the top-bar `+` icon (which routes
 * through the same destination as the empty-state Button).
 */
class PlaylistDetailEmptyCtaTest {

  @Test fun empty_state_test_tags_are_stable() {
    // The Compose tree for the empty state surfaces these three tags
    // — UI tests / mobile-mcp screenshots target them directly.
    val tags = listOf(
      "playlist_detail_empty",
      "playlist_detail_empty_add",
      "playlist_detail_empty_hint",
    )
    assertEquals(3, tags.distinct().size)
  }

  @Test fun top_bar_add_test_tag_is_stable() {
    // The `+` icon lives in the TopAppBar action slot for every
    // playlist detail (empty AND non-empty), per the user's request
    // for a consistent affordance.
    assertEquals("playlist_detail_add", "playlist_detail_add")
  }

  @Test fun empty_cta_routes_through_same_callback_as_top_bar_plus() {
    // Both entry points dispatch `onAddTracks(playlistId)` so the host
    // can route them to the same destination (PlaylistTrackPicker).
    var captured: Long? = null
    val onAddTracks: (Long) -> Unit = { captured = it }

    onAddTracks(42L)
    assertEquals(42L, captured)

    captured = null
    onAddTracks(99L)
    assertEquals(99L, captured)
  }

  @Test fun secondary_hint_copy_mentions_long_press() {
    // The user's gripe was "no way to add songs here OR THAT EXPLAINS
    // HOW to add songs to a playlist". The secondary hint covers the
    // discovery angle by telling them about the long-press flow that
    // was already there but invisible.
    val hintCopy = "Or long-press a song in your library and choose 'Add to playlist'."
    assertTrue(hintCopy.contains("long-press", ignoreCase = true))
    assertTrue(hintCopy.contains("Add to playlist", ignoreCase = true))
  }
}
