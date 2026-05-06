package com.eight87.tonearmboy.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * m3-expressive Phase C — pair of `(container, onContainer)` colours
 * for the per-row coloured circle row-icon avatars. Mirrors Material
 * 3's `primaryContainer` / `onPrimaryContainer` token shape so the
 * runtime use site reads identically to a built-in colour role.
 *
 * Hand-picked palette of ~7 hues (sky blue, purple, pink, magenta,
 * orange, peach, green) hits the Android 16 Settings "happy colours"
 * spread without over-fitting: each card has 2–9 rows and we want
 * each row to read as its own colour. We pick by hashing the row's
 * stable id ([accentFor]) so the palette stays consistent across
 * sessions / app reinstalls but no hand-mapping work is required.
 *
 * The accents are NOT driven off `dynamicDarkColorScheme()` —
 * letting the user's wallpaper steamroll the per-category intent
 * defeats the colour-coding entirely. Hand-picked stays hand-picked.
 */
data class CategoryAccent(
  val container: Color,
  val onContainer: Color,
)

/**
 * Dark / AMOLED-leaning palette. Containers around chroma 60 %,
 * lightness 22–28 % so the circle reads as a quietly-saturated
 * tile against the page; on-container tones land at lightness 80 %
 * for high-contrast filled glyphs.
 */
private val DarkPalette = listOf(
  CategoryAccent(container = Color(0xFF003D5C), onContainer = Color(0xFF8FCEFF)), // sky blue (Network)
  CategoryAccent(container = Color(0xFF3F2C73), onContainer = Color(0xFFD0BCFF)), // purple (Apps)
  CategoryAccent(container = Color(0xFF5C2940), onContainer = Color(0xFFFFB1C8)), // pink (Notifications)
  CategoryAccent(container = Color(0xFF5C1F45), onContainer = Color(0xFFFFB1D8)), // magenta (Modes)
  CategoryAccent(container = Color(0xFF5C3300), onContainer = Color(0xFFFFB877)), // orange (Display)
  CategoryAccent(container = Color(0xFF5C3D24), onContainer = Color(0xFFFFC59A)), // peach (Wallpaper)
  CategoryAccent(container = Color(0xFF1F4D2E), onContainer = Color(0xFF9CDDB4)), // green (About)
)

/**
 * Light palette — same hues, brighter containers so the avatar reads
 * against the lighter page surface. On-container tones drop to
 * lightness 25 % for legible filled glyphs.
 */
private val LightPalette = listOf(
  CategoryAccent(container = Color(0xFFCFE8FF), onContainer = Color(0xFF003047)),
  CategoryAccent(container = Color(0xFFE8DEF8), onContainer = Color(0xFF21005D)),
  CategoryAccent(container = Color(0xFFFFD8E5), onContainer = Color(0xFF3F0026)),
  CategoryAccent(container = Color(0xFFFFD8EA), onContainer = Color(0xFF3F0028)),
  CategoryAccent(container = Color(0xFFFFDDB6), onContainer = Color(0xFF2B1700)),
  CategoryAccent(container = Color(0xFFFFE2C9), onContainer = Color(0xFF2B1900)),
  CategoryAccent(container = Color(0xFFC8E8D2), onContainer = Color(0xFF002912)),
)

/**
 * Pick an accent for the row identified by [id]. Hash-based so the
 * mapping is stable across runs without a hand-built id→accent map.
 *
 * Using `(id.hashCode().rem(N) + N).rem(N)` to avoid Kotlin's
 * sign-preserving `%` on negative hashes.
 */
@Composable
@ReadOnlyComposable
fun accentFor(id: String): CategoryAccent {
  val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
  val raw = id.hashCode() % palette.size
  val idx = (raw + palette.size) % palette.size
  return palette[idx]
}
