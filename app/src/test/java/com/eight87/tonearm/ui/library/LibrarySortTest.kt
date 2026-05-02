package com.eight87.tonearm.ui.library

import com.eight87.tonearm.data.model.Album
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.ui.settings.SortDirection
import com.eight87.tonearm.ui.settings.SortKey
import com.eight87.tonearm.ui.settings.TabSort
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {

  private fun track(
    id: Long,
    title: String,
    artist: String? = null,
    album: String? = null,
    duration: Long = 0,
    year: Int? = null,
    dateAdded: Long = 0,
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = null,
    durationMs = duration,
    trackNumber = null,
    year = year,
    genre = null,
    data = "",
    dateAddedSeconds = dateAdded,
  )

  @Test
  fun sortNameKey_strips_leading_articles_when_intelligent() {
    assertEquals("STROKES", sortNameKey("The Strokes", true))
    assertEquals("PERFECT CIRCLE", sortNameKey("A Perfect Circle", true))
    assertEquals("INNOCENT MAN", sortNameKey("An Innocent Man", true))
    // unchanged when intelligent is off
    assertEquals("THE STROKES", sortNameKey("The Strokes", false))
  }

  @Test
  fun sortTracks_byName_ascending_uses_intelligent_keys() {
    val tracks = listOf(
      track(1, "The Apple"),
      track(2, "Banana"),
      track(3, "An Avocado"),
    )
    val sorted = sortTracks(tracks, TabSort(SortKey.Name, SortDirection.Ascending), true)
    // "Apple" < "Avocado" < "Banana" after stripping "The"/"An".
    assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
  }

  @Test
  fun sortTracks_byDuration_descending() {
    val tracks = listOf(track(1, "a", duration = 100), track(2, "b", duration = 300), track(3, "c", duration = 200))
    val sorted = sortTracks(tracks, TabSort(SortKey.Duration, SortDirection.Descending), true)
    assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
  }

  @Test
  fun sortTracks_byDateAdded_ascending_uses_dateAddedSeconds() {
    val tracks = listOf(track(1, "a", dateAdded = 5), track(2, "b", dateAdded = 1), track(3, "c", dateAdded = 3))
    val sorted = sortTracks(tracks, TabSort(SortKey.DateAdded, SortDirection.Ascending), true)
    assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
  }

  @Test
  fun sortAlbums_byArtist_uses_artist_key() {
    val albums = listOf(
      Album(id = 1, name = "X", artist = "The Beatles", trackCount = 1, year = null),
      Album(id = 2, name = "Y", artist = "ABBA", trackCount = 1, year = null),
    )
    val sorted = sortAlbums(albums, TabSort(SortKey.Artist, SortDirection.Ascending), true)
    // "ABBA" < "Beatles" after stripping "The"
    assertEquals(listOf(2L, 1L), sorted.map { it.id })
  }
}
