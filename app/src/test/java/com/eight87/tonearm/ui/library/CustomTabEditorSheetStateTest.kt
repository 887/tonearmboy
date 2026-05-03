package com.eight87.tonearm.ui.library

import com.eight87.tonearm.data.FilterCondition
import com.eight87.tonearm.data.FilterCriteria
import com.eight87.tonearm.data.db.CustomTabContentType
import com.eight87.tonearm.data.db.CustomTabEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.30 — round-trip an existing custom tab through the conditions
 * model. The pre-D.30 editor state-machine tests (DateAddedOption /
 * HasArtOption preset buckets, etc.) were tied to the old form and
 * have been retired with the form itself. Compose UI is exercised by
 * the screenshot regressions.
 */
class CustomTabEditorSheetStateTest {

  @Test fun edit_mode_round_trips_an_existing_entity() {
    val entity = CustomTabEntity(
      id = 7,
      name = "Synthwave 2025",
      position = 3,
      contentType = CustomTabContentType.ALBUMS,
      criteriaJson = FilterCriteria.toJson(
        FilterCriteria.of(genres = listOf("Synthwave"), yearMin = 2025),
      ),
    )
    val parsed = FilterCriteria.fromJson(entity.criteriaJson)
    val genres = parsed.conditions.filterIsInstance<FilterCondition.GenreIn>().single().values
    val year = parsed.conditions.filterIsInstance<FilterCondition.YearBetween>().single()
    assertEquals(listOf("Synthwave"), genres)
    assertEquals(2025, year.min)
    val reencoded = FilterCriteria.toJson(parsed)
    assertEquals(parsed, FilterCriteria.fromJson(reencoded))
  }

  @Test fun legacy_json_shape_translates_to_conditions() {
    val legacy = """{"genres":["Rock"],"yearMin":2010,"yearMax":2020}"""
    val parsed = FilterCriteria.fromJson(legacy)
    val genres = parsed.conditions.filterIsInstance<FilterCondition.GenreIn>().single().values
    val year = parsed.conditions.filterIsInstance<FilterCondition.YearBetween>().single()
    assertEquals(listOf("Rock"), genres)
    assertEquals(2010, year.min)
    assertEquals(2020, year.max)
  }
}
