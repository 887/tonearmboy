package com.eight87.tonearmboy.data

import com.eight87.tonearmboy.data.model.ScannedTrack
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.9c.1 / R.F.4 — verify [Mapping.deriveArtistsFromScanned] /
 * [Mapping.deriveGenresFromScanned] yield one row per split value when
 * the scanner has populated `additional*` lists on a [ScannedTrack].
 */
class MappingD9cTest {

  private fun scanned(
    id: Long,
    title: String,
    artist: String? = null,
    albumArtist: String? = null,
    genre: String? = null,
    additionalArtists: List<String> = emptyList(),
    additionalAlbumArtists: List<String> = emptyList(),
    additionalGenres: List<String> = emptyList(),
  ) = ScannedTrack(
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
  fun deriveArtistsFromScanned_yields_one_row_per_split_value() {
    val tracks = listOf(
      scanned(
        1, "T1",
        artist = "Jane Doe",
        additionalArtists = listOf("John Smith"),
      ),
      scanned(2, "T2", artist = "Other"),
    )
    val rows = Mapping.deriveArtistsFromScanned(tracks).map { it.name }
    assertEquals(listOf("Jane Doe", "John Smith", "Other"), rows)
  }

  @Test
  fun deriveArtistsFromScanned_prefers_albumArtist_and_uses_its_extras() {
    val tracks = listOf(
      scanned(
        1, "T1",
        artist = "Performer",
        albumArtist = "Composer A",
        additionalArtists = listOf("Should Not Appear"),
        additionalAlbumArtists = listOf("Composer B"),
      ),
    )
    val rows = Mapping.deriveArtistsFromScanned(tracks).map { it.name }
    assertEquals(listOf("Composer A", "Composer B"), rows)
  }

  @Test
  fun deriveArtistsFromScanned_dedupes_across_tracks() {
    val tracks = listOf(
      scanned(1, "T1", artist = "Jane Doe", additionalArtists = listOf("John Smith")),
      scanned(2, "T2", artist = "John Smith"),
    )
    val rows = Mapping.deriveArtistsFromScanned(tracks).map { it.name }
    assertEquals(listOf("Jane Doe", "John Smith"), rows)
  }

  @Test
  fun deriveGenresFromScanned_yields_one_row_per_split_genre() {
    val tracks = listOf(
      scanned(1, "T1", genre = "Rock", additionalGenres = listOf("Pop")),
      scanned(2, "T2", genre = "Jazz"),
    )
    val rows = Mapping.deriveGenresFromScanned(tracks).map { it.name }
    assertEquals(listOf("Rock", "Pop", "Jazz"), rows)
  }
}
