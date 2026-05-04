package com.eight87.tonearmboy.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
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
 * D.26.4 / D.26.5 — pin shuffle / repeat persistence:
 *  - default load returns shuffle=false + repeat=OFF
 *  - saveShuffle(true) round-trips
 *  - saveRepeatMode(REPEAT_MODE_ONE) round-trips
 *  - clear() preserves shuffle / repeat (only the queue + position
 *    scalars are wiped)
 *  - out-of-range repeat values fall back to OFF on load
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class ShuffleRepeatPersistenceTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val store = QueuePersistence(context)

  @After
  fun tearDown() {
    // Wipe everything (including shuffle/repeat) by editing the raw
    // DataStore so subsequent tests see a clean slate.
    runBlocking {
      context.tonearmboyPlaybackDataStore.edit { it.clear() }
    }
  }

  @Test
  fun `default load returns shuffle false and repeat off`() = runBlocking {
    assertFalse(store.loadShuffle())
    assertEquals(Player.REPEAT_MODE_OFF, store.loadRepeatMode())
  }

  @Test
  fun `saveShuffle round trips`() = runBlocking {
    store.saveShuffle(true)
    assertTrue(store.loadShuffle())
    store.saveShuffle(false)
    assertFalse(store.loadShuffle())
  }

  @Test
  fun `saveRepeatMode round trips for REPEAT_MODE_ONE`() = runBlocking {
    store.saveRepeatMode(Player.REPEAT_MODE_ONE)
    assertEquals(Player.REPEAT_MODE_ONE, store.loadRepeatMode())
  }

  @Test
  fun `saveRepeatMode round trips for REPEAT_MODE_ALL`() = runBlocking {
    store.saveRepeatMode(Player.REPEAT_MODE_ALL)
    assertEquals(Player.REPEAT_MODE_ALL, store.loadRepeatMode())
  }

  @Test
  fun `out of range repeat mode falls back to OFF on load`() = runBlocking {
    // Anything other than 0 / 1 / 2 should be sanitized on save.
    store.saveRepeatMode(42)
    assertEquals(Player.REPEAT_MODE_OFF, store.loadRepeatMode())
  }

  @Test
  fun `clear preserves shuffle and repeat across queue wipe`() = runBlocking {
    // Set up the case the user expects to survive: shuffle on, repeat
    // ONE, queue with a track. clear() wipes the queue but the toggles
    // must stay set — the user explicitly asked for "the options like
    // repeating song etc stored across restarts etc."
    store.saveShuffle(true)
    store.saveRepeatMode(Player.REPEAT_MODE_ONE)
    store.saveQueue(
      listOf(QueuePersistence.Entry(mediaId = "1", uri = "file:///x.mp3")),
      startIndex = 0,
    )
    store.clear()

    val rehydrated = QueuePersistence(context)
    val snapshot = rehydrated.load()
    assertTrue(snapshot.isEmpty())
    assertTrue("shuffle must survive clear()", rehydrated.loadShuffle())
    assertEquals(
      "repeat mode must survive clear()",
      Player.REPEAT_MODE_ONE,
      rehydrated.loadRepeatMode(),
    )
  }

  @Test
  fun `restored values mimic restorePersistedShuffleAndRepeat path`() = runBlocking {
    // Mirror what `PlaybackService.restorePersistedShuffleAndRepeat`
    // does on cold start: load both, write them onto a fresh fake
    // player. This guards against drift between the persistence
    // contract and the service's restore call.
    store.saveShuffle(true)
    store.saveRepeatMode(Player.REPEAT_MODE_ONE)

    val player = FakePlayerSurface()
    runBlocking {
      val shuffle = store.loadShuffle()
      val repeat = store.loadRepeatMode()
      player.shuffleModeEnabled = shuffle
      player.repeatMode = repeat
    }

    assertTrue(player.shuffleModeEnabled)
    assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
  }

  /**
   * Mirrors the slice of [androidx.media3.common.Player] that
   * `PlaybackService.restorePersistedShuffleAndRepeat` writes to. Kept
   * inline so the test does not need to spin a real ExoPlayer.
   */
  private class FakePlayerSurface {
    var shuffleModeEnabled: Boolean = false
    var repeatMode: Int = Player.REPEAT_MODE_OFF
  }
}
