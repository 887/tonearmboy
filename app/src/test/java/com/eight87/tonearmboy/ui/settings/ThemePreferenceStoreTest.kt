package com.eight87.tonearmboy.ui.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM unit tests for the DataStore-backed [ThemePreferenceStore].
 *
 * Robolectric provides a real `Context` and a writable file system so
 * the production `preferencesDataStore` actually exercises its disk
 * path — no fakes, no in-memory stand-ins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePreferenceStoreTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val store = ThemePreferenceStore(context)

  @After
  fun tearDown() {
    // DataStore writes to the app's files dir; clear so the next test
    // starts from defaults.
    context.filesDir.resolve("datastore").deleteRecursively()
  }

  @Test
  fun defaults_to_system() = runTest {
    assertEquals(ThemePreference.System, store.flow.first())
  }

  @Test
  fun set_persists_value() = runTest {
    store.set(ThemePreference.Dark)
    assertEquals(ThemePreference.Dark, store.flow.first())
    store.set(ThemePreference.Light)
    assertEquals(ThemePreference.Light, store.flow.first())
  }

  @Test
  fun fromStored_unknownString_fallsBackToDefault() {
    assertEquals(ThemePreference.Default, ThemePreference.fromStored("nonsense"))
    assertEquals(ThemePreference.Default, ThemePreference.fromStored(null))
  }
}
