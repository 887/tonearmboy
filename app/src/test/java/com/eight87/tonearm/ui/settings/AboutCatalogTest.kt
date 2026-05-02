package com.eight87.tonearm.ui.settings

import com.eight87.tonearm.ui.nav.SettingsAbout
import com.eight87.tonearm.ui.settings.catalog.Group
import com.eight87.tonearm.ui.settings.catalog.RowKind
import com.eight87.tonearm.ui.settings.catalog.Section
import com.eight87.tonearm.ui.settings.catalog.SettingsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.16.4 — verifies the About entry is wired into the catalog and routes
 * to the [SettingsAbout] destination. About lives in its own [Group.About]
 * category at the bottom of Settings root (not under Library — see the
 * post-D.16 user correction). Pinning these expectations here means a
 * stealth removal or re-categorisation during a refactor breaks the test.
 */
class AboutCatalogTest {

  @Test
  fun about_entry_routes_to_settings_about_destination() {
    val entry = SettingsCatalog.byId(SettingsCatalog.ID_ABOUT)
    assertEquals(Section.Root, entry.section)
    assertEquals(Group.About, entry.group)
    assertEquals(RowKind.Navigate, entry.kind)
    assertEquals(SettingsAbout, entry.destination)
  }

  @Test
  fun about_is_its_own_group_at_root() {
    val rootEntries = SettingsCatalog.bySection(Section.Root)
    val aboutEntries = rootEntries.filter { it.group == Group.About }
    assertEquals("Exactly one About entry expected", 1, aboutEntries.size)
    assertEquals(SettingsCatalog.ID_ABOUT, aboutEntries.single().id)
  }

  @Test
  fun about_appears_after_library_group_in_root_order() {
    val rootEntries = SettingsCatalog.bySection(Section.Root)
    val firstLibraryIdx = rootEntries.indexOfFirst { it.group == Group.Library }
    val aboutIdx = rootEntries.indexOfFirst { it.group == Group.About }
    assertTrue("Library group must exist in root", firstLibraryIdx >= 0)
    assertTrue("About group must exist in root", aboutIdx >= 0)
    assertTrue(
      "About must render after the entire Library group",
      aboutIdx > rootEntries.indexOfLast { it.group == Group.Library },
    )
  }

  @Test
  fun about_search_keywords_match_expected_terms() {
    listOf("about", "version", "license", "github", "credits").forEach { term ->
      val results = SettingsCatalog.search(term).map { it.id }
      assertTrue(
        "Search for '$term' should include the About entry",
        results.contains(SettingsCatalog.ID_ABOUT),
      )
    }
  }

  @Test
  fun about_breadcrumb_is_rooted_at_settings_without_library_segment() {
    val entry = SettingsCatalog.byId(SettingsCatalog.ID_ABOUT)
    assertEquals(listOf("Settings", "About"), entry.breadcrumb)
  }
}
