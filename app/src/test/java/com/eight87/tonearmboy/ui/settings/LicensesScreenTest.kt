package com.eight87.tonearmboy.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OSS Phase C.2 — drives [LicensesScreen] under Robolectric: confirms
 * the screen renders, a known dep is in the list, and tapping the row
 * opens a dialog containing the canonical SPDX license body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LicensesScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun renders_inventory_and_opens_license_dialog() {
    composeRule.setContent { LicensesScreen(onBack = {}) }

    composeRule.onNodeWithTag("licenses_screen").assertExists()

    // The first card carries `activity 1.13.0` (sorted by groupId then
    // artifactId, `androidx.activity:activity` lands at the top of the
    // 213-entry inventory). Tapping it opens the Apache-2.0 dialog.
    composeRule.onAllNodesWithText("Apache-2.0").onFirst().performClick()

    // The dialog body shows the Apache-2.0 license text — assert on a
    // canonical phrase from the SPDX text body.
    composeRule.onNodeWithText("Apache License", substring = true).assertExists()
    composeRule.onNodeWithTag("license_dialog_body").assertExists()
  }

  @Test
  fun loads_at_least_one_entry_from_assets() {
    val context = androidx.test.core.app.ApplicationProvider
      .getApplicationContext<android.content.Context>()
    val entries = loadLicensesFromAssets(context)
    assertTrue(entries.isNotEmpty())
  }
}
