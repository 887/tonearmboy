package com.eight87.tonearmboy.data

import com.eight87.tonearmboy.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.9c.1 — verify that [Mapping.deriveArtistsFromDomain] /
 * [Mapping.deriveGenresFromDomain] produce one row per split value
 * when the scanner has populated `additional*` lists on a Track.
 */
class MappingD9cTest {

  private fun track(
    id: Long,
    title: String,
    artist: String? = null,
    albumArtist: String? = null,
    genre: String? = null,
    additionalArtists: List<String> = emptyList(),
    additionalAlbumArtists: List<String> = emptyList(),
    additionalGenres: List<String> = emptyList(),
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = "Album",
    albumArtist = albumArtist,
    durationMs = 0,
    trackNumber = null,
    year = null,
    genre = genre,
    data = "",
    dateAddedSeconds = 0,
    additionalArtists = additionalArtists,
    additionalAlbumArtists = additionalAlbumArtists,
    additionalGenres = additionalGenres,
  )

  @Test
  fun deriveArtistsFromDomain_yields_one_row_per_split_value() {
    val tracks = listOf(
      track(
        1, "T1",
        artist = "Jane Doe",
        additionalArtists = listOf("John Smith"),
      ),
      track(2, "T2", artist = "Other"),
    )
    val rows = Mapping.deriveArtistsFromDomain(tracks).map { it.name }
    assertEquals(listOf("Jane Doe", "John Smith", "Other"), rows)
  }

  @Test
  fun deriveArtistsFromDomain_prefers_albumArtist_and_uses_its_extras() {
    val tracks = listOf(
      track(
        1, "T1",
        artist = "Performer",
        albumArtist = "Composer A",
        additionalArtists = listOf("Should Not Appear"),
        additionalAlbumArtists = listOf("Composer B"),
      ),
    )
    val rows = Mapping.deriveArtistsFromDomain(tracks).map { it.name }
    assertEquals(listOf("Composer A", "Composer B"), rows)
  }

  @Test
  fun deriveArtistsFromDomain_dedupes_across_tracks() {
    val tracks = listOf(
      track(1, "T1", artist = "Jane Doe", additionalArtists = listOf("John Smith")),
      track(2, "T2", artist = "John Smith"),
    )
    val rows = Mapping.deriveArtistsFromDomain(tracks).map { it.name }
    assertEquals(listOf("Jane Doe", "John Smith"), rows)
  }

  @Test
  fun deriveGenresFromDomain_yields_one_row_per_split_genre() {
    val tracks = listOf(
      track(1, "T1", genre = "Rock", additionalGenres = listOf("Pop")),
      track(2, "T2", genre = "Jazz"),
    )
    val rows = Mapping.deriveGenresFromDomain(tracks).map { it.name }
    assertEquals(listOf("Rock", "Pop", "Jazz"), rows)
  }
}
