package com.eight87.tonearm.ui.settings

import androidx.test.core.app.ApplicationProvider
import com.eight87.tonearm.theme.deriveCustomScheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.25.1 — pin the custom-color base-theme contract:
 *
 *   1. `BaseTheme.fromStored("Custom:0xFF6750A4")` round-trips cleanly
 *      through the storage form (alpha bits ignored, only 24-bit RGB
 *      preserved).
 *   2. The repository persists `BaseTheme.Custom(seed)` and re-reads
 *      the same seed back.
 *   3. The derived `ColorScheme` for a chosen seed has its `primary`
 *      shifted toward that seed's hue (not the default purple), so a
 *      visual change is actually observable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaseThemeCustomTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val repo = SettingsRepository(context)

  @Before
  fun resetState() = runTest {
    repo.setBaseTheme(BaseTheme.Default)
  }

  @After
  fun tearDown() {
    context.filesDir.resolve("datastore").deleteRecursively()
  }

  @Test
  fun fromStored_parses_custom_seed_with_alpha_prefix() {
    val parsed = BaseTheme.fromStored("Custom:0xFF6750A4")
    assertTrue("expected Custom, got $parsed", parsed is BaseTheme.Custom)
    // Alpha is dropped — only 24-bit RGB survives.
    assertEquals(0x6750A4L, (parsed as BaseTheme.Custom).seedRgb)
  }

  @Test
  fun fromStored_parses_custom_seed_without_alpha_prefix() {
    val parsed = BaseTheme.fromStored("Custom:0x123456")
    assertTrue(parsed is BaseTheme.Custom)
    assertEquals(0x123456L, (parsed as BaseTheme.Custom).seedRgb)
  }

  @Test
  fun fromStored_falls_back_on_malformed_custom_string() {
    assertEquals(BaseTheme.Default, BaseTheme.fromStored("Custom:notahex"))
    assertEquals(BaseTheme.Default, BaseTheme.fromStored("Custom:"))
  }

  @Test
  fun toStored_serialises_custom_with_six_hex_digits() {
    assertEquals("Custom:0x000ABC", BaseTheme.Custom(0xABCL).toStored())
    assertEquals("Custom:0xFFFFFF", BaseTheme.Custom(0xFFFFFFL).toStored())
  }

  @Test
  fun roundTrip_through_datastore_preserves_seed() = runTest {
    val seed = 0xFF8800L
    repo.setBaseTheme(BaseTheme.Custom(seed))
    val baseTheme = repo.baseTheme.flow.first()
    assertTrue(baseTheme is BaseTheme.Custom)
    assertEquals(seed, (baseTheme as BaseTheme.Custom).seedRgb)
  }

  @Test
  fun derived_color_scheme_uses_custom_seed_primary() {
    // Bright orange seed.
    val orange = 0xFF8800L
    val scheme = deriveCustomScheme(orange, darkTheme = false)
    // The derived primary should be more red than green and more
    // green than blue — the canonical default purple has the inverse
    // ordering, so this will fail loudly if the derivation is hard-
    // coded to the default palette.
    assertTrue("primary red ${scheme.primary.red} should be > green ${scheme.primary.green}",
      scheme.primary.red > scheme.primary.green,
    )
    assertTrue("primary green ${scheme.primary.green} should be > blue ${scheme.primary.blue}",
      scheme.primary.green > scheme.primary.blue,
    )
  }

  @Test
  fun derived_dark_scheme_differs_from_light_scheme() {
    // Same seed, different dark flag — primaries should not be identical.
    val seed = 0x33AAEEL
    val light = deriveCustomScheme(seed, darkTheme = false)
    val dark = deriveCustomScheme(seed, darkTheme = true)
    assertNotEquals(light.primary, dark.primary)
  }
}
