package com.eight87.tonearm.ui.settings

import com.eight87.tonearm.ui.settings.catalog.SettingsCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D.25.2 — assert the dead Round-mode toggle stays gone. The user
 * pinned this requirement: Material 3 rounded corners are the
 * universal default; there is no Material-2 fallback. Any future
 * re-introduction of `roundMode` here will break this test loudly.
 *
 *   1. `SettingsCatalog` has no row whose id ends in `round_mode`.
 *   2. `SettingsCatalog` does not declare `ID_ROUND_MODE` (compile-
 *      time check via reflection — getting the constant by name
 *      returns null when it doesn't exist).
 *   3. `SettingsRepository` has no `setRoundMode` mutator. (R.B.5
 *      retired the snapshot data class so the original
 *      `getRoundMode` reflection check moved to the repo.)
 */
class RoundModeRemovedTest {

  @Test
  fun catalog_has_no_round_mode_row() {
    val rows = SettingsCatalog.entries.filter { it.id.endsWith("round_mode") }
    assertFalse(
      "Catalog still contains a round_mode row: ${rows.map { it.id }}",
      rows.isNotEmpty(),
    )
  }

  @Test
  fun catalog_companion_does_not_declare_id_round_mode_const() {
    // Reflectively look for the public ID_ROUND_MODE field.
    val field = runCatching {
      SettingsCatalog::class.java.getField("ID_ROUND_MODE")
    }.getOrNull()
    assertNull(
      "SettingsCatalog must not re-introduce ID_ROUND_MODE",
      field,
    )
  }

  @Test
  fun settings_repository_has_no_set_round_mode_method() {
    val hasSetter = SettingsRepository::class.java.methods.any { it.name == "setRoundMode" }
    assertFalse(
      "SettingsRepository must not re-introduce setRoundMode",
      hasSetter,
    )
  }
}
