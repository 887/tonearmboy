package com.eight87.tonearm.playback

import android.content.Context
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
 * `Context.tonearmPlaybackDataStore`. Robolectric gives us a real
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
        artist = "Tonearm",
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
}
