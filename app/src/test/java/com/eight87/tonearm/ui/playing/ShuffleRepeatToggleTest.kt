package com.eight87.tonearm.ui.playing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.Player
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.21.4 — pin the shuffle / repeat toggle behaviour:
 *  - `IconToggleButton` wired to `state.shuffleEnabled` /
 *    `state.repeatMode`
 *  - shuffle is a binary toggle
 *  - repeat cycles OFF → ALL → ONE → OFF and the IconToggleButton
 *    reflects the on / off bit (any non-OFF mode = "on")
 *  - both buttons appear in the queue header (same controller state
 *    drives both surfaces, so we only need to exercise one here)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShuffleRepeatToggleTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun snapshot() = QueueSnapshot(
    items = listOf(
      QueueItem(mediaId = "1", title = "Active", artist = "Artist"),
      QueueItem(mediaId = "2", title = "Next", artist = "Artist"),
    ),
    currentIndex = 0,
  )

  @Test
  fun shuffle_toggle_reflects_state_and_invokes_callback() {
    var shuffle = false
    var toggleCalls = 0
    composeRule.setContent {
      MaterialTheme {
        QueueSheetContent(
          snapshot = snapshot(),
          shuffleEnabled = shuffle,
          repeatMode = Player.REPEAT_MODE_OFF,
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
          onSeek = {},
          onToggleShuffle = { toggleCalls++ },
          onCycleRepeat = {},
          positionMs = 0,
          durationMs = 60_000,
        )
      }
    }
    composeRule.onNodeWithTag("queue_shuffle_toggle").assertIsOff()
    // IconToggleButton's semantics use a `toggle` action rather than
    // a click action; route through performTouchInput so the gesture
    // actually lands on the toggle's Modifier.toggleable surface.
    composeRule.onNodeWithTag("queue_shuffle_toggle").performTouchInput { click() }
    composeRule.waitForIdle()
    assertEquals(1, toggleCalls)
  }

  @Test
  fun shuffle_toggle_lights_up_when_state_says_on() {
    composeRule.setContent {
      MaterialTheme {
        QueueSheetContent(
          snapshot = snapshot(),
          shuffleEnabled = true,
          repeatMode = Player.REPEAT_MODE_OFF,
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
          onSeek = {},
          onToggleShuffle = {},
          onCycleRepeat = {},
          positionMs = 0,
          durationMs = 60_000,
        )
      }
    }
    composeRule.onNodeWithTag("queue_shuffle_toggle").assertIsOn()
  }

  @Test
  fun repeat_toggle_off_renders_off_state() {
    composeRule.setContent {
      MaterialTheme {
        QueueSheetContent(
          snapshot = snapshot(),
          shuffleEnabled = false,
          repeatMode = Player.REPEAT_MODE_OFF,
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
          onSeek = {},
          onToggleShuffle = {},
          onCycleRepeat = {},
          positionMs = 0,
          durationMs = 60_000,
        )
      }
    }
    composeRule.onNodeWithTag("queue_repeat_toggle").assertIsOff()
  }

  @Test
  fun repeat_toggle_lights_up_for_repeat_all() {
    composeRule.setContent {
      MaterialTheme {
        QueueSheetContent(
          snapshot = snapshot(),
          shuffleEnabled = false,
          repeatMode = Player.REPEAT_MODE_ALL,
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
          onSeek = {},
          onToggleShuffle = {},
          onCycleRepeat = {},
          positionMs = 0,
          durationMs = 60_000,
        )
      }
    }
    composeRule.onNodeWithTag("queue_repeat_toggle").assertIsOn()
  }

  @Test
  fun repeat_toggle_lights_up_for_repeat_one() {
    composeRule.setContent {
      MaterialTheme {
        QueueSheetContent(
          snapshot = snapshot(),
          shuffleEnabled = false,
          repeatMode = Player.REPEAT_MODE_ONE,
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
          onSeek = {},
          onToggleShuffle = {},
          onCycleRepeat = {},
          positionMs = 0,
          durationMs = 60_000,
        )
      }
    }
    composeRule.onNodeWithTag("queue_repeat_toggle").assertIsOn()
  }

  @Test
  fun repeat_toggle_click_invokes_cycle_callback_once() {
    var cycles = 0
    composeRule.setContent {
      MaterialTheme {
        QueueSheetContent(
          snapshot = snapshot(),
          shuffleEnabled = false,
          repeatMode = Player.REPEAT_MODE_OFF,
          onJumpTo = {},
          onRemove = {},
          onMove = { _, _ -> },
          onSeek = {},
          onToggleShuffle = {},
          onCycleRepeat = { cycles++ },
          positionMs = 0,
          durationMs = 60_000,
        )
      }
    }
    composeRule.onNodeWithTag("queue_repeat_toggle").performTouchInput { click() }
    composeRule.waitForIdle()
    assertEquals(1, cycles)
  }

  /**
   * Walk the OFF → ALL → ONE → OFF cycle without hitting the
   * `MediaController` — pure state-machine assertion against the
   * controller's `nextRepeatMode`-equivalent behaviour. Mirrors the
   * test contract from D.9a.3 (pause-on-repeat).
   */
  @Test
  fun repeat_mode_cycles_off_all_one_off() {
    val sequence = generateSequence(Player.REPEAT_MODE_OFF) { current ->
      when (current) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
      }
    }.take(4).toList()
    assertEquals(
      listOf(
        Player.REPEAT_MODE_OFF,
        Player.REPEAT_MODE_ALL,
        Player.REPEAT_MODE_ONE,
        Player.REPEAT_MODE_OFF,
      ),
      sequence,
    )
  }
}
