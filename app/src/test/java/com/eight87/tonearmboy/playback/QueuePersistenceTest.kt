package com.eight87.tonearmboy.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the Phase E.5 queue-persistence layer.
 *
 * The DataStore file lives in `getNoBackupFilesDir()` /
 * `Context.tonearmboyPlaybackDataStore`. Robolectric gives us a real
 * temp dir per test; cleanup happens in [tearDown] so a second run
 * inside the same JVM does not see stale state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class QueuePersistenceTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val store = QueuePersistence(context)

  @After
  fun tearDown() = runBlocking {
    store.clear()
  }

  @Test
  fun `load returns Empty when nothing persisted`() = runBlocking {
    val snapshot = store.load()
    assertTrue(snapshot.isEmpty())
    assertEquals(0, snapshot.startIndex)
    assertEquals(0L, snapshot.startPositionMs)
  }

  @Test
  fun `saveQueue then load returns persisted entries`() = runBlocking {
    val items = listOf(
      QueuePersistence.Entry(
        mediaId = "1",
        uri = "file:///music/a.mp3",
        title = "Alpha",
        artist = "Tonearmboy",
        album = "Smoke",
      ),
      QueuePersistence.Entry(
        mediaId = "2",
        uri = "file:///music/b.mp3",
        title = "Beta",
      ),
    )
    store.saveQueue(items, startIndex = 1)

    val snapshot = store.load()
    assertFalse(snapshot.isEmpty())
    assertEquals(2, snapshot.items.size)
    assertEquals("Alpha", snapshot.items[0].title)
    assertEquals("file:///music/b.mp3", snapshot.items[1].uri)
    assertEquals(1, snapshot.startIndex)
    assertEquals(0L, snapshot.startPositionMs)
  }

  @Test
  fun `savePosition does not clobber the queue`() = runBlocking {
    val items = listOf(QueuePersistence.Entry(mediaId = "1", uri = "file:///x.mp3"))
    store.saveQueue(items, startIndex = 0)
    store.savePosition(index = 0, positionMs = 12_345L)

    val snapshot = store.load()
    assertEquals(1, snapshot.items.size)
    assertEquals(12_345L, snapshot.startPositionMs)
  }

  @Test
  fun `clear empties the store`() = runBlocking {
    val items = listOf(QueuePersistence.Entry(mediaId = "1", uri = "file:///x.mp3"))
    store.saveQueue(items, startIndex = 0)
    store.clear()

    val snapshot = store.load()
    assertTrue(snapshot.isEmpty())
  }

  @Test
  fun `fromMediaItem captures all relevant metadata`() {
    val item = MediaItem.Builder()
      .setMediaId("42")
      .setUri("file:///foo.mp3")
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle("T")
          .setArtist("A")
          .setAlbumTitle("AT")
          .setAlbumArtist("AA")
          .build(),
      )
      .build()

    val entry = QueuePersistence.fromMediaItem(item)
    assertEquals("42", entry.mediaId)
    assertEquals("file:///foo.mp3", entry.uri)
    assertEquals("T", entry.title)
    assertEquals("A", entry.artist)
    assertEquals("AT", entry.album)
    assertEquals("AA", entry.albumArtist)
  }

  @Test
  fun `Snapshot toMediaItemsWithStartPosition rebuilds player input`() {
    val snapshot = QueuePersistence.Snapshot(
      items = listOf(
        QueuePersistence.Entry(mediaId = "1", uri = "file:///a.mp3", title = "A"),
        QueuePersistence.Entry(mediaId = "2", uri = "file:///b.mp3", title = "B"),
      ),
      startIndex = 1,
      startPositionMs = 9_999L,
    )
    val rebuilt = snapshot.toMediaItemsWithStartPosition()
    assertEquals(2, rebuilt.mediaItems.size)
    assertEquals("1", rebuilt.mediaItems[0].mediaId)
    assertEquals("B", rebuilt.mediaItems[1].mediaMetadata.title.toString())
    assertEquals(1, rebuilt.startIndex)
    assertEquals(9_999L, rebuilt.startPositionMs)
  }

  // -- D.12.5 — process-death survival -------------------------------------

  @Test
  fun `D12_5 debounce constant is reasonable for human-perceptible kill window`() = runBlocking {
    // The smoke script asserts kill-recovery within ±2s of the kill
    // point. POSITION_DEBOUNCE_MS bounds how stale the persisted
    // position can be. If someone bumps it past ~3s the integration
    // assertion will start flaking.
    assertTrue(
      "POSITION_DEBOUNCE_MS=${QueuePersistence.POSITION_DEBOUNCE_MS}; " +
        "must stay under 3000ms so kill-recovery resumes within ±2s",
      QueuePersistence.POSITION_DEBOUNCE_MS in 500L..3_000L,
    )
  }

  @Test
  fun `D12_5 mid-track position write does not lose queue contents`() = runBlocking {
    // Simulates: user is mid-track, the position-debounce ticker
    // fires, then the OS kills the app. On next launch, the queue
    // must still be intact and the position must be the most recent
    // one written.
    val items = listOf(
      QueuePersistence.Entry(mediaId = "1", uri = "file:///a.mp3", title = "Alpha"),
      QueuePersistence.Entry(mediaId = "2", uri = "file:///b.mp3", title = "Beta"),
      QueuePersistence.Entry(mediaId = "3", uri = "file:///c.mp3", title = "Gamma"),
    )
    store.saveQueue(items, startIndex = 1)
    // Three debounce ticks worth of position writes:
    store.savePosition(index = 1, positionMs = 2_000L)
    store.savePosition(index = 1, positionMs = 4_000L)
    store.savePosition(index = 1, positionMs = 6_000L)

    // "Process death" — drop and recreate the persistence handle.
    val rehydrated = QueuePersistence(context).load()
    assertFalse(rehydrated.isEmpty())
    assertEquals(3, rehydrated.items.size)
    assertEquals("Alpha", rehydrated.items[0].title)
    assertEquals("Beta", rehydrated.items[1].title)
    assertEquals("Gamma", rehydrated.items[2].title)
    assertEquals(1, rehydrated.startIndex)
    assertEquals(6_000L, rehydrated.startPositionMs)
  }

  @Test
  fun `D12_5 saveQueue resets position to zero per contract`() = runBlocking {
    // PlaybackService relies on this: after a queue replacement, the
    // persisted position MUST start at 0 — otherwise the resumed
    // controller would seek into a track it isn't on yet.
    val items = listOf(QueuePersistence.Entry(mediaId = "1", uri = "file:///a.mp3"))
    store.saveQueue(items, startIndex = 0)
    store.savePosition(index = 0, positionMs = 30_000L)

    // Replace the queue. Position MUST reset.
    val newItems = listOf(QueuePersistence.Entry(mediaId = "9", uri = "file:///z.mp3"))
    store.saveQueue(newItems, startIndex = 0)

    val snapshot = store.load()
    assertEquals(1, snapshot.items.size)
    assertEquals("9", snapshot.items[0].mediaId)
    assertEquals(0L, snapshot.startPositionMs)
  }

  @Test
  fun `D12_5 debounce ticker only flushes within debounce window`() = runBlocking {
    // Mirror the schedulePositionPersistTicker contract: write only
    // when the position has actually advanced AND at least
    // POSITION_DEBOUNCE_MS has elapsed since the last write.
    val items = listOf(QueuePersistence.Entry(mediaId = "1", uri = "file:///a.mp3"))
    store.saveQueue(items, startIndex = 0)

    // Simulate the ticker:
    // tick1 (t=2s): position advanced from 0 -> 2000, flush
    val t1 = System.nanoTime()
    store.savePosition(index = 0, positionMs = 2_000L)
    val elapsedMs = (System.nanoTime() - t1) / 1_000_000
    assertTrue(
      "DataStore write should be cheap and well under one debounce window — got ${elapsedMs}ms",
      elapsedMs < QueuePersistence.POSITION_DEBOUNCE_MS,
    )

    // No-op tick (same position, no flush): just don't write.
    // Final tick (t=4s): position advanced.
    store.savePosition(index = 0, positionMs = 4_000L)

    // After kill+restart, the latest debounced position is what we
    // see — older ones have been overwritten in place.
    val snapshot = QueuePersistence(context).load()
    assertEquals(4_000L, snapshot.startPositionMs)
  }

  @Test
  fun `D12_5 corrupted queue json gracefully degrades to empty`() = runBlocking {
    // An older app version, a partial write, or a manual edit of the
    // DataStore file could leave invalid JSON in the queue slot. The
    // service must degrade to "no queue" rather than crash on resume
    // — Phase E.5's `MediaSession.Callback.onPlaybackResumption`
    // returns an `IllegalStateException` future when the load is
    // empty, which Media3 handles as "no resumable session".
    // Simulate corruption by writing invalid JSON into the underlying
    // preference slot directly.
    context.tonearmboyPlaybackDataStore.edit { prefs ->
      prefs[QueuePersistence.KEY_QUEUE_JSON] = "{ this is not json"
      prefs[QueuePersistence.KEY_INDEX] = 0
      prefs[QueuePersistence.KEY_POSITION] = 0L
    }
    val snapshot = store.load()
    assertTrue(
      "load() should degrade to empty Snapshot on corrupt JSON, not throw",
      snapshot.isEmpty(),
    )
  }
}
