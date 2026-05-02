package com.eight87.tonearm.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * D.12.2 — assert that the [MediaItem] / [MediaMetadata] payload our
 * playback stack hands the `MediaSession` carries every field the
 * platform lock-screen renderer needs.
 *
 * Phase E.2 flows the metadata exactly the same way E.1 does: System
 * UI populates the lock-screen "now playing" surface from
 * `MediaSession.metadata`, which mirrors the active player's current
 * `MediaItem.mediaMetadata`. We can therefore unit-test the lock-screen
 * input shape without spinning a real `MediaSession` (which would need
 * a Looper-backed real ExoPlayer and runs as part of the integration
 * smoke).
 *
 * The integration assertion in `scripts/playback-smoke-test.sh` locks
 * the AVD via `KEYCODE_POWER` and reads `dumpsys media_session` to
 * confirm `state=PLAYING(3)` and the description matches the metadata
 * we set here.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class MediaSessionMetadataTest {

  @Test
  fun metadata_carries_lockscreen_inputs() {
    val item = buildItem(
      title = "Cipher Light",
      artist = "The Synth Foxes",
      album = "Velvet Den",
      albumArtist = "The Synth Foxes",
      artwork = "file:///music/velvet-den/cipher.mp3",
    )

    val md = item.mediaMetadata
    // The lock-screen renderer keys off these fields directly.
    assertEquals("Cipher Light", md.title?.toString())
    assertEquals("The Synth Foxes", md.artist?.toString())
    assertEquals("Velvet Den", md.albumTitle?.toString())
    assertEquals("The Synth Foxes", md.albumArtist?.toString())
    assertEquals("file:///music/velvet-den/cipher.mp3", md.artworkUri?.toString())
  }

  @Test
  fun media_id_round_trips() {
    val item = MediaItem.Builder()
      .setMediaId("42")
      .setUri("file:///x.mp3")
      .build()
    assertEquals("42", item.mediaId)
  }

  @Test
  fun missing_album_artist_still_renders_artist() {
    // A common edge case in real libraries: tracks without an
    // ALBUM_ARTIST tag fall back to the per-track ARTIST. Lock-screen
    // should still show "Quiet Hours" rather than blank.
    val item = buildItem(
      title = "Pawprints in Snow",
      artist = "Quiet Hours",
      album = "Field Recordings",
      albumArtist = null,
      artwork = null,
    )
    val md = item.mediaMetadata
    assertEquals("Quiet Hours", md.artist?.toString())
    // Album artist deliberately not set — the renderer falls back to
    // artist, which is the Auxio-equivalent semantic.
    assertNull(md.albumArtist)
  }

  @Test
  fun queue_persistence_round_trip_preserves_lockscreen_inputs() {
    val entry = QueuePersistence.Entry(
      mediaId = "1",
      uri = "file:///music/velvet-den/cipher.mp3",
      title = "Cipher Light",
      artist = "The Synth Foxes",
      album = "Velvet Den",
      albumArtist = "The Synth Foxes",
      artworkUri = "file:///music/velvet-den/cipher.mp3",
    )
    val snapshot = QueuePersistence.Snapshot(
      items = listOf(entry),
      startIndex = 0,
      startPositionMs = 12_000L,
    )
    val rebuilt = snapshot.toMediaItemsWithStartPosition()
    val md = rebuilt.mediaItems.first().mediaMetadata
    assertNotNull(md.title)
    assertEquals("Cipher Light", md.title?.toString())
    assertEquals("The Synth Foxes", md.artist?.toString())
    assertEquals("Velvet Den", md.albumTitle?.toString())
    assertEquals("The Synth Foxes", md.albumArtist?.toString())
    assertEquals(12_000L, rebuilt.startPositionMs)
  }

  @Test
  fun fromMediaItem_handles_minimal_payload() {
    // A bare MediaItem (just uri + id) round-trips with null metadata
    // — the lock-screen renderer falls back to the URI's filename.
    val item = MediaItem.Builder()
      .setMediaId("99")
      .setUri("file:///nameless.mp3")
      .build()
    val entry = QueuePersistence.fromMediaItem(item)
    assertEquals("99", entry.mediaId)
    assertEquals("file:///nameless.mp3", entry.uri)
    assertNull(entry.title)
    assertNull(entry.artist)
  }

  @Test
  fun artwork_uri_is_a_file_or_content_uri() {
    // We deliberately point artworkUri at the audio file URI itself
    // — Media3's DataSourceBitmapLoader extracts embedded ID3v2 /
    // FLAC pictures from the audio file. Verify Uri parses cleanly so
    // the bitmap loader doesn't choke on a malformed string.
    val item = buildItem(
      title = "T",
      artist = "A",
      album = "AT",
      albumArtist = "AA",
      artwork = "file:///music/test.mp3",
    )
    val artworkUri = item.mediaMetadata.artworkUri
    assertNotNull(artworkUri)
    val scheme = artworkUri!!.scheme
    assertTrue(
      "expected file:// or content:// scheme, got $scheme",
      scheme == "file" || scheme == "content",
    )
  }

  private fun buildItem(
    title: String,
    artist: String,
    album: String,
    albumArtist: String?,
    artwork: String?,
  ): MediaItem {
    val builder = MediaMetadata.Builder()
      .setTitle(title)
      .setArtist(artist)
      .setAlbumTitle(album)
    if (albumArtist != null) builder.setAlbumArtist(albumArtist)
    if (artwork != null) builder.setArtworkUri(Uri.parse(artwork))
    return MediaItem.Builder()
      .setMediaId("test")
      .setUri("file:///x.mp3")
      .setMediaMetadata(builder.build())
      .build()
  }
}
