package com.eight87.tonearmboy.data.playlist

import com.eight87.tonearmboy.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase H.7.3 — JSON envelope round-trip + fuzzy-match resolver. Pure
 * helpers so we don't need Room / Robolectric.
 */
class PlaylistExportImportTest {

  private fun track(
    id: Long,
    title: String,
    artist: String?,
    album: String?,
    durationMs: Long = 180_000L,
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = artist,
    durationMs = durationMs,
    trackNumber = null,
    year = null,
    genre = null,
    data = "/sdcard/Music/$title.mp3",
    dateAddedSeconds = 0L,
  )

  @Test
  fun envelope_round_trips_through_json() {
    val original = PlaylistBackupEnvelope(
      exportedAt = "2026-05-02T00:00:00Z",
      playlists = listOf(
        PlaylistBackup(
          name = "Mixtape",
          tracks = listOf(
            TrackRef("Levitating", "Dua Lipa", "Future Nostalgia", 200_000L),
            TrackRef("Bohemian Rhapsody", "Queen", "A Night at the Opera", 354_000L),
          ),
        ),
      ),
    )
    val raw = PlaylistBackupCodec.encode(original)
    val decoded = PlaylistBackupCodec.decode(raw)
    assertEquals(original, decoded)
  }

  @Test
  fun resolver_matches_exact_title_artist_pairs() {
    val library = listOf(
      track(1, "Levitating", "Dua Lipa", "Future Nostalgia"),
      track(2, "Bohemian Rhapsody", "Queen", "A Night at the Opera"),
    )
    val envelope = PlaylistBackupEnvelope(
      exportedAt = "now",
      playlists = listOf(
        PlaylistBackup(
          name = "Roundtrip",
          tracks = listOf(
            TrackRef("Levitating", "Dua Lipa", "Future Nostalgia"),
            TrackRef("Bohemian Rhapsody", "Queen", "A Night at the Opera"),
          ),
        ),
      ),
    )
    val res = resolvePlaylistImport(envelope, library)
    assertEquals(0, res.unmatchedCount)
    assertEquals(listOf(1L, 2L), res.resolved["Roundtrip"])
  }

  @Test
  fun resolver_matches_case_insensitively() {
    val library = listOf(
      track(7, "Levitating", "Dua Lipa", "Future Nostalgia"),
    )
    val envelope = PlaylistBackupEnvelope(
      exportedAt = "now",
      playlists = listOf(
        PlaylistBackup(
          name = "Casing",
          tracks = listOf(
            // Title casing differs slightly; artist casing differs too.
            TrackRef("LEVITATING", "dua lipa", "future nostalgia"),
          ),
        ),
      ),
    )
    val res = resolvePlaylistImport(envelope, library)
    assertEquals(0, res.unmatchedCount)
    assertEquals(listOf(7L), res.resolved["Casing"])
  }

  @Test
  fun resolver_falls_back_to_title_only_when_artist_differs() {
    val library = listOf(
      track(11, "Crazy in Love", "Beyoncé", "Dangerously in Love"),
    )
    val envelope = PlaylistBackupEnvelope(
      exportedAt = "now",
      playlists = listOf(
        PlaylistBackup(
          name = "Featuring",
          tracks = listOf(
            // The exporter wrote "Beyoncé feat. Jay-Z" but the library
            // only has the primary artist name.
            TrackRef("Crazy in Love", "Beyoncé feat. Jay-Z", null),
          ),
        ),
      ),
    )
    val res = resolvePlaylistImport(envelope, library)
    assertEquals(0, res.unmatchedCount)
    assertEquals(listOf(11L), res.resolved["Featuring"])
  }

  @Test
  fun resolver_uses_album_as_tiebreaker_for_duplicate_titles() {
    val library = listOf(
      track(20, "Yesterday", "The Beatles", "Help!"),
      track(21, "Yesterday", "The Beatles", "1962-1966"),
    )
    val envelope = PlaylistBackupEnvelope(
      exportedAt = "now",
      playlists = listOf(
        PlaylistBackup(
          name = "Albums",
          tracks = listOf(TrackRef("Yesterday", "The Beatles", "1962-1966")),
        ),
      ),
    )
    val res = resolvePlaylistImport(envelope, library)
    assertEquals(0, res.unmatchedCount)
    assertEquals(listOf(21L), res.resolved["Albums"])
  }

  @Test
  fun resolver_counts_unmatched_tracks() {
    val library = listOf(
      track(1, "Levitating", "Dua Lipa", "Future Nostalgia"),
    )
    val envelope = PlaylistBackupEnvelope(
      exportedAt = "now",
      playlists = listOf(
        PlaylistBackup(
          name = "Half",
          tracks = listOf(
            TrackRef("Levitating", "Dua Lipa", null),
            TrackRef("Definitely Not In Library", "Nobody", null),
          ),
        ),
      ),
    )
    val res = resolvePlaylistImport(envelope, library)
    assertEquals(1, res.unmatchedCount)
    assertEquals(listOf(1L), res.resolved["Half"])
  }

  @Test
  fun default_file_name_uses_iso_date() {
    val name = PlaylistBackupCodec.defaultFileName(
      java.util.Date(0L), // 1970-01-01
    )
    assertEquals("tonearmboy-playlists-1970-01-01.json", name)
  }
}
