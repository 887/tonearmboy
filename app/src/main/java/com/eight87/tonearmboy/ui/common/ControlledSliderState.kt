package com.eight87.tonearmboy.ui.common

import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * A [SliderState] driven by a caller-owned [value] — the "controlled"
 * shape the value-based `Slider(value = …)` overload had before
 * material3 1.5 deprecated it in favour of `Slider(state = …)`.
 *
 * The state is re-created when [steps] or [valueRange] change (a
 * scrubber's range tracks the playing item's duration), and its value is
 * re-synced to [value] on every recomposition so external updates —
 * playback position ticks, a "Reset" button — move the thumb.
 * `rememberSliderState` does neither: it is keyed on nothing and only
 * reads its arguments once.
 */
@Composable
fun rememberControlledSliderState(
  value: Float,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
): SliderState {
  val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
  state.value = value
  return state
}

/** [RangeSliderState] counterpart of [rememberControlledSliderState]. */
@Composable
fun rememberControlledRangeSliderState(
  value: ClosedFloatingPointRange<Float>,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
): RangeSliderState {
  val state = remember(steps, valueRange) {
    RangeSliderState(value.start, value.endInclusive, steps, valueRange)
  }
  state.startValue = value.start
  state.endValue = value.endInclusive
  return state
}
