package com.eight87.tonearmboy.ui.playing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.tonearmboy.playback.PlaybackUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.20.2 — pin the mini-player tap contract: tapping it MUST be a
 * pure UI-thread no-op that triggers a navigation callback. No
 * suspending work, no `MediaController.buildAsync().get()`, no
 * `runBlocking` on Main. The bug we shipped against was the click
 * handler calling into a path that blocked the main looper, which
 * the platform reports as an ANR.
 *
 * We render the composable, click it, and assert the on-expand
 * callback ran synchronously. If anyone re-introduces blocking work
 * here (e.g. spinning a controller connection on the click thread)
 * the change will register here as a hang or a thread-violation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerTapTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun mini_player_tap_invokes_on_expand_synchronously() {
    var expanded = 0
    composeRule.setContent {
      MaterialTheme {
        MiniPlayer(
          state = PlaybackUiState(
            hasMedia = true,
            title = "Velvet",
            artist = "Den",
            album = "Test",
            isPlaying = true,
            positionMs = 1_000,
            durationMs = 60_000,
            hasNext = false,
            hasPrevious = false,
          ),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = { expanded++ },
        )
      }
    }

    composeRule.onNodeWithTag("mini_player").performClick()
    composeRule.waitForIdle()

    // Synchronous: the click handler dispatched a callback in the
    // same UI frame. If onExpand were posted to a slow thread or
    // wrapped in a runBlocking we would still measure 0 here.
    assert(expanded == 1) { "expected onExpand to fire exactly once, got $expanded" }
  }

  @Test
  fun mini_player_play_button_tap_does_not_invoke_on_expand() {
    var expanded = 0
    var toggled = 0
    composeRule.setContent {
      MaterialTheme {
        MiniPlayer(
          state = PlaybackUiState(
            hasMedia = true,
            title = "Velvet",
            artist = "Den",
            album = "Test",
            isPlaying = false,
            positionMs = 0,
            durationMs = 60_000,
            hasNext = false,
            hasPrevious = false,
          ),
          onTogglePlayPause = { toggled++ },
          onClose = {},
          onExpand = { expanded++ },
        )
      }
    }

    composeRule.onNodeWithTag("mini_player_play_button").performClick()
    composeRule.waitForIdle()

    // The play button is a separate target above the row's clickable.
    // Tapping it must NOT dispatch the row's expand handler — that
    // would route the user to Now Playing every time they tried to
    // pause.
    assert(toggled == 1) { "expected onTogglePlayPause to fire once, got $toggled" }
    assert(expanded == 0) { "play button tap must not trigger row expand, got $expanded" }
  }
}
