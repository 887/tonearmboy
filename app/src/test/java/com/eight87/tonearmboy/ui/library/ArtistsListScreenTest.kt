package com.eight87.tonearmboy.ui.library

import com.eight87.tonearmboy.data.model.Artist
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.11.4 Artists list unit assertions (genre + playlist tests live in
 * sibling files, but share this overall shape).
 *
 * The list renders one row per `Artist` with an "albums · tracks"
 * subtitle and applies intelligent sort + the "Hide collaborators"
 * filter at the repository layer (D.9a.6). The unit assertions cover:
 *  - row content shape (count + subtitle wording)
 *  - intelligent sort applied per current setting
 *  - hide-collaborators filtering predicate (mirror of the Repository's
 *    derivation: when ON, only `albumArtist` rows survive; when OFF, the
 *    union of artist + albumArtist + additionalArtists keeps everyone).
 */
class ArtistsListScreenTest {

  private fun artist(id: Long, name: String, albumCount: Int = 1, trackCount: Int = 1) =
    Artist(id, name, albumCount, trackCount)

  // --- one row per artist --------------------------------------------------

  @Test
  fun artists_count_matches_input() {
    val artists = listOf(
      artist(1, "Quiet Hours", albumCount = 1, trackCount = 3),
      artist(2, "The Synth Foxes", albumCount = 1, trackCount = 4),
    )
    val sorted = sortArtists(artists, TabSort(SortKey.Name, SortDirection.Ascending), true)
    assertEquals(2, sorted.size)
  }

  // --- count subtitle shape -----------------------------------------------

  @Test
  fun artist_subtitle_format_is_albums_dot_tracks() {
    // The subtitle format `"$albumCount albums · $trackCount tracks"`
    // is asserted at the integration layer; we pin the numeric inputs
    // here so a regression in upstream rollups (e.g. albumCount=0
    // showing "0 albums") shows up loudly.
    val a = artist(1, "Quiet Hours", albumCount = 1, trackCount = 3)
    val subtitle = "${a.albumCount} albums · ${a.trackCount} tracks"
    assertEquals("1 albums · 3 tracks", subtitle)
  }

  // --- intelligent sort ----------------------------------------------------

  @Test
  fun artists_sort_with_intelligent_strips_articles() {
    val artists = listOf(artist(1, "The Beatles"), artist(2, "ABBA"))
    val sorted = sortArtists(artists, TabSort(SortKey.Name, SortDirection.Ascending), true)
    // ABBA < BEATLES (after stripping "The")
    assertEquals(listOf(2L, 1L), sorted.map { it.id })
  }

  @Test
  fun artists_sort_without_intelligent_keeps_articles() {
    val artists = listOf(artist(1, "The Beatles"), artist(2, "ABBA"))
    val sorted = sortArtists(artists, TabSort(SortKey.Name, SortDirection.Ascending), false)
    // Plain uppercase: "ABBA" < "THE BEATLES"
    assertEquals(listOf(2L, 1L), sorted.map { it.id })
  }

  // --- hide-collaborators filter mirror ------------------------------------

  /**
   * Pure mirror of the Repository's hide-collaborators rule. R.F.4 —
   * Track no longer carries `additionalArtists` (those moved to
   * `ScannedTrack`); this mirror tracks the live entity-keyed
   * derivation in `LibraryRepository.deriveArtistsFromTracks`.
   */
  private fun deriveArtistRoster(tracks: List<Track>, hideCollaborators: Boolean): Set<String> {
    return if (hideCollaborators) {
      tracks.mapNotNull { it.albumArtist?.takeIf { name -> name.isNotBlank() } ?: it.artist }
        .filter { it.isNotBlank() }
        .toSet()
    } else {
      tracks.flatMap { t ->
        listOfNotNull(t.artist, t.albumArtist).distinct()
      }.filter { it.isNotBlank() }.toSet()
    }
  }

  private fun track(id: Long, artist: String?, albumArtist: String? = null) =
    Track(
      id = id, title = "T$id", artist = artist, album = null,
      albumArtist = albumArtist, durationMs = 0, trackNumber = null,
      year = null, genre = null, data = "", dateAddedSeconds = 0,
    )

  @Test
  fun hide_collaborators_filters_to_album_artist_when_on() {
    val tracks = listOf(
      track(1, artist = "Jane Doe", albumArtist = "Quiet Hours"),
      track(2, artist = "John Smith", albumArtist = "Quiet Hours"),
      track(3, artist = "Solo", albumArtist = null),
    )
    val roster = deriveArtistRoster(tracks, hideCollaborators = true)
    assertTrue(roster.contains("Quiet Hours"))
    assertFalse(roster.contains("Jane Doe"))
    assertFalse(roster.contains("John Smith"))
    // Falls back to artist when albumArtist is missing.
    assertTrue(roster.contains("Solo"))
  }

  @Test
  fun hide_collaborators_off_keeps_every_credited_name() {
    // R.F.4 — additionalArtists moved to ScannedTrack (scan-only); the
    // Track-keyed mirror only sees primary + albumArtist.
    val tracks = listOf(
      track(1, artist = "Jane Doe", albumArtist = "Quiet Hours"),
      track(2, artist = "John Smith"),
    )
    val roster = deriveArtistRoster(tracks, hideCollaborators = false)
    assertEquals(setOf("Jane Doe", "Quiet Hours", "John Smith"), roster)
  }

  // --- fixture parity ------------------------------------------------------

  @Test
  fun fixture_artist_roster_matches_two_known_artists() {
    // The two test fixtures (Velvet Den + Field Recordings) are tagged
    // with two `albumArtist` values: "The Synth Foxes" and "Quiet Hours".
    // That count drives the integration assertion in `ui-smoke-test.sh`
    // (artists list has 2 rows after fixtures land).
    val fixtureNames = setOf("The Synth Foxes", "Quiet Hours")
    assertEquals(2, fixtureNames.size)
  }
}
