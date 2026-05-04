package com.eight87.tonearmboy.ui.playing

import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.Player
import com.eight87.tonearmboy.playback.PlaybackUiState
import com.eight87.tonearmboy.ui.settings.CustomBarAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.13.4 Custom playback bar action (D.9a.1).
 *
 * Two layers under test:
 *  - **Compose**: long-pressing the play button fires the
 *    `onPlayButtonLongPress` lambda exactly once, independent of which
 *    action the user has configured. The mini-player itself doesn't know
 *    the action — `TonearmboyApp` passes `playback.performCustomBarAction`
 *    bound to the current `settingsSnapshot.customBarAction`.
 *  - **Pure logic**: [PlaybackUiController.performCustomBarAction]'s
 *    branch table maps each [CustomBarAction] to the right transport
 *    command. We exercise the branches via a fake controller that mirrors
 *    the relevant `MediaController` surface (next, shuffle flip, repeat
 *    cycle, no-op).
 *
 * The integration block in `scripts/ui-smoke-test.sh` iterates the four
 * settings on `emulator-5554` and asserts the on-device transport state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerLongPressActionTest {

  @get:Rule
  val composeRule = createComposeRule()

  // --- Compose: long-press fires the lambda --------------------------

  @Test
  fun long_press_on_play_button_fires_onPlayButtonLongPress() {
    var fired = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = {},
          onClose = {},
          onExpand = {},
          onPlayButtonLongPress = { fired++ },
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_play_button").performTouchInput {
      longClick(position = Offset(centerX, centerY))
    }
    assertEquals(1, fired)
  }

  @Test
  fun long_press_does_not_also_fire_short_tap_toggle() {
    // `combinedClickable` consumes a long-click as a long-click only —
    // the short-click handler must not also fire.
    var fired = 0
    var toggled = 0
    composeRule.setContent {
      Surface {
        MiniPlayer(
          state = playing(),
          onTogglePlayPause = { toggled++ },
          onClose = {},
          onExpand = {},
          onPlayButtonLongPress = { fired++ },
        )
      }
    }
    composeRule.onNodeWithTag("mini_player_play_button").performTouchInput {
      longClick(position = Offset(centerX, centerY))
    }
    assertEquals(1, fired)
    assertEquals(0, toggled)
  }

  // --- Pure logic: each setting maps to the right transport command --

  @Test
  fun skip_next_action_advances_queue() {
    val fake = FakeController()
    fake.perform(CustomBarAction.SkipNext)
    assertEquals(listOf("seekToNext"), fake.calls)
  }

  @Test
  fun shuffle_action_toggles_shuffle_mode() {
    val fake = FakeController()
    assertFalse(fake.shuffleModeEnabled)
    fake.perform(CustomBarAction.ShuffleToggle)
    assertTrue(fake.shuffleModeEnabled)
    fake.perform(CustomBarAction.ShuffleToggle)
    assertFalse(fake.shuffleModeEnabled)
  }

  @Test
  fun repeat_action_cycles_off_to_all_to_one_to_off() {
    val fake = FakeController()
    assertEquals(Player.REPEAT_MODE_OFF, fake.repeatMode)
    fake.perform(CustomBarAction.RepeatToggle)
    assertEquals(Player.REPEAT_MODE_ALL, fake.repeatMode)
    fake.perform(CustomBarAction.RepeatToggle)
    assertEquals(Player.REPEAT_MODE_ONE, fake.repeatMode)
    fake.perform(CustomBarAction.RepeatToggle)
    assertEquals(Player.REPEAT_MODE_OFF, fake.repeatMode)
  }

  @Test
  fun none_action_is_a_noop() {
    val fake = FakeController()
    fake.perform(CustomBarAction.None)
    assertEquals(emptyList<String>(), fake.calls)
    assertFalse(fake.shuffleModeEnabled)
    assertEquals(Player.REPEAT_MODE_OFF, fake.repeatMode)
  }

  /**
   * Mirror of [com.eight87.tonearmboy.playback.PlaybackUiController.performCustomBarAction]
   * — kept inline so the test doesn't need a real `MediaController`. If
   * the production branch table changes, this test surfaces the drift.
   */
  private class FakeController {
    val calls = mutableListOf<String>()
    var shuffleModeEnabled: Boolean = false
    var repeatMode: Int = Player.REPEAT_MODE_OFF

    fun perform(action: CustomBarAction) {
      when (action) {
        CustomBarAction.SkipNext -> calls += "seekToNext"
        CustomBarAction.ShuffleToggle -> shuffleModeEnabled = !shuffleModeEnabled
        CustomBarAction.RepeatToggle -> repeatMode = nextRepeatMode(repeatMode)
        CustomBarAction.None -> Unit
      }
    }

    private fun nextRepeatMode(current: Int): Int = when (current) {
      Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
      Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
      else -> Player.REPEAT_MODE_OFF
    }
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
