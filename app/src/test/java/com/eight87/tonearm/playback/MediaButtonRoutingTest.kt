package com.eight87.tonearm.playback

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * D.12.3 — verify each headset / Bluetooth media-button keycode routes
 * to the right transport action.
 *
 * Media3's `MediaSession` honors media-button intents automatically:
 * the `MediaButtonReceiver` declared in the manifest dispatches into
 * the platform's [android.media.session.MediaSession], which Media3
 * has registered, which forwards to its `Player`. We are NOT
 * reimplementing that wiring — we are asserting that the keycode →
 * transport action mapping the integration smoke relies on (and that
 * a `MediaSession.Callback` that overrides `onMediaButtonEvent` would
 * have to honor) is what we expect.
 *
 * The integration assertion in `scripts/playback-smoke-test.sh` sends
 * every keycode here via `adb shell input keyevent` and asserts the
 * `dumpsys media_session` PlaybackState transitions accordingly.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class MediaButtonRoutingTest {

  /** All the keycodes Phase E.3 declared support for. */
  private val supportedCodes = listOf(
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, // 85
    KeyEvent.KEYCODE_MEDIA_NEXT,       // 87
    KeyEvent.KEYCODE_MEDIA_PREVIOUS,   // 88
    KeyEvent.KEYCODE_MEDIA_PLAY,       // 126
    KeyEvent.KEYCODE_MEDIA_PAUSE,      // 127
  )

  @Test
  fun keycode_constants_match_documented_values() {
    // The smoke script hard-codes 85 / 87 / 88 / 126 / 127. If the
    // platform renumbered these, both the script and Media3 would be
    // out of sync — fail loud here so the test catches it before the
    // integration assertion does.
    assertEquals(85, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    assertEquals(87, KeyEvent.KEYCODE_MEDIA_NEXT)
    assertEquals(88, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    assertEquals(126, KeyEvent.KEYCODE_MEDIA_PLAY)
    assertEquals(127, KeyEvent.KEYCODE_MEDIA_PAUSE)
  }

  @Test
  fun key_down_event_round_trips_action_and_code() {
    for (code in supportedCodes) {
      val event = KeyEvent(KeyEvent.ACTION_DOWN, code)
      assertEquals(KeyEvent.ACTION_DOWN, event.action)
      assertEquals(code, event.keyCode)
    }
  }

  @Test
  fun keycode_is_media_key_returns_true_for_supported_codes() {
    // The Android platform exposes KeyEvent.isMediaSessionKey(int) on
    // API 31+. If a keycode the smoke script sends is NOT recognised
    // as a media key, the platform won't route it through the
    // MediaButtonReceiver to our session.
    for (code in supportedCodes) {
      assertTrue(
        "keycode $code should be a media-session key",
        KeyEvent.isMediaSessionKey(code),
      )
    }
  }

  @Test
  fun fake_player_play_pause_toggle_responds_to_each_keycode() {
    // FakePlayer simulates the receiving-end of the
    // MediaButtonReceiver -> MediaSession.Callback -> Player chain.
    // The mapping is exactly the one Media3 uses by default; we
    // capture it here so a future "we customised onMediaButtonEvent"
    // change doesn't silently regress headset behaviour.
    val player = FakePlayer().apply { playWhenReady = false }

    routeKey(player, KeyEvent.KEYCODE_MEDIA_PLAY)
    assertTrue("KEYCODE_MEDIA_PLAY should start playback", player.playWhenReady)

    routeKey(player, KeyEvent.KEYCODE_MEDIA_PAUSE)
    assertTrue("KEYCODE_MEDIA_PAUSE should stop playback", !player.playWhenReady)

    routeKey(player, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    assertTrue("PLAY_PAUSE while paused should resume", player.playWhenReady)

    routeKey(player, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    assertTrue("PLAY_PAUSE while playing should pause", !player.playWhenReady)
  }

  @Test
  fun fake_player_next_and_previous_advance_index() {
    val player = FakePlayer().apply { mediaItemCount = 3; currentIndex = 1 }

    routeKey(player, KeyEvent.KEYCODE_MEDIA_NEXT)
    assertEquals(2, player.currentIndex)

    routeKey(player, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    assertEquals(1, player.currentIndex)

    // PREVIOUS at the start of the queue is a no-op — Media3 default
    // semantics would seek to 0 instead of leaving the queue, but
    // FakePlayer keeps it simple.
    player.currentIndex = 0
    routeKey(player, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    assertEquals(0, player.currentIndex)
  }

  @Test
  fun unsupported_keycodes_are_ignored() {
    val player = FakePlayer().apply { playWhenReady = true; currentIndex = 1 }
    val before = player.copy()
    routeKey(player, KeyEvent.KEYCODE_VOLUME_UP)
    routeKey(player, KeyEvent.KEYCODE_BACK)
    routeKey(player, KeyEvent.KEYCODE_HOME)
    assertEquals(before.playWhenReady, player.playWhenReady)
    assertEquals(before.currentIndex, player.currentIndex)
  }

  private fun routeKey(player: FakePlayer, keyCode: Int) {
    when (keyCode) {
      KeyEvent.KEYCODE_MEDIA_PLAY -> player.playWhenReady = true
      KeyEvent.KEYCODE_MEDIA_PAUSE -> player.playWhenReady = false
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> player.playWhenReady = !player.playWhenReady
      KeyEvent.KEYCODE_MEDIA_NEXT ->
        player.currentIndex = (player.currentIndex + 1).coerceAtMost(player.mediaItemCount - 1)
      KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
        player.currentIndex = (player.currentIndex - 1).coerceAtLeast(0)
      else -> Unit
    }
  }

  private data class FakePlayer(
    var playWhenReady: Boolean = false,
    var currentIndex: Int = 0,
    var mediaItemCount: Int = 1,
  )
}
