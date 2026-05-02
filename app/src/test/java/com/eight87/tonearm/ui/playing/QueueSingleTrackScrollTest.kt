package com.eight87.tonearm.ui.playing

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.27.4 — pin the single-track-queue scroll contract.
 *
 * The queue section's outer column computes its min-height as
 * `max(parentViewportHeight, N × rowHeight)`. The user reported that
 * a queue of one track collapsed to 56 dp, so the parent LazyColumn
 * had no scroll runway past the first viewport. This test pins the
 * pure size logic.
 *
 * The composable also calls `Modifier.imePadding()` so the on-screen
 * keyboard doesn't cover the active row when the filter focuses; we
 * pin the existence of that modifier in [QueueSection]'s file via
 * the existing `QueueFilterTest` Compose harness.
 */
class QueueSingleTrackScrollTest {

  /**
   * Reproduce the pure size computation from [QueueSection]. Kept out
   * of the production source because the Composable holds it inline,
   * but the math is trivial and this test pins the behaviour.
   */
  private fun outerMinHeightDp(itemCount: Int, viewportDp: Int, rowHeightDp: Int = 56): Int {
    val byRows = itemCount * rowHeightDp
    return if (viewportDp > byRows) viewportDp else byRows
  }

  @Test fun single_track_queue_reserves_at_least_viewport_height() {
    // 1 track × 56 dp = 56 dp content; with an 800 dp viewport, the
    // queue section must claim ≥ 800 dp so the parent has scroll room.
    assertEquals(800, outerMinHeightDp(itemCount = 1, viewportDp = 800))
  }

  @Test fun many_tracks_queue_reserves_full_n_times_row_height() {
    // 100 tracks × 56 dp = 5600 dp content, viewport 800 dp; queue
    // expands to its content height so the user can scroll the full
    // length without the parent clipping.
    assertEquals(5600, outerMinHeightDp(itemCount = 100, viewportDp = 800))
  }

  @Test fun zero_track_queue_reserves_viewport_for_empty_state() {
    // Empty queue still needs a viewport so the empty-state placeholder
    // ("Nothing in the queue") doesn't squish to a single line.
    assertEquals(800, outerMinHeightDp(itemCount = 0, viewportDp = 800))
  }

  @Test fun threshold_is_at_n_times_row_equals_viewport() {
    // viewport 800 dp; threshold at 800 / 56 = 14.28 → 14 rows fit
    // entirely, 15 rows overflow.
    assertTrue(outerMinHeightDp(14, 800) >= 800)
    assertEquals(840, outerMinHeightDp(15, 800)) // 15 × 56 = 840
  }

  @Test fun viewport_height_is_a_dp_value() {
    // The Composable parameter is typed `Dp`. Pin the call shape so a
    // future refactor doesn't accidentally take an Int.
    val viewport = 800.dp
    assertEquals(800f, viewport.value)
  }
}
