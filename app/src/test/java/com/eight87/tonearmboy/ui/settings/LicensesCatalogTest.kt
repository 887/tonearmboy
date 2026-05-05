package com.eight87.tonearmboy.ui.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OSS Phase C.1 — guards the Licensee-generated `assets/licenses/artifacts.json`
 * inventory and its companion SPDX license-text assets. Catches three
 * regressions:
 *   - dependency removal (a known sample missing → tests fail loud)
 *   - new dependency with an unrecognised SPDX (no matching `<spdx>.txt`)
 *   - inventory drift (entry has a SPDX id but the text file is absent)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LicensesCatalogTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun catalog_is_non_empty() {
    val entries = loadLicensesFromAssets(context)
    assertTrue("artifacts.json should contain at least one entry", entries.isNotEmpty())
  }

  @Test
  fun every_entry_has_a_known_spdx_with_backing_text() {
    val entries = loadLicensesFromAssets(context)
    val allowed = setOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")
    val offenders = entries.filter { it.spdxId == null || it.spdxId !in allowed }
    assertTrue(
      "Entries with unknown / unhandled SPDX (extend the allowlist or add an asset): " +
        offenders.joinToString { "${it.groupId}:${it.artifactId}=${it.spdxId}" },
      offenders.isEmpty(),
    )
    val missingText = entries.filter { it.spdxId != null && it.licenseText == null }
    assertTrue(
      "Entries with SPDX id but no matching assets/licenses/<spdx>.txt: " +
        missingText.joinToString { "${it.groupId}:${it.artifactId}=${it.spdxId}" },
      missingText.isEmpty(),
    )
  }

  @Test
  fun catalog_contains_known_shipping_samples() {
    val entries = loadLicensesFromAssets(context)
    val coords = entries.map { "${it.groupId}:${it.artifactId}" }.toSet()
    val expected = listOf(
      "androidx.media3:media3-exoplayer",
      "io.coil-kt.coil3:coil-compose",
      "androidx.room:room-runtime",
    )
    expected.forEach { sample ->
      assertNotNull(
        "Expected $sample in the inventory — has a critical shipping dep been removed?",
        coords.firstOrNull { it == sample },
      )
    }
  }
}
