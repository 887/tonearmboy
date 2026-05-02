package com.eight87.tonearm.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.25.1 — pin the colour-picker contract:
 *
 *   1. The dialog renders with a preview swatch, an HSV square, and
 *      the hue slider.
 *   2. Tapping Confirm fires the callback with the seed-as-Long.
 *   3. Tapping Cancel fires the dismiss callback and does NOT fire
 *      the confirm callback.
 *   4. The `rgbToHsv` / `colorToRgbLong` round-trip is lossless to
 *      within the byte-quantisation rounding error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColorPickerTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun renders_preview_square_and_slider() {
    composeRule.setContent {
      MaterialTheme {
        Surface {
          ColorPickerDialog(
            initialRgb = 0x6750A4L,
            onConfirm = {},
            onDismiss = {},
          )
        }
      }
    }
    composeRule.onNodeWithTag("color_picker_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("color_picker_preview").assertIsDisplayed()
    composeRule.onNodeWithTag("color_picker_sv_square").assertIsDisplayed()
    composeRule.onNodeWithTag("color_picker_hue_slider").assertIsDisplayed()
  }

  @Test
  fun confirm_fires_callback_with_initial_seed_when_user_does_not_drag() {
    var picked: Long? = null
    composeRule.setContent {
      MaterialTheme {
        Surface {
          ColorPickerDialog(
            initialRgb = 0x6750A4L,
            onConfirm = { picked = it },
            onDismiss = {},
          )
        }
      }
    }
    composeRule.onNodeWithTag("color_picker_confirm").performClick()
    assertNotNull("confirm callback should have fired", picked)
    // Quantisation through HSV and back to RGB can drift by up to 1
    // per channel (half-up rounding); the original purple round-trips
    // exactly because every channel is divisible into 255 cleanly.
    assertEquals(0x6750A4L, picked)
  }

  @Test
  fun cancel_fires_dismiss_not_confirm() {
    var dismissed = false
    var picked: Long? = null
    composeRule.setContent {
      MaterialTheme {
        Surface {
          ColorPickerDialog(
            initialRgb = 0x123456L,
            onConfirm = { picked = it },
            onDismiss = { dismissed = true },
          )
        }
      }
    }
    composeRule.onNodeWithTag("color_picker_cancel").performClick()
    assertEquals(true, dismissed)
    assertEquals(null, picked)
  }

  @Test
  fun rgbToHsv_roundTrips_to_within_one_unit_per_channel() {
    listOf(0x000000L, 0xFFFFFFL, 0xFF0000L, 0x00FF00L, 0x0000FFL, 0x6750A4L, 0xFF8800L).forEach { rgb ->
      val hsv = rgbToHsv(rgb)
      val color = androidx.compose.ui.graphics.Color.hsv(hsv[0], hsv[1], hsv[2])
      val back = colorToRgbLong(color)
      val originalR = ((rgb shr 16) and 0xFFL); val backR = ((back shr 16) and 0xFFL)
      val originalG = ((rgb shr 8) and 0xFFL); val backG = ((back shr 8) and 0xFFL)
      val originalB = (rgb and 0xFFL); val backB = (back and 0xFFL)
      assertEquals("R drift for $rgb", 0L, kotlin.math.abs(originalR - backR))
      assertEquals("G drift for $rgb", 0L, kotlin.math.abs(originalG - backG))
      assertEquals("B drift for $rgb", 0L, kotlin.math.abs(originalB - backB))
    }
  }
}
