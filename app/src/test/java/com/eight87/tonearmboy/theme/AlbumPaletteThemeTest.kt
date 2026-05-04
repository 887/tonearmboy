package com.eight87.tonearmboy.theme

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * D.20.4 — pin the palette extraction + chrome-blend shape so a
 * known-red bitmap (Velvet Den's cover) yields a red-biased
 * `surfaceTint`, and the album-art tint toggle's "off" state collapses
 * the chrome back to the base scheme.
 *
 * `AndroidX Palette` requires a real `Bitmap` so we run under the
 * Robolectric runner; the bitmap itself is hand-painted so the test
 * doesn't depend on test-fixture bytes on disk.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AlbumPaletteThemeTest {

  private fun solidBitmap(color: Int, side: Int = 32): Bitmap {
    val pixels = IntArray(side * side) { color }
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, side, 0, 0, side, side)
    return bmp
  }

  private fun mostlyRedBitmap(side: Int = 32): Bitmap {
    // Robolectric's Canvas is a stub — drawColor/drawRect won't
    // populate the pixel buffer. Build the pixels by hand and feed
    // them through Bitmap.setPixels so Palette.Builder has real
    // data to quantise.
    val pixels = IntArray(side * side) { idx ->
      val y = idx / side
      val x = idx % side
      // Velvet-Den-ish: deep dark red over most of the surface,
      // with a small near-black square in the top-left corner.
      if (x < side / 4 && y < side / 4) AColor.rgb(0x10, 0x05, 0x05)
      else AColor.rgb(0x66, 0x12, 0x12)
    }
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, side, 0, 0, side, side)
    return bmp
  }

  @Test
  fun extract_returns_swatches_for_a_dark_red_bitmap() {
    val palette = extractAlbumPalette(mostlyRedBitmap())
    // We don't pin the exact rgb because Palette can pick either the
    // muted or the vibrant slot depending on its quantiser. We DO
    // pin the bias toward red, expressed as red dominating green +
    // blue on whichever swatch we got.
    val tint = palette.surfaceTint
    assertNotNull("dark red bitmap must yield a non-null surfaceTint", tint)
    val r = tint!!.red
    val g = tint.green
    val b = tint.blue
    assertTrue("expected red-biased tint, got rgb=($r,$g,$b)", r > g && r > b)
  }

  @Test
  fun extract_falls_back_to_empty_for_one_pixel_bitmap() {
    val tiny = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val palette = extractAlbumPalette(tiny)
    assertEquals(AlbumPalette.Empty, palette)
    assertNull(palette.surfaceTint)
  }

  @Test
  fun blend_surface_returns_base_when_tint_is_null() {
    val base = Color(red = 0.13f, green = 0.13f, blue = 0.13f, alpha = 1f)
    val out = blendSurface(base, tint = null)
    assertEquals(base, out)
  }

  @Test
  fun blend_surface_biases_toward_tint_when_present() {
    val base = Color.Black
    val tint = Color.Red
    val blended = blendSurface(base, tint, fraction = 0.4f)
    // 40 % toward tint: red component = 0 * 0.6 + 1 * 0.4 = 0.4.
    assertTrue("expected red component near 0.4, got ${blended.red}", kotlin.math.abs(blended.red - 0.4f) < 0.01f)
    assertEquals(0f, blended.green, 0.01f)
    assertEquals(0f, blended.blue, 0.01f)
  }

  @Test
  fun blend_surface_clamps_fraction_into_unit_range() {
    val base = Color.Black
    val tint = Color.White
    // 1.5f over-shoot: clamp to 1.0, output should be pure tint.
    val out = blendSurface(base, tint, fraction = 1.5f)
    assertEquals(1f, out.red, 0.001f)
    assertEquals(1f, out.green, 0.001f)
    assertEquals(1f, out.blue, 0.001f)
  }

  @Test
  fun toggle_off_collapses_to_base_via_blendSurface_null_path() {
    // The "Tint chrome by album art" toggle works by passing `tint =
    // null` to `blendSurface`. So even when the palette has a
    // non-null swatch, the chrome stays put.
    val base = Color.DarkGray
    val palette = AlbumPalette(darkMutedSwatch = Color.Red, darkVibrantSwatch = null)
    // Mirror what TonearmboyTheme does when albumArtTintEnabled = false:
    val tint = if (false) palette.surfaceTint else null
    val blended = blendSurface(base, tint)
    assertEquals(base, blended)
  }

  @Test
  fun palette_prefers_dark_muted_then_falls_back_to_dark_vibrant() {
    val muted = AlbumPalette(darkMutedSwatch = Color.Red, darkVibrantSwatch = Color.Green)
    assertEquals(Color.Red, muted.surfaceTint)
    val vibrantOnly = AlbumPalette(darkMutedSwatch = null, darkVibrantSwatch = Color.Green)
    assertEquals(Color.Green, vibrantOnly.surfaceTint)
    assertNull(AlbumPalette.Empty.surfaceTint)
  }

  @Test
  fun extract_handles_solid_black_without_throwing() {
    // Pathological: an all-black cover may not have any vibrant /
    // muted swatch detected. The extractor must degrade to Empty
    // rather than throw.
    val out = extractAlbumPalette(solidBitmap(AColor.BLACK))
    // surfaceTint may or may not be null depending on Palette's
    // quantiser; either way we must not throw.
    assertNotNull(out)
  }
}

