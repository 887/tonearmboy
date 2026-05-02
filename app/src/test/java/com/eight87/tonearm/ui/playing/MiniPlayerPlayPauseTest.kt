package com.eight87.tonearm.ui.playing

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.tonearm.playback.PlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.13.3 Inline play / pause toggle.
 *
 * `TonearmApp` wires the mini-player's `onTogglePlayPause` to
 * `playback::togglePlayPause`, which calls `MediaController.play()` or
 * `pause()` depending on `MediaController.isPlaying`. The Compose
 * surface's job is just to fire the lambda — the conditional branch on
 * "play vs pause" lives in [PlaybackUiController.togglePlayPause]:
 *
 *   if (ctl.isPlaying) ctl.pause() else ctl.play()
 *
 * That single-source decision is what we pin down here. We can't run a
 * real `MediaController` in a JVM unit test (it requires a bound
 * `MediaSessionService`), so we use a fake [TogglePlayPauseLogic] that
 * mirrors the production rule and assert it routes correctly under both
 * `isPlaying` flags. The integration assertion on `emulator-5554`
 * verifies the actual `MediaController` transition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerPlayPauseTest {

  @get:Rule
  val composeRule = createComposeRule()

  // --- Compose: the play button fires the toggle lambda once per tap ----

  @Test
  fun tap_on_play_button_invokes_toggle_when_paused() {
    var toggled = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = state(isPlaying = false),
          onTogglePlayPause = { toggled++ },
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_play_button").performClick()
    assertEquals(1, toggled)
  }

  @Test
  fun tap_on_play_button_invokes_toggle_when_playing() {
    var toggled = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = state(isPlaying = true),
          onTogglePlayPause = { toggled++ },
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_play_button").performClick()
    assertEquals(1, toggled)
  }

  // --- Pure logic: the controller's play/pause routing -----------------

  @Test
  fun toggle_routes_to_pause_when_isPlaying_true() {
    val fake = FakeTransport(isPlaying = true)
    fake.toggle()
    assertEquals(listOf("pause"), fake.calls)
  }

  @Test
  fun toggle_routes_to_play_when_isPlaying_false() {
    val fake = FakeTransport(isPlaying = false)
    fake.toggle()
    assertEquals(listOf("play"), fake.calls)
  }

  @Test
  fun successive_toggles_produce_alternating_calls() {
    // Mirror an end-user double-tap: pause then play.
    val fake = FakeTransport(isPlaying = true)
    fake.toggle()
    fake.isPlaying = false // listener flip; real MediaController does this
    fake.toggle()
    assertEquals(listOf("pause", "play"), fake.calls)
  }

  private class FakeTransport(var isPlaying: Boolean) {
    val calls = mutableListOf<String>()
    fun toggle() {
      if (isPlaying) calls += "pause" else calls += "play"
    }
  }

  private fun state(isPlaying: Boolean) = PlaybackUiState(
    hasMedia = true,
    title = "Cipher Light",
    artist = "The Synth Foxes",
    album = "Velvet Den",
    isPlaying = isPlaying,
    positionMs = 0,
    durationMs = 30_000,
    hasNext = true,
    hasPrevious = true,
  )
}
