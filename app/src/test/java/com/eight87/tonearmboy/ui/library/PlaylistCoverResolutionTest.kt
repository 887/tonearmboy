package com.eight87.tonearmboy.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.27.6 — pin the playlist tile cover-resolution chain.
 *
 *   chosen URI  >  first-track album-art  >  letter avatar
 *
 * The chosen URI can be either:
 *  - a `tonearmboy-albumart://<id>` opaque scheme pointing at a
 *    MediaStore album-art id (used by the "pick from a track" path,
 *    same renderer as the album-art fallback), or
 *  - a `content://...` SAF URI from the device picker, which the tile
 *    feeds straight into Coil via `AsyncImage`.
 */
class PlaylistCoverResolutionTest {

  @Test fun custom_content_uri_takes_priority() {
    val resolved = resolvePlaylistCover(
      coverUri = "content://media/external/images/media/42",
      firstTrackAlbumId = 1234L,
    )
    assertTrue(resolved is PlaylistCoverSource.Custom)
    assertEquals(
      "content://media/external/images/media/42",
      (resolved as PlaylistCoverSource.Custom).uri,
    )
  }

  @Test fun tonearmboy_albumart_scheme_resolves_to_album_art_branch() {
    val resolved = resolvePlaylistCover(
      coverUri = albumArtSchemeUri(99L),
      firstTrackAlbumId = 1234L,
    )
    assertTrue(resolved is PlaylistCoverSource.AlbumArt)
    assertEquals(99L, (resolved as PlaylistCoverSource.AlbumArt).albumId)
  }

  @Test fun tonearmboy_albumart_with_unparseable_id_falls_through_to_first_track() {
    val resolved = resolvePlaylistCover(
      coverUri = "tonearmboy-albumart://not-a-number",
      firstTrackAlbumId = 1234L,
    )
    assertTrue(resolved is PlaylistCoverSource.AlbumArt)
    assertEquals(1234L, (resolved as PlaylistCoverSource.AlbumArt).albumId)
  }

  @Test fun no_chosen_uri_falls_back_to_first_track_album_art() {
    val resolved = resolvePlaylistCover(
      coverUri = null,
      firstTrackAlbumId = 555L,
    )
    assertTrue(resolved is PlaylistCoverSource.AlbumArt)
    assertEquals(555L, (resolved as PlaylistCoverSource.AlbumArt).albumId)
  }

  @Test fun blank_uri_treated_as_null_falls_back_to_first_track_album_art() {
    val resolved = resolvePlaylistCover(coverUri = "   ", firstTrackAlbumId = 7L)
    assertTrue(resolved is PlaylistCoverSource.AlbumArt)
    assertEquals(7L, (resolved as PlaylistCoverSource.AlbumArt).albumId)
  }

  @Test fun no_chosen_uri_and_no_album_art_falls_back_to_letter() {
    val resolved = resolvePlaylistCover(coverUri = null, firstTrackAlbumId = null)
    assertTrue(resolved is PlaylistCoverSource.Letter)
  }

  @Test fun letterFor_returns_uppercase_first_letter_when_alphabetic() {
    assertEquals("L", letterFor("late night drives"))
    assertEquals("M", letterFor("morning queue"))
  }

  @Test fun letterFor_falls_back_to_hash_for_non_alpha() {
    assertEquals("#", letterFor("123 going up"))
    assertEquals("#", letterFor(""))
    assertEquals("#", letterFor("!!!"))
  }

  @Test fun albumArtSchemeUri_round_trips() {
    val uri = albumArtSchemeUri(12345L)
    val resolved = resolvePlaylistCover(coverUri = uri, firstTrackAlbumId = null)
    assertTrue(resolved is PlaylistCoverSource.AlbumArt)
    assertEquals(12345L, (resolved as PlaylistCoverSource.AlbumArt).albumId)
  }
}
