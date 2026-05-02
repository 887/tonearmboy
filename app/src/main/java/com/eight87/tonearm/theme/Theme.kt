package com.eight87.tonearm.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.eight87.tonearm.ui.settings.BaseTheme

/**
 * Three-mode app theme backed by Material 3 with dynamic color (Material You)
 * on Android 12+, plus an album-art tint layer on top.
 *
 * The base scheme is determined by [baseTheme]:
 *
 *  - [BaseTheme.DefaultAndroid] — Material You / dynamic colour on
 *    API 31+, falls back to the brand palette on older devices.
 *  - [BaseTheme.DefaultColors] — the static brand palette regardless
 *    of API.
 *  - [BaseTheme.PureBlack] — same primary colours as `DefaultAndroid`
 *    but with `surface` / `background` collapsed to pure black for
 *    AMOLED-friendly displays.
 *
 * When [albumArtTintEnabled] is true and `LocalAlbumPalette` carries a
 * non-null `surfaceTint`, the chrome `surface` / `surfaceVariant` /
 * `background` shift toward that tint, animated via
 * [animateColorAsState] so transitions are smooth. When the tint is
 * null (no track / extraction failed) the chrome stays on the base.
 */
private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

@Composable
fun TonearmTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  baseTheme: BaseTheme = BaseTheme.Default,
  albumArtTintEnabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  val baseScheme = resolveBaseScheme(darkTheme, baseTheme)

  val palette = LocalAlbumPalette.current
  val tint = if (albumArtTintEnabled) palette.surfaceTint else null

  // Animate the surface family to/from the tint smoothly. The duration
  // matches the default Compose `animateColorAsState` tween (300 ms);
  // we make it explicit so the value is testable / tunable.
  val surface by animateColorAsState(
    targetValue = blendSurface(baseScheme.surface, tint),
    animationSpec = tween(durationMillis = 300),
    label = "AlbumPaletteSurface",
  )
  val surfaceVariant by animateColorAsState(
    targetValue = blendSurface(baseScheme.surfaceVariant, tint),
    animationSpec = tween(durationMillis = 300),
    label = "AlbumPaletteSurfaceVariant",
  )
  val background by animateColorAsState(
    targetValue = blendSurface(baseScheme.background, tint),
    animationSpec = tween(durationMillis = 300),
    label = "AlbumPaletteBackground",
  )

  val colorScheme = baseScheme.copy(
    surface = surface,
    surfaceVariant = surfaceVariant,
    background = background,
  )

  CompositionLocalProvider(
    // Re-publish the palette local from the receiving site so child
    // composables can consult it directly when they need to read the
    // tint themselves (e.g. a Preview that wants the same bias).
    LocalAlbumPalette provides palette,
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}

/**
 * Resolve the foundation [ColorScheme] for the active [BaseTheme],
 * before any album-palette tint is applied.
 *
 * Pulled out as a top-level composable so a Robolectric test can
 * exercise the three-way picker logic without instantiating the full
 * theme + palette plumbing.
 */
@Composable
internal fun resolveBaseScheme(darkTheme: Boolean, baseTheme: BaseTheme): ColorScheme {
  val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  return when (baseTheme) {
    BaseTheme.DefaultAndroid -> {
      if (dynamicAvailable) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
      }
    }
    BaseTheme.DefaultColors -> if (darkTheme) DarkColorScheme else LightColorScheme
    BaseTheme.PureBlack -> {
      val foundation = if (dynamicAvailable) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
      }
      foundation.copy(background = Color.Black, surface = Color.Black)
    }
  }
}

/**
 * Blend the base surface colour toward the album-palette [tint]. We
 * keep the bias modest (40 % of the way to the tint) so chrome
 * remains chrome — the user is meant to read it as "tinted by the
 * cover", not "becomes the cover". Returning [base] unchanged when
 * the tint is null preserves the static-theme path bit-for-bit.
 *
 * Pure helper for unit-testability — no Compose runtime required.
 */
internal fun blendSurface(base: Color, tint: Color?, fraction: Float = 0.4f): Color {
  if (tint == null) return base
  val f = fraction.coerceIn(0f, 1f)
  return Color(
    red = base.red * (1f - f) + tint.red * f,
    green = base.green * (1f - f) + tint.green * f,
    blue = base.blue * (1f - f) + tint.blue * f,
    alpha = base.alpha,
  )
}
