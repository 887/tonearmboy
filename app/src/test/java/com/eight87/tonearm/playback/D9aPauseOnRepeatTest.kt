package com.eight87.tonearm.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.9a.3 — verify the pause-on-repeat decision boundary: when the user
 * has the toggle on and Media3 fires a media-item transition with reason
 * `MEDIA_ITEM_TRANSITION_REASON_REPEAT`, we (a) seek to position 0 and
 * (b) flip `playWhenReady` off. Other transition reasons are ignored,
 * and the toggle being off short-circuits the entire path.
 *
 * The test runs the small decision helper [shouldPauseOnRepeatBoundary]
 * pulled out of [PlaybackUiController]'s listener so we can exercise
 * the matrix without instantiating a full ExoPlayer / MediaController.
 */
class D9aPauseOnRepeatTest {

  @Test
  fun pauses_when_toggle_on_and_reason_is_repeat() {
    assertTrue(
      shouldPauseOnRepeatBoundary(
        reason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        pauseOnRepeat = true,
      ),
    )
  }

  @Test
  fun does_not_pause_when_toggle_off() {
    assertFalse(
      shouldPauseOnRepeatBoundary(
        reason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        pauseOnRepeat = false,
      ),
    )
  }

  @Test
  fun does_not_pause_for_auto_advance_or_seek() {
    // AUTO advance (next track in queue), SEEK (user scrubbed), and
    // PLAYLIST_CHANGED (queue replacement) must NOT trigger the pause.
    val ignored = listOf(
      Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
      Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
      Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
    )
    for (reason in ignored) {
      assertFalse(
        "should not pause on reason=$reason",
        shouldPauseOnRepeatBoundary(reason = reason, pauseOnRepeat = true),
      )
    }
  }

  @Test
  fun fakeplayer_simulates_pause_on_repeat_boundary() {
    val player = FakePlayer()
    player.playWhenReady = true
    player.position = 90_000L

    // Simulate a REPEAT-mode-one loop firing the transition.
    if (shouldPauseOnRepeatBoundary(
        reason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        pauseOnRepeat = true,
      )
    ) {
      player.seekTo(0)
      player.playWhenReady = false
    }
    assertEquals(0L, player.position)
    assertFalse(player.playWhenReady)
  }

  private class FakePlayer {
    var playWhenReady: Boolean = false
    var position: Long = 0L
    fun seekTo(p: Long) { position = p }
  }
}
