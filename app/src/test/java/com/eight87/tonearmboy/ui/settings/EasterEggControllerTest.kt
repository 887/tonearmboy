package com.eight87.tonearmboy.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.16.5 — pure-Kotlin tests for [EasterEggController]. The 5-second
 * window is exercised with a synthetic clock so the test stays
 * Robolectric-free and runs in milliseconds.
 */
class EasterEggControllerTest {

  @Test
  fun first_tap_returns_first_prompt() {
    val controller = EasterEggController()
    val outcome = controller.tap(0L)
    assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, outcome)
  }

  @Test
  fun second_tap_within_window_returns_second_prompt() {
    val controller = EasterEggController()
    controller.tap(0L)
    val outcome = controller.tap(1_000L)
    assertEquals(EasterEggController.Outcome.SecondPromptSnackbar, outcome)
  }

  @Test
  fun third_tap_within_window_returns_reveal() {
    val controller = EasterEggController()
    controller.tap(0L)
    controller.tap(1_000L)
    val outcome = controller.tap(2_000L)
    assertEquals(EasterEggController.Outcome.Reveal, outcome)
  }

  @Test
  fun reveal_resets_counter_so_egg_is_repeatable() {
    val controller = EasterEggController()
    controller.tap(0L)
    controller.tap(1_000L)
    controller.tap(2_000L)
    // After reveal, the next tap should be the first prompt again.
    val outcome = controller.tap(3_000L)
    assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, outcome)
  }

  @Test
  fun tap_after_window_lapse_resets_counter() {
    val controller = EasterEggController()
    controller.tap(0L)
    // Six seconds later, > 5s window — the counter resets and we get the
    // first-prompt outcome rather than the second.
    val outcome = controller.tap(6_000L)
    assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, outcome)
  }

  @Test
  fun tap_exactly_at_window_boundary_still_counts_continuation() {
    val controller = EasterEggController()
    controller.tap(0L)
    // 5_000 ms later: at the exact window limit (delta is not > limit),
    // so the counter should not reset.
    val outcome = controller.tap(5_000L)
    assertEquals(EasterEggController.Outcome.SecondPromptSnackbar, outcome)
  }

  @Test
  fun mixed_pattern_lapse_then_quick_three() {
    val controller = EasterEggController()
    controller.tap(0L)
    // Lapse: counter resets on next tap.
    controller.tap(7_000L) // first prompt again
    controller.tap(8_000L) // second prompt
    val outcome = controller.tap(9_000L) // reveal
    assertEquals(EasterEggController.Outcome.Reveal, outcome)
  }

  @Test
  fun custom_window_is_respected() {
    val controller = EasterEggController(windowMillis = 100L)
    controller.tap(0L)
    val outcome = controller.tap(200L)
    // 200 - 0 > 100 → reset. First tap of a fresh sequence again.
    assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, outcome)
  }
}
