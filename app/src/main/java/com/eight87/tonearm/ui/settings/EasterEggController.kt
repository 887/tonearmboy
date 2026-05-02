package com.eight87.tonearm.ui.settings

/**
 * D.16.5 — pure-Kotlin state machine for the build-version easter egg.
 *
 * The user taps the build-version row in About; the first two taps surface
 * a snackbar prompt, the third reveals a fullscreen fox image. Taps that
 * arrive more than [windowMillis] after the previous tap reset the
 * counter — the user has to start over.
 *
 * The class is deliberately stateful but framework-free: the test in
 * `EasterEggControllerTest` drives it with a synthetic clock so the
 * 5-second window is exercised without `Thread.sleep`. The owning
 * Compose layer asks for the wall-clock time when calling [tap], which
 * keeps the controller plain Kotlin and Robolectric-friendly.
 */
class EasterEggController(
  private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
  /**
   * What the UI should do after a tap. Distinguishes "show the first
   * snackbar prompt" from "show the second prompt" from "reveal the
   * fox" so the call site can wire each path without re-deriving state.
   */
  sealed class Outcome {
    /** First tap landed; UI should show "Click 2 more times for a treat". */
    data object FirstPromptSnackbar : Outcome()
    /** Second tap landed within the window; UI should show "1 more time". */
    data object SecondPromptSnackbar : Outcome()
    /** Third tap landed within the window; UI should reveal the fox dialog. */
    data object Reveal : Outcome()
  }

  private var counter: Int = 0
  private var lastTapAtMillis: Long = 0L

  /**
   * Record a tap at [nowMillis] (caller-supplied wall clock).
   *
   * If the time since the previous tap exceeds [windowMillis], the
   * counter resets before this tap is counted. The reveal path
   * (counter == 3) also resets the counter so the easter egg is
   * repeatable without leaving the screen.
   */
  fun tap(nowMillis: Long): Outcome {
    if (counter > 0 && (nowMillis - lastTapAtMillis) > windowMillis) {
      counter = 0
    }
    counter += 1
    lastTapAtMillis = nowMillis
    return when (counter) {
      1 -> Outcome.FirstPromptSnackbar
      2 -> Outcome.SecondPromptSnackbar
      else -> {
        // D.16.5.4 — reset so the user can do it again.
        counter = 0
        Outcome.Reveal
      }
    }
  }

  /** Test-only inspector for the current count. */
  internal fun debugCount(): Int = counter

  companion object {
    /** D.16.5.1 — five-second tap window per the spec. */
    const val DEFAULT_WINDOW_MILLIS: Long = 5_000L
  }
}
