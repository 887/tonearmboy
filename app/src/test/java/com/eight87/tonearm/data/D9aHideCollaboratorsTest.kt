package com.eight87.tonearm.data

import com.eight87.tonearm.data.db.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.9a.6 — verify [LibraryRepository.deriveArtistsFromTracks] dedupes
 * collaborators when the toggle is on, and surfaces them when it's
 * off. The Flow integration is exercised via the existing
 * `LibraryDatabaseTest` style; this test pins the behaviour of the
 * pure function so the toggle's intent stays correct.
 */
class D9aHideCollaboratorsTest {

  private fun row(
    id: Long,
    title: String,
    artist: String?,
    albumArtist: String?,
    album: String? = "Album",
  ) = TrackEntity(
    id = id,
    title = title,
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

  @Test
  fun hideCollaborators_on_returns_only_album_artists() {
    val tracks = listOf(
      row(1, "t1", artist = "Bowie", albumArtist = "Bowie"),
      // A featuring credit: artist field includes "feat. Jagger" but
      // album_artist is the primary "Bowie".
      row(2, "t2", artist = "Bowie feat. Jagger", albumArtist = "Bowie"),
      row(3, "t3", artist = "Eno", albumArtist = "Eno"),
    )
    val artists = LibraryRepository.deriveArtistsFromTracks(tracks, hideCollaborators = true)
    val names = artists.map { it.name }.toSet()
    assertEquals(setOf("Bowie", "Eno"), names)
  }

  @Test
  fun hideCollaborators_off_includes_secondary_artist_credit() {
    val tracks = listOf(
      row(1, "t1", artist = "Bowie", albumArtist = "Bowie"),
      row(2, "t2", artist = "Bowie feat. Jagger", albumArtist = "Bowie"),
      row(3, "t3", artist = "Eno", albumArtist = "Eno"),
    )
    val artists = LibraryRepository.deriveArtistsFromTracks(tracks, hideCollaborators = false)
    val names = artists.map { it.name }.toSet()
    // Both the primary "Bowie" and the secondary "Bowie feat. Jagger"
    // appear because they differ; "Eno" appears once.
    assertTrue("expected Bowie present, got $names", names.contains("Bowie"))
    assertTrue("expected secondary artist visible, got $names", names.contains("Bowie feat. Jagger"))
    assertTrue(names.contains("Eno"))
  }

  @Test
  fun missing_albumArtist_falls_back_to_artist_in_both_modes() {
    val tracks = listOf(
      row(1, "t1", artist = "OnlyArtist", albumArtist = null),
    )
    val on = LibraryRepository.deriveArtistsFromTracks(tracks, hideCollaborators = true)
    val off = LibraryRepository.deriveArtistsFromTracks(tracks, hideCollaborators = false)
    assertEquals(listOf("OnlyArtist"), on.map { it.name })
    assertEquals(listOf("OnlyArtist"), off.map { it.name })
  }

  @Test
  fun blank_strings_are_ignored() {
    val tracks = listOf(
      row(1, "t1", artist = "  ", albumArtist = ""),
      row(2, "t2", artist = "Real", albumArtist = "Real"),
    )
    val artists = LibraryRepository.deriveArtistsFromTracks(tracks, hideCollaborators = false)
    assertEquals(listOf("Real"), artists.map { it.name })
  }

  @Test
  fun counts_are_aggregated_per_artist() {
    val tracks = listOf(
      row(1, "t1", artist = "A", albumArtist = "A", album = "X"),
      row(2, "t2", artist = "A", albumArtist = "A", album = "X"),
      row(3, "t3", artist = "A", albumArtist = "A", album = "Y"),
    )
    val artists = LibraryRepository.deriveArtistsFromTracks(tracks, hideCollaborators = true)
    val a = artists.single { it.name == "A" }
    assertEquals(2, a.albumCount)
    assertEquals(3, a.trackCount)
  }
}
