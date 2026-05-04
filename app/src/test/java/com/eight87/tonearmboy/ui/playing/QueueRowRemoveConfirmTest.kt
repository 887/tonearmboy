package com.eight87.tonearmboy.ui.playing

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.tonearmboy.playback.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.27.9.3 — gate the X "remove from queue" action behind an M3
 * AlertDialog so accidental taps don't silently drop a track.
 *
 *  - Tapping the leading `queue_remove` X opens an AlertDialog with
 *    `queue_remove_confirm_dialog` test tag and the row's title in the
 *    body text.
 *  - Tapping Cancel dismisses the dialog without firing `onRemove`.
 *  - Tapping Remove dismisses the dialog AND fires `onRemove` exactly
 *    once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class QueueRowRemoveConfirmTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun renderRow(
    title: String = "Adrenaline",
    onRemove: () -> Unit = {},
  ) {
    composeRule.setContent {
      MaterialTheme {
        QueueRow(
          item = QueueItem(mediaId = "1", title = title, artist = "Tester"),
          isActive = false,
          dragHandleModifier = Modifier,
          dragHandleEnabled = true,
          onJumpTo = {},
          onRemove = onRemove,
        )
      }
    }
  }

  @Test
  fun tapping_x_opens_dialog_with_row_title_in_body() {
    renderRow(title = "Adrenaline")
    composeRule.onNodeWithTag("queue_remove", useUnmergedTree = true).performClick()
    composeRule.waitForIdle()

    composeRule.onAllNodesWithTag("queue_remove_confirm_dialog", useUnmergedTree = true)
      .assertCountEquals(1)
    // The body string contains "'Adrenaline'" (single-quoted). Match
    // by substring so the assertion is resilient to whitespace
    // differences in the rendered Text node.
    composeRule.onAllNodesWithText("'Adrenaline'", substring = true, useUnmergedTree = true)
      .assertCountEquals(1)
  }

  @Test
  fun cancel_dismisses_without_invoking_onRemove() {
    var removeCount = 0
    renderRow(title = "Adrenaline", onRemove = { removeCount++ })

    composeRule.onNodeWithTag("queue_remove", useUnmergedTree = true).performClick()
    composeRule.waitForIdle()
    composeRule.onAllNodesWithTag("queue_remove_confirm_dialog", useUnmergedTree = true)
      .assertCountEquals(1)

    composeRule.onNodeWithTag("queue_remove_cancel_button", useUnmergedTree = true).performClick()
    composeRule.waitForIdle()

    composeRule.onAllNodesWithTag("queue_remove_confirm_dialog", useUnmergedTree = true)
      .assertCountEquals(0)
    assertEquals(0, removeCount)
  }

  @Test
  fun confirm_dismisses_and_invokes_onRemove_once() {
    var removeCount = 0
    renderRow(title = "Adrenaline", onRemove = { removeCount++ })

    composeRule.onNodeWithTag("queue_remove", useUnmergedTree = true).performClick()
    composeRule.waitForIdle()
    composeRule.onAllNodesWithTag("queue_remove_confirm_dialog", useUnmergedTree = true)
      .assertCountEquals(1)

    composeRule.onNodeWithTag("queue_remove_confirm_button", useUnmergedTree = true).performClick()
    composeRule.waitForIdle()

    composeRule.onAllNodesWithTag("queue_remove_confirm_dialog", useUnmergedTree = true)
      .assertCountEquals(0)
    assertEquals(1, removeCount)
  }
}
