package com.eight87.tonearm.ui.library

import com.eight87.tonearm.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D.15.6 — overflow-menu actions, decoupled from Compose. The screen
 * dispatches each action through one of:
 *  - onAddToQueue(track)
 *  - onAddToPlaylist(track)
 *  - onGoToAlbum(track)   → resolves to (track.album, track.albumArtist|artist)
 *  - onGoToArtist(track)  → resolves to (track.albumArtist | track.artist)
 *
 * We pin the resolution rules here so the destinations match what the
 * AlbumDetail / ArtistDetail screens expect.
 */
class TrackOverflowMenuTest {

  private fun track(
    title: String = "T",
    album: String? = "A",
    artist: String? = "X",
    albumArtist: String? = null,
  ) = Track(
    id = 1L,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = 0L,
    trackNumber = null,
    year = null,
    genre = null,
    data = "/audio/1.flac",
    dateAddedSeconds = 0L,
  )

  private fun resolveAlbumKey(track: Track): Pair<String, String?>? {
    val name = track.album ?: return null
    return name to (track.albumArtist ?: track.artist)
  }

  private fun resolveArtistName(track: Track): String? =
    track.albumArtist?.takeIf { it.isNotBlank() } ?: track.artist?.takeIf { it.isNotBlank() }

  @Test fun go_to_album_uses_album_artist_when_present() {
    val t = track(album = "Velvet Den", albumArtist = "The Synth Foxes", artist = "feat. Bear")
    assertEquals("Velvet Den" to "The Synth Foxes", resolveAlbumKey(t))
  }

  @Test fun go_to_album_falls_back_to_artist_when_album_artist_missing() {
    val t = track(album = "Velvet Den", albumArtist = null, artist = "The Synth Foxes")
    assertEquals("Velvet Den" to "The Synth Foxes", resolveAlbumKey(t))
  }

  @Test fun go_to_album_returns_null_when_track_has_no_album() {
    assertNull(resolveAlbumKey(track(album = null)))
  }

  @Test fun go_to_artist_prefers_album_artist() {
    assertEquals("Foxes", resolveArtistName(track(albumArtist = "Foxes", artist = "Bear")))
  }

  @Test fun go_to_artist_falls_back_to_artist_when_album_artist_blank() {
    assertEquals("Bear", resolveArtistName(track(albumArtist = " ", artist = "Bear")))
  }

  @Test fun go_to_artist_returns_null_when_both_missing() {
    assertNull(resolveArtistName(track(albumArtist = null, artist = null)))
  }
}
