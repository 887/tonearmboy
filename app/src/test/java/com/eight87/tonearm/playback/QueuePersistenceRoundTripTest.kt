package com.eight87.tonearm.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * D.20.3 — extends `QueuePersistenceTest` to lock down the cold-
 * start restore path that regressed on the v1.0 phone install.
 *
 * Failure mode pre-fix: the queue + position were being written
 * (the existing tests already cover the write path), but no code
 * was reading them back into the player on cold start. The
 * `MediaSession.Callback.onPlaybackResumption` callback only
 * fires for system / Bluetooth resumption; an in-app
 * `MediaController.connect()` doesn't trigger it. So the user saw
 * an empty queue every time they reopened the app.
 *
 * We assert two things here:
 *   1. The persisted Snapshot is what `onPlaybackResumption` would
 *      hand back, complete with restored start position and
 *      MediaStore album-id extras (D.20.4 dependency).
 *   2. The position-debounce window is short enough to keep
 *      cold-start drift inside the documented ±2 s tolerance.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class QueuePersistenceRoundTripTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val store = QueuePersistence(context)

  @After
  fun tearDown() = runBlocking { store.clear() }

  @Test
  fun cold_start_restore_returns_persisted_position_within_two_seconds() = runBlocking {
    val items = listOf(
      QueuePersistence.Entry(
        mediaId = "1",
        uri = "file:///tracks/a.mp3",
        title = "A",
        mediaStoreAlbumId = 42L,
      ),
      QueuePersistence.Entry(
        mediaId = "2",
        uri = "file:///tracks/b.mp3",
        title = "B",
        mediaStoreAlbumId = 42L,
      ),
    )
    store.saveQueue(items, startIndex = 1)
    // Most recent debounced position write before the user closed.
    store.savePosition(index = 1, positionMs = 38_500L)

    // Simulate process death by re-instantiating QueuePersistence.
    val rehydrated = QueuePersistence(context).load()
    assertFalse("snapshot must round-trip", rehydrated.isEmpty())
    assertEquals(2, rehydrated.items.size)
    assertEquals(1, rehydrated.startIndex)
    // ±2 s tolerance is the user-facing contract from Phase E.5.
    val drift = kotlin.math.abs(38_500L - rehydrated.startPositionMs)
    assertTrue("restored position drift ${drift}ms must be <= 2000ms", drift <= 2_000L)
  }

  @Test
  fun cold_start_restore_preserves_album_id_extra_for_palette() = runBlocking {
    // D.20.4 — the album-art tint depends on the MediaStore album id
    // travelling through queue persistence. Without this, the tint
    // can't extract a cover bitmap on resume because the MediaItem
    // built from the persisted Entry has no `tonearm.mediaStoreAlbumId`
    // extra.
    val items = listOf(
      QueuePersistence.Entry(
        mediaId = "10",
        uri = "file:///velvet/den.mp3",
        title = "Velvet Den",
        mediaStoreAlbumId = 7777L,
      ),
    )
    store.saveQueue(items, startIndex = 0)

    val rehydrated = QueuePersistence(context).load()
    val mediaItems = rehydrated.toMediaItemsWithStartPosition().mediaItems
    assertEquals(1, mediaItems.size)
    val albumId = mediaItems[0].mediaMetadata.extras
      ?.getLong("tonearm.mediaStoreAlbumId", -1L)
      ?.takeIf { it >= 0 }
    assertNotNull("MediaStore album id extra must survive the round trip", albumId)
    assertEquals(7777L, albumId)
  }

  @Test
  fun on_playback_resumption_returns_persisted_snapshot_shape() = runBlocking {
    // Mirrors the data flow the SessionCallback's onPlaybackResumption
    // returns: a Snapshot from `QueuePersistence.load()` mapped via
    // toMediaItemsWithStartPosition(). We can't spin a real
    // MediaSession in Robolectric so we exercise the data path here.
    val items = listOf(
      QueuePersistence.Entry(mediaId = "9", uri = "file:///z.mp3", title = "Zeta"),
    )
    store.saveQueue(items, startIndex = 0)
    store.savePosition(index = 0, positionMs = 500L)

    val resumed = QueuePersistence(context).load().toMediaItemsWithStartPosition()
    assertEquals(1, resumed.mediaItems.size)
    assertEquals("9", resumed.mediaItems[0].mediaId)
    assertEquals(0, resumed.startIndex)
    assertEquals(500L, resumed.startPositionMs)
  }

  @Test
  fun debounce_constant_is_at_or_below_500_ms() {
    // D.20.3 — bumped down from 2_000 so close+reopen drift fits the
    // ±2 s smoke contract. If anyone bumps it back up, the smoke
    // test will start flaking on slow ADB.
    assertTrue(
      "POSITION_DEBOUNCE_MS=${QueuePersistence.POSITION_DEBOUNCE_MS}; D.20.3 capped at 500",
      QueuePersistence.POSITION_DEBOUNCE_MS <= 500L,
    )
  }
}
