package com.eight87.tonearmboy.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/**
 * D.20.4 (extends D.8b) — derived palette colours for the currently
 * playing track. Provided through [LocalAlbumPalette] and consumed by
 * [TonearmboyTheme] to bias `surface` / `surfaceVariant` / `background`
 * toward the dominant dark swatch of the cover art.
 *
 * `null` swatches mean either no track is playing or palette
 * extraction failed (e.g. the track has no cover, the bitmap was
 * one-pixel, etc.). Consumers MUST fall back to the static base
 * theme in that case rather than picking an ad-hoc colour.
 *
 * `darkMutedSwatch` is preferred (it's the lower-saturation, lower-
 * value swatch that best blends with the chrome); we fall back to
 * `darkVibrantSwatch` if muted is null. Both being null collapses
 * the consumer to the base theme.
 */
@Immutable
data class AlbumPalette(
  val darkMutedSwatch: Color?,
  val darkVibrantSwatch: Color?,
) {
  /**
   * The colour to use for the chrome surface bias. `darkMutedSwatch`
   * preferred, then `darkVibrantSwatch`. Null when neither is
   * available — the theme should keep the base scheme.
   */
  val surfaceTint: Color?
    get() = darkMutedSwatch ?: darkVibrantSwatch

  companion object {
    /** "No tint available" — the static base theme is in force. */
    val Empty: AlbumPalette = AlbumPalette(darkMutedSwatch = null, darkVibrantSwatch = null)
  }
}

/**
 * `CompositionLocal` for the active [AlbumPalette]. The default is
 * [AlbumPalette.Empty] so a composable that reads the local outside
 * a `TonearmboyTheme` (e.g. previews) gets a sane fallback rather than
 * a crash.
 */
val LocalAlbumPalette = compositionLocalOf { AlbumPalette.Empty }

/**
 * Synchronous palette extraction from a [Bitmap]. Used by
 * `AlbumPaletteCache` (in the UI layer) when a track transitions; the
 * cache calls into the `Palette.Builder.generate()` API on a
 * background dispatcher and then publishes the result to a
 * `MutableStateFlow` the activity feeds into [LocalAlbumPalette].
 *
 * Pure helper so it's unit-testable on the JVM with a fake bitmap.
 */
fun extractAlbumPalette(bitmap: Bitmap): AlbumPalette {
  if (bitmap.width <= 1 || bitmap.height <= 1) return AlbumPalette.Empty
  val palette = Palette.Builder(bitmap)
    // Defaults: 16 max colours, ~24 pixel resize. Plenty for a
    // 600x600 cover; we're not trying to be precise, just biased.
    .generate()
  val muted = palette.darkMutedSwatch?.rgb?.let { Color(it) }
  val vibrant = palette.darkVibrantSwatch?.rgb?.let { Color(it) }
  return AlbumPalette(darkMutedSwatch = muted, darkVibrantSwatch = vibrant)
}
