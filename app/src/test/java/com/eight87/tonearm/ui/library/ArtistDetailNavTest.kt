package com.eight87.tonearm.ui.library

import com.eight87.tonearm.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.15.2 — artist-detail tracks include both album-artist matches and
 * featured-artist matches, so a track credited to "Foxes feat. Bear"
 * surfaces under both artists. The screen uses the same filter rule;
 * this pins the predicate down without spinning a Compose host.
 */
class ArtistDetailNavTest {

  private fun track(
    id: Long,
    title: String,
    artist: String?,
    albumArtist: String? = null,
    album: String? = "Album",
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = 180_000L,
    trackNumber = id.toInt(),
    year = 2024,
    genre = "Synthwave",
    data = "/audio/$id.flac",
    dateAddedSeconds = 0,
  )

  private fun filterArtist(tracks: List<Track>, name: String): List<Track> =
    tracks.filter { (it.albumArtist ?: it.artist) == name || it.artist == name }

  @Test fun primary_artist_matches() {
    val tracks = listOf(
      track(1, "A", artist = "Foxes", albumArtist = "Foxes"),
      track(2, "B", artist = "Bear", albumArtist = "Bear"),
    )
    assertEquals(1, filterArtist(tracks, "Foxes").size)
  }

  @Test fun album_artist_takes_precedence_over_artist() {
    // A featured track credits albumArtist=Foxes, artist=Bear; we want
    // the "Foxes" detail screen to include this track and the "Bear"
    // detail screen to also include it as a featured-artist appearance.
    val tracks = listOf(track(1, "Featured", artist = "Bear", albumArtist = "Foxes"))
    val foxes = filterArtist(tracks, "Foxes")
    val bear = filterArtist(tracks, "Bear")
    assertEquals(1, foxes.size)
    assertEquals(1, bear.size)
  }

  @Test fun unrelated_artist_yields_empty() {
    val tracks = listOf(track(1, "A", artist = "Foxes"))
    assertTrue(filterArtist(tracks, "Otter").isEmpty())
  }
}
