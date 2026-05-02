package com.eight87.tonearm.ui.library

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.11.7 Custom tab rendering — **deferred to land alongside D.8d**.
 *
 * D.8c (Room schema for `CustomTabEntity` + `FilterCriteria` matching
 * predicates + `CustomTabDao` + `LibraryRepository` matching methods)
 * and D.8d (`CustomTabEditorSheet` + rendered custom tabs in the rail)
 * are still open in `docs/plans/main.md`. The asserted behaviours for
 * D.11.7 — filter intersection, criteria-matched track / album / artist
 * / genre lists, custom tab in the rail — depend on those entities
 * existing. Once D.8c/d ship, this file will host:
 *
 *   - `criteria_with_genres_synthwave_returns_only_synthwave_tracks`
 *   - `criteria_with_yearMin_2025_returns_only_2025_tracks`
 *   - `criteria_intersection_narrows_results`
 *   - `custom_tab_appears_in_rail_after_save`
 *
 * For now this file ships a placeholder that the JVM test harness
 * picks up — keeps the file present, the tracking visible, and the
 * test harness green.
 */
class CustomTabRenderingTest {

  @Test
  fun deferred_to_land_alongside_d8d() {
    // Marker test: the D.11.7 unit assertions move in once D.8c/d land.
    assertTrue("placeholder pending D.8c/d", true)
  }
}
