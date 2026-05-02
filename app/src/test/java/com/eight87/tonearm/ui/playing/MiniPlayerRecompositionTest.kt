package com.eight87.tonearm.ui.playing

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.tonearm.playback.PlaybackUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.13.5 Title / artist / cover updates on track change.
 *
 * `PlaybackUiController.pushState()` derives a fresh [PlaybackUiState]
 * from `MediaController.currentMediaItem.mediaMetadata` whenever the
 * controller's `onMediaItemTransition` listener fires. The mini-player
 * is parameterised on that state, so as long as Compose recomposes the
 * row when the state changes, the on-screen fields update.
 *
 * Here we drive a `mutableStateOf<PlaybackUiState>` directly and flip it
 * — that's a tighter analogue of the real flow than spinning ExoPlayer.
 * Recomposition timing on a real device is the integration assertion's
 * job.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerRecompositionTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun mini_player_recomposes_when_state_changes_track() {
    var state by mutableStateOf(track1())
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = state,
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithText("Cipher Light").assertIsDisplayed()
    composeRule.onNodeWithText("The Synth Foxes").assertIsDisplayed()

    // Advance to the next track — same shape as MediaController firing
    // onMediaItemTransition and pushState() emitting fresh metadata.
    composeRule.runOnIdle { state = track2() }
    composeRule.waitForIdle()

    composeRule.onNodeWithText("Quiet Hours").assertIsDisplayed()
    composeRule.onNodeWithText("Field Recordings Trio").assertIsDisplayed()
  }

  @Test
  fun mini_player_recomposes_when_isPlaying_flips() {
    var state by mutableStateOf(track1(isPlaying = true))
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = state,
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()

    composeRule.runOnIdle { state = track1(isPlaying = false) }
    composeRule.waitForIdle()

    composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
  }

  @Test
  fun mini_player_disappears_when_state_loses_media() {
    var state by mutableStateOf(track1())
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = state,
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
        )
      }
    }
    composeRule.onNodeWithTag("mini_player").assertIsDisplayed()

    // Equivalent to the queue clearing — pushState() emits Empty and the
    // composable returns early.
    composeRule.runOnIdle { state = PlaybackUiState.Empty }
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("mini_player").assertDoesNotExist()
  }

  private fun track1(isPlaying: Boolean = true) = PlaybackUiState(
    hasMedia = true,
    title = "Cipher Light",
    artist = "The Synth Foxes",
    album = "Velvet Den",
    isPlaying = isPlaying,
    positionMs = 0,
    durationMs = 30_000,
    hasNext = true,
    hasPrevious = false,
  )

  private fun track2() = PlaybackUiState(
    hasMedia = true,
    title = "Quiet Hours",
    artist = "Field Recordings Trio",
    album = "Field Recordings",
    isPlaying = true,
    positionMs = 0,
    durationMs = 45_000,
    hasNext = false,
    hasPrevious = true,
  )
}
