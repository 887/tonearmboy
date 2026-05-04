package com.eight87.tonearmboy.playback

import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.PlayFromItemDetails
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.9a.4 + D.9a.5 — verify the queue-build strategies for taps from a
 * library list view and from a detail surface. The tests run against
 * [computePlayFromLibraryQueue] and [computePlayFromDetailQueue], the
 * pure helpers extracted alongside [PlaybackUiController.playFromLibrary]
 * / [PlaybackUiController.playFromDetail] so the logic can be exercised
 * without spinning up a `MediaController`.
 */
class D9aPlayFromStrategyTest {

  private fun track(id: Long, album: String? = null, artist: String? = null, albumArtist: String? = null) =
    Track(
      id = id,
      title = "t$id",
      artist = artist,
      album = album,
      albumArtist = albumArtist,
      durationMs = 0,
      trackNumber = null,
      year = null,
      genre = null,
      data = "/path/$id",
      dateAddedSeconds = 0,
    )

  private val songs = listOf(
    track(1, album = "A", artist = "Artist A", albumArtist = "Artist A"),
    track(2, album = "A", artist = "Artist A", albumArtist = "Artist A"),
    track(3, album = "B", artist = "Artist B", albumArtist = "Artist B"),
    track(4, album = "B", artist = "Artist B", albumArtist = "Artist B"),
    track(5, album = "C", artist = "Artist C", albumArtist = "Artist C"),
  )

  // --- Library: AllSongs / ItemOnly / CurrentFilter ----------------

  @Test
  fun playFromLibrary_allSongs_uses_full_library() {
    // Surrounding list = the 3 tracks of album A; allSongs = all 5.
    val surrounding = songs.take(2) // album A
    val (q, idx) = computePlayFromLibraryQueue(
      surroundingList = surrounding,
      tappedIndex = 1, // track id=2
      strategy = PlayFromLibrary.AllSongs,
      allSongs = songs,
    )
    assertEquals(songs.map { it.id }, q.map { it.id })
    assertEquals(1, idx) // id=2 is at position 1 in `songs`
  }

  @Test
  fun playFromLibrary_itemOnly_returns_single_track_queue() {
    val (q, idx) = computePlayFromLibraryQueue(
      surroundingList = songs,
      tappedIndex = 3, // track id=4
      strategy = PlayFromLibrary.ItemOnly,
      allSongs = songs,
    )
    assertEquals(listOf(4L), q.map { it.id })
    assertEquals(0, idx)
  }

  @Test
  fun playFromLibrary_currentFilter_uses_surrounding_list_only() {
    val albumB = songs.filter { it.album == "B" } // ids 3, 4
    val (q, idx) = computePlayFromLibraryQueue(
      surroundingList = albumB,
      tappedIndex = 1, // track id=4
      strategy = PlayFromLibrary.CurrentFilter,
      allSongs = songs,
    )
    assertEquals(listOf(3L, 4L), q.map { it.id })
    assertEquals(1, idx)
  }

  // --- Detail: ShownItem / Album / Artist --------------------------

  @Test
  fun playFromDetail_shownItem_uses_surrounding_list() {
    val (q, idx) = computePlayFromDetailQueue(
      surroundingList = songs,
      tappedIndex = 2, // id=3
      strategy = PlayFromItemDetails.ShownItem,
    )
    assertEquals(songs.map { it.id }, q.map { it.id })
    assertEquals(2, idx)
  }

  @Test
  fun playFromDetail_album_filters_to_same_album() {
    val (q, idx) = computePlayFromDetailQueue(
      surroundingList = songs,
      tappedIndex = 0, // id=1, album A
      strategy = PlayFromItemDetails.Album,
    )
    assertEquals(listOf(1L, 2L), q.map { it.id })
    assertEquals(0, idx)
  }

  @Test
  fun playFromDetail_artist_filters_to_same_artist() {
    val (q, idx) = computePlayFromDetailQueue(
      surroundingList = songs,
      tappedIndex = 2, // id=3, Artist B
      strategy = PlayFromItemDetails.Artist,
    )
    assertEquals(listOf(3L, 4L), q.map { it.id })
    assertEquals(0, idx)
  }

  @Test
  fun playFromDetail_album_with_null_album_falls_back_to_single() {
    val orphan = listOf(track(99, album = null, artist = "X", albumArtist = "X"))
    val (q, idx) = computePlayFromDetailQueue(
      surroundingList = orphan,
      tappedIndex = 0,
      strategy = PlayFromItemDetails.Album,
    )
    assertEquals(listOf(99L), q.map { it.id })
    assertEquals(0, idx)
  }
}
