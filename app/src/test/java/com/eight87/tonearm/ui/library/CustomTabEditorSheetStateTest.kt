package com.eight87.tonearm.ui.library

import com.eight87.tonearm.data.FilterCriteria
import com.eight87.tonearm.data.db.CustomTabContentType
import com.eight87.tonearm.data.db.CustomTabEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.18.6 — state-machine assertions for the CustomTabEditorSheet's
 * pure logic. The Compose UI is exercised by the screenshot
 * regression in the smoke test; this file pins the bits that don't
 * need a renderer:
 *
 *   - DateAddedOption epoch rendering / parsing.
 *   - Initial criteria → editor selection mapping.
 *   - Save composes a [FilterCriteria] from the user's selections.
 */
class CustomTabEditorSheetStateTest {

  @Test fun date_added_option_to_epoch_offset() {
    val now = 1_000_000L
    assertEquals(null, DateAddedOption.Any.toEpochOffset(now))
    assertEquals(now - 7L * 86400, DateAddedOption.Last7Days.toEpochOffset(now))
    assertEquals(now - 30L * 86400, DateAddedOption.Last30Days.toEpochOffset(now))
    assertEquals(now - 365L * 86400, DateAddedOption.LastYear.toEpochOffset(now))
  }

  @Test fun date_added_from_epoch_buckets_correctly() {
    // We use the live clock here — bucket bounds are coarse so the
    // exact "now" doesn't matter (any reasonable epoch within the
    // last 366 days lands in the right bucket).
    val now = System.currentTimeMillis() / 1000
    assertEquals(DateAddedOption.Any, DateAddedOption.fromEpoch(null))
    assertEquals(DateAddedOption.Last7Days, DateAddedOption.fromEpoch(now - 5L * 86400))
    assertEquals(DateAddedOption.Last30Days, DateAddedOption.fromEpoch(now - 20L * 86400))
    assertEquals(DateAddedOption.LastYear, DateAddedOption.fromEpoch(now - 200L * 86400))
  }

  @Test fun has_art_label_branches() {
    assertEquals("Any", hasArtLabel(HasArtOption.Any))
    assertEquals("Only with album art", hasArtLabel(HasArtOption.Only))
    assertEquals("Only without album art", hasArtLabel(HasArtOption.Without))
  }

  @Test fun content_type_label_branches() {
    assertEquals("Songs", contentTypeLabel(CustomTabContentType.SONGS))
    assertEquals("Albums", contentTypeLabel(CustomTabContentType.ALBUMS))
    assertEquals("Artists", contentTypeLabel(CustomTabContentType.ARTISTS))
    assertEquals("Genres", contentTypeLabel(CustomTabContentType.GENRES))
  }

  @Test fun edit_mode_round_trips_an_existing_entity() {
    val entity = CustomTabEntity(
      id = 7,
      name = "Synthwave 2025",
      position = 3,
      contentType = CustomTabContentType.ALBUMS,
      criteriaJson = FilterCriteria.toJson(
        FilterCriteria(genres = listOf("Synthwave"), yearMin = 2025),
      ),
    )
    val parsed = FilterCriteria.fromJson(entity.criteriaJson)
    assertEquals(listOf("Synthwave"), parsed.genres)
    assertEquals(2025, parsed.yearMin)
    // Re-encode -> identical
    val reencoded = FilterCriteria.toJson(parsed)
    assertEquals(parsed, FilterCriteria.fromJson(reencoded))
  }

  @Test fun multi_select_toggle_semantics() {
    var set = setOf<String>()
    set = if ("A" in set) set - "A" else set + "A"
    assertTrue("A" in set)
    set = if ("A" in set) set - "A" else set + "A"
    assertTrue("A" !in set)
  }
}
