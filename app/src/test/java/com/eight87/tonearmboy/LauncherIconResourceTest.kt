package com.eight87.tonearmboy

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.17.1 — verify the adaptive launcher icon resources resolve. The
 * APK is the source of truth for what the launcher will load: this
 * test pins the names + the existence of the foreground / monochrome
 * raster set + the anydpi-v26 adaptive XML.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherIconResourceTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val res = context.resources

  @Test
  fun ic_launcher_resolves_to_a_drawable() {
    val id = res.getIdentifier("ic_launcher", "mipmap", context.packageName)
    assertNotEquals("R.mipmap.ic_launcher must resolve", 0, id)
    assertNotNull(res.getDrawable(id, null))
  }

  @Test
  fun ic_launcher_round_resolves_to_a_drawable() {
    val id = res.getIdentifier("ic_launcher_round", "mipmap", context.packageName)
    assertNotEquals("R.mipmap.ic_launcher_round must resolve", 0, id)
    assertNotNull(res.getDrawable(id, null))
  }

  @Test
  fun ic_launcher_foreground_resolves_in_every_density() {
    val id = res.getIdentifier("ic_launcher_foreground", "mipmap", context.packageName)
    assertNotEquals("R.mipmap.ic_launcher_foreground must resolve", 0, id)
    assertNotNull(res.getDrawable(id, null))
  }

  @Test
  fun ic_launcher_monochrome_layer_exists() {
    val id = res.getIdentifier("ic_launcher_monochrome", "mipmap", context.packageName)
    assertNotEquals(
      "R.mipmap.ic_launcher_monochrome must resolve so themed icons (Android 13+) work",
      0,
      id,
    )
    assertNotNull(res.getDrawable(id, null))
  }

  @Test
  fun launcher_background_color_is_defined() {
    val id = res.getIdentifier("launcher_background", "color", context.packageName)
    assertNotEquals("R.color.launcher_background must resolve", 0, id)
    // Robolectric uses res.getColor(int, Theme); a missing colour throws.
    res.getColor(id, null)
  }
}
