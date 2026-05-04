package com.eight87.tonearmboy.playback.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eight87.tonearmboy.playback.QueuePersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * D.12.1 — Robolectric coverage of [PlaybackNotificationProvider].
 *
 * Phase E.1 design note: on API 33+, System UI populates the
 * `MediaStyle` notification directly from the active `MediaSession`'s
 * `MediaMetadata` and transport state. The
 * `MediaNotification.Provider` we install only owns the channel + id,
 * plus the pre-33 fallback rendering path. So the unit-level surface
 * we can assert here is:
 *
 *   - the right channel id / importance / silent config is created;
 *   - building the provider is idempotent and survives an existing
 *     channel without clobbering its config;
 *   - the rich `MediaMetadata` we hand to a [MediaItem] (the path the
 *     System UI actually reads on API 33+) round-trips title / artist
 *     / album / album-artist / artwork URI exactly the way the
 *     notification renderer expects them.
 *
 * Anything that requires the real SystemUI process — pixel-level
 * layout, the actual button row in the shade — is exercised by the
 * integration assertions in `scripts/playback-smoke-test.sh` against
 * the running AVD via `dumpsys notification --noredact`.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class MediaNotificationProviderTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val nm: NotificationManager = context.getSystemService()!!

  @Test
  fun build_creates_low_importance_silent_channel() {
    PlaybackNotificationProvider.build(context)
    val channel = nm.getNotificationChannel(PlaybackNotificationProvider.CHANNEL_ID)
    assertNotNull("channel ${PlaybackNotificationProvider.CHANNEL_ID} should exist", channel)
    requireNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    assertEquals("Playback", channel.name?.toString())
    // No alert noise on a control surface.
    assertFalse(channel.shouldVibrate())
    assertFalse(channel.shouldShowLights())
  }

  @Test
  fun build_uses_stable_notification_id() {
    // The smoke script + Media3 internals key off this id; bumping it
    // would silently break notification swap behaviour. Lock it.
    assertEquals(1001, PlaybackNotificationProvider.NOTIFICATION_ID)
  }

  @Test
  fun build_is_idempotent() {
    PlaybackNotificationProvider.build(context)
    val first = nm.getNotificationChannel(PlaybackNotificationProvider.CHANNEL_ID)
    PlaybackNotificationProvider.build(context)
    val second = nm.getNotificationChannel(PlaybackNotificationProvider.CHANNEL_ID)
    assertNotNull(first)
    assertNotNull(second)
    assertEquals(first!!.id, second!!.id)
    assertEquals(first.importance, second.importance)
  }

  @Test
  fun pre_existing_channel_is_not_clobbered() {
    // If the user (or a prior install) named the channel something
    // else, the platform forbids us renaming it. We just keep going.
    val existing = NotificationChannel(
      PlaybackNotificationProvider.CHANNEL_ID,
      "User Renamed",
      NotificationManager.IMPORTANCE_LOW,
    )
    nm.createNotificationChannel(existing)
    PlaybackNotificationProvider.build(context)
    val channel = nm.getNotificationChannel(PlaybackNotificationProvider.CHANNEL_ID)
    assertNotNull(channel)
    // The platform doesn't let us rename, so the user's name persists.
    assertEquals("User Renamed", channel!!.name?.toString())
  }

  @Test
  fun media_item_metadata_round_trips_to_renderer_inputs() {
    // This is the canonical Phase E.1 path: System UI on API 33+
    // reads from MediaSession.metadata, which in turn comes from the
    // currently-playing MediaItem. The notification renderer wants
    // title, artist, album title, album artist, and artwork URI to
    // produce a MediaStyle layout with name / subtitle / large icon.
    val item = MediaItem.Builder()
      .setMediaId("42")
      .setUri("file:///music/velvet-den/cipher.mp3")
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle("Cipher Light")
          .setArtist("The Synth Foxes")
          .setAlbumTitle("Velvet Den")
          .setAlbumArtist("The Synth Foxes")
          .setArtworkUri(android.net.Uri.parse("file:///music/velvet-den/cipher.mp3"))
          .build(),
      )
      .build()

    val md = item.mediaMetadata
    assertEquals("Cipher Light", md.title?.toString())
    assertEquals("The Synth Foxes", md.artist?.toString())
    assertEquals("Velvet Den", md.albumTitle?.toString())
    assertEquals("The Synth Foxes", md.albumArtist?.toString())
    assertEquals(
      "file:///music/velvet-den/cipher.mp3",
      md.artworkUri?.toString(),
    )
  }

  @Test
  fun queue_persistence_entry_to_media_item_keeps_renderer_inputs() {
    // After a process kill, the resumed MediaItem comes back through
    // QueuePersistence — verify it carries every field the
    // notification renderer needs, so the post-resumption notification
    // is not visually different from the pre-kill one.
    val entry = QueuePersistence.Entry(
      mediaId = "42",
      uri = "file:///music/velvet-den/cipher.mp3",
      title = "Cipher Light",
      artist = "The Synth Foxes",
      album = "Velvet Den",
      albumArtist = "The Synth Foxes",
      artworkUri = "file:///music/velvet-den/cipher.mp3",
    )
    val rebuilt = QueuePersistence.Snapshot(
      items = listOf(entry),
      startIndex = 0,
      startPositionMs = 0L,
    ).toMediaItemsWithStartPosition()
    val md = rebuilt.mediaItems.first().mediaMetadata
    assertEquals("Cipher Light", md.title?.toString())
    assertEquals("The Synth Foxes", md.artist?.toString())
    assertEquals("Velvet Den", md.albumTitle?.toString())
    assertEquals("The Synth Foxes", md.albumArtist?.toString())
    assertEquals(entry.artworkUri, md.artworkUri?.toString())
  }

  @Test
  fun built_provider_is_non_null() {
    val provider = PlaybackNotificationProvider.build(context)
    assertNotNull(provider)
    // Sanity: the provider class is the Media3 default (we keep
    // the actual rendering on the library side); we are not
    // reflecting on the package name to allow future Media3 swaps.
    assertTrue(
      "expected a Media3 DefaultMediaNotificationProvider variant",
      provider.javaClass.simpleName.contains("DefaultMediaNotificationProvider"),
    )
  }
}
