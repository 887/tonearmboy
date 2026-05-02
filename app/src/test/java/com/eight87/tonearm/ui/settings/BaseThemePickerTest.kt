package com.eight87.tonearm.ui.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.20.4 — pin the three-way base-theme picker contract:
 *
 *   1. Every [BaseTheme] entry round-trips through DataStore.
 *   2. The default value is `DefaultAndroid` (Material You) so a
 *      fresh install reproduces the v1.0 visual.
 *   3. The album-art tint default is **on** so the user sees the
 *      tint without flipping a setting.
 *   4. The two settings are independent — toggling tint never
 *      mutates the base theme and vice versa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaseThemePickerTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val repo = SettingsRepository(context)

  @Before
  fun resetState() = runTest {
    // The `preferencesDataStore` delegate caches the DataStore by name
    // across tests in the same JVM, so we explicitly reset the keys
    // this suite reads/writes before every test.
    repo.setBaseTheme(BaseTheme.Default)
    repo.setAlbumArtTintEnabled(true)
  }

  @After
  fun tearDown() {
    context.filesDir.resolve("datastore").deleteRecursively()
  }

  @Test
  fun default_base_theme_is_default_android() = runTest {
    val s = repo.snapshot.first()
    assertEquals(BaseTheme.DefaultAndroid, s.baseTheme)
  }

  @Test
  fun default_album_art_tint_is_enabled() = runTest {
    val s = repo.snapshot.first()
    assertEquals(true, s.albumArtTintEnabled)
  }

  @Test
  fun every_base_theme_round_trips() = runTest {
    val values = listOf(
      BaseTheme.DefaultAndroid,
      BaseTheme.DefaultColors,
      BaseTheme.PureBlack,
      BaseTheme.Custom(0x6750A4L),
    )
    values.forEach { value ->
      repo.setBaseTheme(value)
      assertEquals(value, repo.snapshot.first().baseTheme)
    }
  }

  @Test
  fun album_art_tint_round_trips() = runTest {
    repo.setAlbumArtTintEnabled(false)
    assertEquals(false, repo.snapshot.first().albumArtTintEnabled)
    repo.setAlbumArtTintEnabled(true)
    assertEquals(true, repo.snapshot.first().albumArtTintEnabled)
  }

  @Test
  fun base_theme_and_tint_are_independent() = runTest {
    repo.setBaseTheme(BaseTheme.PureBlack)
    repo.setAlbumArtTintEnabled(false)
    val s1 = repo.snapshot.first()
    assertEquals(BaseTheme.PureBlack, s1.baseTheme)
    assertEquals(false, s1.albumArtTintEnabled)

    // Toggling one MUST NOT reset the other.
    repo.setAlbumArtTintEnabled(true)
    val s2 = repo.snapshot.first()
    assertEquals(BaseTheme.PureBlack, s2.baseTheme)
    assertEquals(true, s2.albumArtTintEnabled)

    repo.setBaseTheme(BaseTheme.DefaultColors)
    val s3 = repo.snapshot.first()
    assertEquals(BaseTheme.DefaultColors, s3.baseTheme)
    assertEquals(true, s3.albumArtTintEnabled)
  }

  @Test
  fun base_theme_fromStored_handles_unknown_string() {
    assertEquals(BaseTheme.Default, BaseTheme.fromStored(null))
    assertEquals(BaseTheme.Default, BaseTheme.fromStored("nonsense"))
    assertEquals(BaseTheme.PureBlack, BaseTheme.fromStored("PureBlack"))
  }
}
