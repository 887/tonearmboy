package com.eight87.tonearm.ui.settings

import com.eight87.tonearm.ui.nav.SettingsAbout
import com.eight87.tonearm.ui.settings.catalog.RowKind
import com.eight87.tonearm.ui.settings.catalog.Section
import com.eight87.tonearm.ui.settings.catalog.SettingsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.16.4 — verifies the new About entry is wired into the catalog and
 * routes to the [SettingsAbout] destination. Pinning these expectations
 * here means a stealth removal during a refactor breaks compile or test.
 */
class AboutCatalogTest {

  @Test
  fun about_entry_is_present_under_root_library_group() {
    val entry = SettingsCatalog.byId(SettingsCatalog.ID_LIBRARY_ABOUT)
    assertEquals(Section.Root, entry.section)
    assertEquals(RowKind.Navigate, entry.kind)
    assertEquals(SettingsAbout, entry.destination)
  }

  @Test
  fun about_appears_after_rescan_in_root_library_card() {
    val rootEntries = SettingsCatalog.bySection(Section.Root)
      .filter { it.group == com.eight87.tonearm.ui.settings.catalog.Group.Library }
    val ids = rootEntries.map { it.id }
    val rescanIdx = ids.indexOf(SettingsCatalog.ID_LIBRARY_RESCAN)
    val aboutIdx = ids.indexOf(SettingsCatalog.ID_LIBRARY_ABOUT)
    assertTrue("Rescan must be present in Library group", rescanIdx >= 0)
    assertTrue("About must be present in Library group", aboutIdx >= 0)
    assertTrue("About must come after Rescan", aboutIdx > rescanIdx)
  }

  @Test
  fun about_search_keywords_match_expected_terms() {
    listOf("about", "version", "license", "github", "credits").forEach { term ->
      val results = SettingsCatalog.search(term).map { it.id }
      assertTrue(
        "Search for '$term' should include the About entry",
        results.contains(SettingsCatalog.ID_LIBRARY_ABOUT),
      )
    }
  }

  @Test
  fun about_breadcrumb_is_rooted_at_settings() {
    val entry = SettingsCatalog.byId(SettingsCatalog.ID_LIBRARY_ABOUT)
    assertEquals(listOf("Settings", "Library", "About"), entry.breadcrumb)
  }
}
