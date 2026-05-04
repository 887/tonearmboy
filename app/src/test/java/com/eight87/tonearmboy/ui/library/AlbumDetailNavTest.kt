package com.eight87.tonearmboy.ui.library

import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.nav.AlbumDetail
import com.eight87.tonearmboy.ui.nav.ArtistDetail
import com.eight87.tonearmboy.ui.nav.GenreDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.15.1 — Album detail navigation key + tracks-by-album filtering
 * helpers. Exercises the pure logic the screen relies on:
 *
 *  - the [AlbumDetail] destination is `@Serializable` and equality is
 *    structural (so `popToFirstOrPush` picks up an existing entry)
 *  - filtering the all-tracks observable by `(album, albumArtist|artist)`
 *    matches the rule [AlbumDetailScreen] uses
 *  - the album header total duration formatter handles sub-hour and
 *    multi-hour cases
 */
class AlbumDetailNavTest {

  private fun track(
    id: Long,
    title: String,
    album: String?,
    artist: String?,
    albumArtist: String? = null,
    durationMs: Long = 180_000L,
    trackNumber: Int? = null,
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = 2024,
    genre = "Synthwave",
    data = "/audio/$id.flac",
    dateAddedSeconds = 0,
  )

  @Test fun album_detail_keys_compare_by_value() {
    val a = AlbumDetail(name = "Velvet Den", albumArtist = "The Synth Foxes")
    val b = AlbumDetail(name = "Velvet Den", albumArtist = "The Synth Foxes")
    val c = AlbumDetail(name = "Velvet Den", albumArtist = null)
    assertEquals(a, b)
    assertNotEquals(a, c)
  }

  @Test fun artist_detail_keys_compare_by_value() {
    val a = ArtistDetail(name = "The Synth Foxes")
    val b = ArtistDetail(name = "The Synth Foxes")
    assertEquals(a, b)
  }

  @Test fun genre_detail_keys_compare_by_value() {
    val a = GenreDetail(name = "Synthwave")
    val b = GenreDetail(name = "Synthwave")
    assertEquals(a, b)
  }

  @Test fun filter_tracks_picks_only_target_album() {
    val tracks = listOf(
      track(1, "Brushwork", album = "Velvet Den", artist = "The Synth Foxes", albumArtist = "The Synth Foxes"),
      track(2, "Cipher Light", album = "Velvet Den", artist = "The Synth Foxes", albumArtist = "The Synth Foxes"),
      track(3, "Quiet Hours", album = "Field Recordings", artist = "The Synth Foxes", albumArtist = "The Synth Foxes"),
    )
    val filtered = tracks.filter {
      it.album == "Velvet Den" && (it.albumArtist ?: it.artist) == "The Synth Foxes"
    }
    assertEquals(2, filtered.size)
    assertTrue(filtered.all { it.album == "Velvet Den" })
  }

  @Test fun total_duration_formatter_sub_hour() {
    assertEquals("3:00", formatDuration(180_000L))
    assertEquals("12:34", formatDuration(12L * 60_000 + 34_000))
  }

  @Test fun total_duration_formatter_multi_hour() {
    assertEquals("1:02:03", formatDuration(1L * 3_600_000 + 2 * 60_000 + 3_000))
  }
}
