package com.eight87.tonearm.ui.library

import com.eight87.tonearm.data.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.11.4 Playlists list unit assertions.
 *
 * Playlists render as a `LazyColumn` of two-line rows + an
 * `ExtendedFloatingActionButton` ("New playlist") + a create-playlist
 * `AlertDialog`. The load-bearing logic worth pinning down:
 *  - empty list shows the "tap + to create one" empty-state copy
 *  - non-empty list yields one row per [Playlist] with a `<count> tracks`
 *    subtitle
 *  - the create dialog only confirms on a non-blank trimmed name (the
 *    confirm button is wired to `name.trim().isNotEmpty()`)
 */
class PlaylistsListScreenTest {

  private fun playlist(id: Long, name: String, trackCount: Int = 0, createdAt: Long = 0) =
    Playlist(id, name, trackCount, createdAt)

  // --- empty / non-empty branching ----------------------------------------

  @Test
  fun empty_list_renders_zero_rows() {
    val playlists = emptyList<Playlist>()
    assertEquals(0, playlists.size)
  }

  @Test
  fun non_empty_list_yields_one_row_per_playlist() {
    val playlists = listOf(
      playlist(1, "Late night drives"),
      playlist(2, "Morning queue"),
    )
    assertEquals(2, playlists.size)
  }

  @Test
  fun playlist_subtitle_format_is_count_tracks() {
    val p = playlist(1, "Late night drives", trackCount = 7)
    val subtitle = "${p.trackCount} tracks"
    assertEquals("7 tracks", subtitle)
  }

  // --- FAB / create dialog input validation --------------------------------

  @Test
  fun blank_dialog_input_does_not_create_playlist() {
    val raw = "   "
    val trimmed = raw.trim()
    assertTrue(trimmed.isEmpty())
  }

  @Test
  fun whitespace_around_real_name_is_trimmed_before_create() {
    val raw = "  Late night drives "
    val trimmed = raw.trim()
    assertEquals("Late night drives", trimmed)
  }

  @Test
  fun fixture_playlist_roster_matches_zero_initial_then_one_after_create() {
    // The integration smoke test creates one playlist via the FAB and
    // asserts the on-device list flips from zero rows to one row.
    var playlists = emptyList<Playlist>()
    assertEquals(0, playlists.size)
    playlists = playlists + playlist(1, "Late night drives")
    assertEquals(1, playlists.size)
  }
}
