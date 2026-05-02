package com.eight87.tonearm.ui.playing

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * D.24.1 / D.24.6 — pin the mini-player two-row transport surface:
 *  - prev / play-pause / next icon buttons are present in the
 *    transport row
 *  - tapping each invokes the matching lambda (`onSkipPrevious`,
 *    `onTogglePlayPause`, `onSkipNext`) exactly once
 *  - tapping the info row (`mini_player`) opens NowPlaying via
 *    `onExpand`; tapping the transport buttons does not also expand
 *  - prev / next are gated on `state.hasPrevious` / `state.hasNext`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerTransportTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun playing(
    hasNext: Boolean = true,
    hasPrevious: Boolean = true,
    isPlaying: Boolean = true,
  ) = PlaybackUiState(
    hasMedia = true,
    title = "Cipher Light",
    artist = "The Synth Foxes",
    album = "Velvet Den",
    isPlaying = isPlaying,
    positionMs = 1_000,
    durationMs = 60_000,
    hasNext = hasNext,
    hasPrevious = hasPrevious,
  )

  @Test
  fun transport_row_renders_prev_play_pause_next() {
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
          onSkipNext = {},
          onSkipPrevious = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_transport_row", useUnmergedTree = true)
      .assertIsDisplayed()
    composeRule.onNodeWithTag("mini_player_prev", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("mini_player_play_button", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithTag("mini_player_next", useUnmergedTree = true).assertExists()
    composeRule.onNodeWithContentDescription("Previous").assertExists()
    composeRule.onNodeWithContentDescription("Next").assertExists()
  }

  @Test
  fun tapping_prev_invokes_skip_previous() {
    var prev = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
          onSkipNext = {},
          onSkipPrevious = { prev++ },
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_prev").performClick()
    assertEquals(1, prev)
  }

  @Test
  fun tapping_next_invokes_skip_next() {
    var next = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
          onSkipNext = { next++ },
          onSkipPrevious = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_next").performClick()
    assertEquals(1, next)
  }

  @Test
  fun tapping_info_row_invokes_on_expand_not_transport() {
    var expanded = 0
    var prev = 0
    var next = 0
    var toggled = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = { toggled++ },
          onClose = {},
          onExpand = { expanded++ },
          onSkipNext = { next++ },
          onSkipPrevious = { prev++ },
        )
      }
    }
    composeRule.onNodeWithTag("mini_player").performClick()
    assertEquals(1, expanded)
    assertEquals(0, prev)
    assertEquals(0, next)
    assertEquals(0, toggled)
  }

  @Test
  fun tapping_transport_buttons_does_not_invoke_on_expand() {
    var expanded = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = { expanded++ },
          onSkipNext = {},
          onSkipPrevious = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_prev").performClick()
    composeRule.onNodeWithTag("mini_player_next").performClick()
    composeRule.onNodeWithTag("mini_player_play_button").performClick()
    assertEquals(0, expanded)
  }

  @Test
  fun prev_disabled_when_state_has_no_previous() {
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(hasPrevious = false),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
          onSkipNext = {},
          onSkipPrevious = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_prev").assertIsNotEnabled()
    composeRule.onNodeWithTag("mini_player_next").assertIsEnabled()
  }

  @Test
  fun next_disabled_when_state_has_no_next() {
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(hasNext = false),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
          onSkipNext = {},
          onSkipPrevious = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_next").assertIsNotEnabled()
    composeRule.onNodeWithTag("mini_player_prev").assertIsEnabled()
  }
}
