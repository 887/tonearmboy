package com.eight87.tonearmboy.ui.playing

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
// assertExists / assertDoesNotExist are member functions on
// SemanticsNodeInteraction in androidx.compose.ui.test, no import needed.
import com.eight87.tonearmboy.playback.PlaybackUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.13.1 Visibility states.
 *
 * The mini-player is a thin Compose surface that renders only when the
 * controller is producing a [PlaybackUiState] with `hasMedia = true`.
 * The `MediaController` itself is not under test here — its
 * `currentMediaItem == null` situation is mirrored by [PlaybackUiState.Empty]
 * which `pushState()` already emits when `mediaItemCount == 0`. The
 * controller-level wiring is exercised by the integration assertions in
 * `scripts/ui-smoke-test.sh`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerVisibilityTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun renders_nothing_when_state_has_no_media() {
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = PlaybackUiState.Empty,
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player").assertDoesNotExist()
    composeRule.onNodeWithTag("mini_player_play_button").assertDoesNotExist()
  }

  @Test
  fun renders_with_title_artist_play_pause_and_cover_when_playing() {
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = state(isPlaying = true),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
    composeRule.onNodeWithText("Cipher Light").assertIsDisplayed()
    // D.21.1: subtitle is now "artist · album", not artist-only.
    composeRule.onNodeWithText("The Synth Foxes · Velvet Den").assertIsDisplayed()
    composeRule.onNodeWithTag("mini_player_play_button").assertExists()
    // The transport icon's contentDescription flips with `isPlaying`.
    composeRule.onNodeWithContentDescription("Pause").assertExists()
    composeRule.onNodeWithContentDescription("Stop").assertExists()
  }

  @Test
  fun renders_same_fields_when_paused_only_transport_icon_flips() {
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = state(isPlaying = false),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
    composeRule.onNodeWithText("Cipher Light").assertIsDisplayed()
    composeRule.onNodeWithText("The Synth Foxes · Velvet Den").assertIsDisplayed()
    // Paused: the icon is `PlayArrow` with contentDescription "Play".
    composeRule.onNodeWithContentDescription("Play").assertExists()
    composeRule.onNodeWithContentDescription("Pause").assertDoesNotExist()
  }

  @Test
  fun renders_unknown_fallbacks_when_metadata_blank() {
    val blank = PlaybackUiState(
      hasMedia = true,
      title = "",
      artist = "",
      album = "",
      isPlaying = true,
      positionMs = 0,
      durationMs = 0,
      hasNext = false,
      hasPrevious = false,
    )
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = blank,
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
    composeRule.onNodeWithText("Unknown").assertIsDisplayed()
    composeRule.onNodeWithText("Unknown artist").assertIsDisplayed()
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
