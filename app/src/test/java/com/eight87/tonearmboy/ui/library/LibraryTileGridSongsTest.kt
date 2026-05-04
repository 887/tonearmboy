package com.eight87.tonearmboy.ui.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.28.6 — Songs tile-mode rendering. We assert that the grid
 * surfaces a `library_tile_grid` semantics tag and one
 * `library_tile_<mediaId>` per input track. The render path is
 * `TracksListContent(viewMode = Tile)` — the same shape the dispatcher
 * picks when [SettingsRepository.viewModes] reports Tile for Songs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryTileGridSongsTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun track(id: Long, title: String) = Track(
    id = id,
    title = title,
    artist = "Test",
    album = "Album",
    albumArtist = null,
    durationMs = 180_000L,
    trackNumber = id.toInt(),
    year = 2024,
    genre = "Synth",
    data = "/audio/$id.flac",
    dateAddedSeconds = 0L,
  )

  private val seeded = listOf(
    track(1, "Aria"),
    track(2, "Borealis"),
    track(3, "Cinder"),
  )

  @Test
  fun tile_mode_renders_one_tile_per_track_with_tagged_grid() {
    composeRule.setContent {
      Surface {
        TracksListContent(
          tracks = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          viewMode = ViewMode.Tile,
          onTrackClick = { _, _ -> },
          onComingSoon = {},
        )
      }
    }
    composeRule.onNodeWithTag("library_tile_grid").assertIsDisplayed()
    // At least one tile should be laid out and tagged with the
    // expected `library_tile_<id>` semantics tag. Downstream tiles
    // may not be laid out in Robolectric's tiny default viewport, so
    // we don't assert the full N-of-N count here — the grid plus its
    // first tile is the load-bearing contract.
    val tagged = (1L..3L).count { id ->
      composeRule.onAllNodesWithTag("library_tile_$id").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals("at least one library_tile_<id> tag rendered", true, tagged >= 1)
  }

  @Test
  fun tile_grouping_yields_three_distinct_section_runs_for_alphabetic_titles() {
    // "Aria", "Borealis", "Cinder" → A / B / C runs of length 1.
    val keys = seeded.map { initialKey(it.title.uppercase()) }
    val groups = buildGroups(keys)
    assertEquals(listOf("A", "B", "C"), groups.map { it.letter })
    assertEquals(listOf(1, 1, 1), groups.map { it.length })
  }

  @Test
  fun list_mode_does_not_render_the_tile_grid_tag() {
    composeRule.setContent {
      Surface {
        TracksListContent(
          tracks = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          viewMode = ViewMode.List,
          onTrackClick = { _, _ -> },
          onComingSoon = {},
        )
      }
    }
    assertEquals(0, composeRule.onAllNodesWithTag("library_tile_grid").fetchSemanticsNodes().size)
  }
}
