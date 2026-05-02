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
 * D.13.2 Tap-to-expand.
 *
 * `TonearmApp` wires `onExpand` to `backStack.push(NowPlaying)`. Here we
 * assert the mini-player invokes its `onExpand` lambda exactly once when
 * its body row (the `mini_player` testTag) is tapped — that's the
 * navigation contract from the perspective of the composable. The
 * back-stack wiring itself is exercised by the integration assertion in
 * `scripts/ui-smoke-test.sh`.
 *
 * Compose semantics quirk worth noting: the play-pause inner box is its
 * own clickable, so tapping the row at the play-button position invokes
 * `onTogglePlayPause` instead of `onExpand`. The body row's testTag
 * targets the outer `Row` so the click lands on its `clickable` modifier
 * (the inner clickable consumes events only inside its own bounds).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerNavigationTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun tapping_mini_player_body_invokes_onExpand_navigation() {
    var expanded = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = { expanded++ },
        )
      }
    }
    composeRule.onNodeWithTag("mini_player").performClick()
    assertEquals(1, expanded)
  }

  @Test
  fun tapping_play_button_does_not_trigger_navigation() {
    // The play button is its own clickable surface — tapping it must
    // toggle playback, not open NowPlaying.
    var expanded = 0
    var toggled = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = { toggled++ },
          onClose = {},
          onExpand = { expanded++ },
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_play_button").performClick()
    assertEquals(0, expanded)
    assertEquals(1, toggled)
  }

  private fun playing() = PlaybackUiState(
    hasMedia = true,
    title = "Cipher Light",
    artist = "The Synth Foxes",
    album = "Velvet Den",
    isPlaying = true,
    positionMs = 0,
    durationMs = 30_000,
    hasNext = true,
    hasPrevious = true,
  )
}
