package com.eight87.tonearm.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Three-mode app theme (System / Light / Dark) backed by Material 3 with
 * dynamic color (Material You) on Android 12+.
 *
 * - On API 31+ with `dynamicColor = true` (the default), the wallpaper-
 *   derived dynamic palette is used.
 * - Otherwise falls back to the app's brand palette.
 *
 * The default is **dark** (per the locked spec) — `darkTheme = true` is
 * only resolved from `isSystemInDarkTheme()` when [TonearmTheme] is
 * called without an explicit value. Caller (the activity) usually drives
 * `darkTheme` from the persisted [com.eight87.tonearm.ui.settings.ThemePreference].
 */
private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

@Composable
fun TonearmTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
