package com.eight87.tonearm.ui.settings

/**
 * R.B.6 — UI-only helpers for the base-theme picker. Lives outside
 * [BaseTheme] (which is a pure data type belonging to the settings
 * data layer) so the radio-dialog vocabulary doesn't leak into the
 * value type's API.
 */

/**
 * The four base-theme variants surfaced by the picker dialog. Used
 * in place of `BaseTheme.entries` (a sealed class can't enumerate
 * its leaves automatically). The [BaseTheme.Custom] entry here is a
 * sentinel — the dialog displays it, and tapping opens the colour
 * picker; the actual stored value carries the user-picked seed.
 */
val baseThemePickerOptions: List<BaseTheme> = listOf(
  BaseTheme.DefaultAndroid,
  BaseTheme.DefaultColors,
  BaseTheme.PureBlack,
  BaseTheme.Custom(seedRgb = 0xFF6750A4L and 0xFFFFFFL), // Material 3 default purple
)

/**
 * Project a stored [BaseTheme] onto the picker's display set so the
 * radio dialog can highlight the right option. The picker only
 * surfaces a single `Custom` sentinel (with a placeholder seed); the
 * stored `Custom(...)` value (whatever the seed) maps to that
 * sentinel.
 */
fun baseThemeMatch(stored: BaseTheme): BaseTheme = when (stored) {
  is BaseTheme.DefaultAndroid -> BaseTheme.DefaultAndroid
  is BaseTheme.DefaultColors -> BaseTheme.DefaultColors
  is BaseTheme.PureBlack -> BaseTheme.PureBlack
  is BaseTheme.Custom -> baseThemePickerOptions.last()
}
