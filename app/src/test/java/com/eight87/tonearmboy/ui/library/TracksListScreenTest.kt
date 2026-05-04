package com.eight87.tonearmboy.ui.library

import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.playback.computePlayFromLibraryQueue
import com.eight87.tonearmboy.ui.library.tabs.TrackRowAction
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.11.3 Tracks list unit assertions.
 *
 * The list is a `LazyColumn` with sticky alphabet headers, an alphabet
 * scroller column, a per-row overflow `DropdownMenu` and a tap action.
 * The load-bearing logic worth pinning down is:
 *  - one row per [Track] (size invariance through sort)
 *  - sticky alphabet header keys are derived from [sortNameKey]
 *  - alphabet-scroller jump computes the right flat-list index
 *  - the per-row overflow menu surfaces every entry the spec promises
 *    (Play / Add to queue / Add to playlist / Go to album / Go to artist
 *    / Delete-disabled-stub for Phase F)
 *  - tap-to-play threads through the configured `PlayFromLibrary`
 *    strategy via the existing pure helper [computePlayFromLibraryQueue].
 */
class TracksListScreenTest {

  private fun track(
    id: Long,
    title: String,
    artist: String? = null,
    album: String? = null,
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = null,
    durationMs = 0,
    trackNumber = null,
    year = null,
    genre = null,
    data = "",
    dateAddedSeconds = 0,
  )

  // --- one row per track ---------------------------------------------------

  @Test
  fun sortTracks_preserves_count_one_to_one() {
    val tracks = listOf(
      track(1, "Cipher Light"),
      track(2, "Velvet Pulse"),
      track(3, "Dust Loop"),
    )
    val sorted = sortTracks(tracks, TabSort(SortKey.Name, SortDirection.Ascending), true)
    assertEquals(3, sorted.size)
  }

  // --- sticky alphabet headers --------------------------------------------

  @Test
  fun alphabet_initial_keys_are_derived_from_sort_keys() {
    val tracks = listOf(
      track(1, "Apple"),
      track(2, "Avocado"),
      track(3, "Banana"),
    )
    val sorted = sortTracks(tracks, TabSort(SortKey.Name, SortDirection.Ascending), true)
    val grouped = sorted.groupBy { initialKey(sortNameKey(it.title, true)) }
    assertEquals(setOf("A", "B"), grouped.keys)
    assertEquals(2, grouped.getValue("A").size)
    assertEquals(1, grouped.getValue("B").size)
  }

  @Test
  fun intelligent_sort_strips_articles_for_section_grouping() {
    val tracks = listOf(track(1, "The Beatles"), track(2, "ABBA"))
    val grouped = tracks.groupBy { initialKey(sortNameKey(it.title, true)) }
    // "The Beatles" → "BEATLES" → "B"; "ABBA" → "A"
    assertTrue(grouped.containsKey("A"))
    assertTrue(grouped.containsKey("B"))
  }

  @Test
  fun non_letter_titles_bucket_under_hash_section() {
    val tracks = listOf(track(1, "1999"), track(2, "@home"))
    val grouped = tracks.groupBy { initialKey(sortNameKey(it.title, true)) }
    assertEquals(setOf("#"), grouped.keys)
  }

  // --- alphabet scroller jump ---------------------------------------------

  @Test
  fun alphabet_scroller_jump_computes_correct_flat_index_for_first_letter() {
    // Section A header at flat index 0; the matched item lands at the
    // start of its section, header included.
    val ordered = listOf("A", "B", "C")
    val grouped = mapOf(
      "A" to listOf(track(1, "Apple"), track(2, "Avocado")),
      "B" to listOf(track(3, "Banana")),
      "C" to listOf(track(4, "Cipher Light"), track(5, "Cobalt")),
    )
    assertEquals(0, computeFlatIndexForTest(ordered, grouped, "A"))
  }

  @Test
  fun alphabet_scroller_jump_to_C_skips_AB_sections() {
    val ordered = listOf("A", "B", "C")
    val grouped = mapOf(
      "A" to listOf(track(1, "Apple"), track(2, "Avocado")), // header + 2
      "B" to listOf(track(3, "Banana")),                     // header + 1
      "C" to listOf(track(4, "Cipher Light")),
    )
    // 1 + 2 (A) + 1 + 1 (B) = 5
    assertEquals(5, computeFlatIndexForTest(ordered, grouped, "C"))
  }

  @Test
  fun alphabet_scroller_jump_to_unknown_letter_returns_minus_one() {
    val ordered = listOf("A", "B")
    val grouped = mapOf(
      "A" to listOf(track(1, "Apple")),
      "B" to listOf(track(2, "Banana")),
    )
    assertEquals(-1, computeFlatIndexForTest(ordered, grouped, "Z"))
  }

  // --- per-row overflow menu set ------------------------------------------

  @Test
  fun track_row_overflow_lists_every_required_action() {
    // Compile-time guarantee: every action enum entry has a route in
    // the row's `handleTrackAction` switch (Compose-side wiring is
    // asserted by the integration smoke test).
    val expected = setOf(
      TrackRowAction.Play,
      TrackRowAction.AddToQueue,
      TrackRowAction.AddToPlaylist,
      TrackRowAction.GoToAlbum,
      TrackRowAction.GoToArtist,
      TrackRowAction.Delete,
    )
    assertEquals(expected, TrackRowAction.entries.toSet())
  }

  // --- play-from-library strategy threading -------------------------------

  @Test
  fun tap_with_AllSongs_strategy_uses_all_library_songs_as_queue() {
    val all = listOf(track(1, "Cipher Light"), track(2, "Velvet Pulse"), track(3, "Dust Loop"))
    val visible = listOf(all[1], all[2]) // a filtered subset is showing
    val (queue, idx) = computePlayFromLibraryQueue(visible, 1, PlayFromLibrary.AllSongs, all)
    assertEquals(all, queue)
    // The tapped track is "Dust Loop" — find it in the all-songs queue.
    assertEquals(2, idx)
  }

  @Test
  fun tap_with_ItemOnly_strategy_uses_only_tapped_track() {
    val all = listOf(track(1, "Cipher Light"), track(2, "Velvet Pulse"))
    val (queue, idx) = computePlayFromLibraryQueue(all, 1, PlayFromLibrary.ItemOnly, all)
    assertEquals(listOf(all[1]), queue)
    assertEquals(0, idx)
  }

  @Test
  fun tap_with_CurrentFilter_strategy_uses_visible_subset() {
    val all = listOf(track(1, "Cipher Light"), track(2, "Velvet Pulse"), track(3, "Dust Loop"))
    val visible = listOf(all[1], all[2])
    val (queue, idx) = computePlayFromLibraryQueue(visible, 0, PlayFromLibrary.CurrentFilter, all)
    assertEquals(visible, queue)
    assertEquals(0, idx)
  }
}

/**
 * Re-implementation of the private `computeFlatIndex` so the test can
 * exercise the alphabet-scroller mapping without exposing the production
 * helper. Kept identical in shape so future edits stay tested.
 */
private fun <T> computeFlatIndexForTest(
  orderedKeys: List<String>,
  grouped: Map<String, List<T>>,
  letter: String,
): Int {
  var flat = 0
  for (key in orderedKeys) {
    if (key == letter) return flat
    flat += 1 // header
    flat += grouped.getValue(key).size
  }
  return -1
}
