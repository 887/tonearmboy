package com.eight87.tonearmboy.ui.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.library.tabs.TracksListContent
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase F.2 / F.3 — exercise the track context menu's "Delete file…"
 * entry and the multi-select bar that the long-press flow opens.
 *
 * Tests render [TracksListContent] (the repository-free body of
 * [TracksListScreen]) against a hand-built list of three tracks so we
 * don't fight the library scanner. The screen-level wrapper is a
 * thin `repository.observeTracks().collectAsState() → TracksListContent`
 * shim with no extra logic worth covering separately.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MultiSelectDeleteTest {

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

  private val seeded = listOf(track(1, "Aria"), track(2, "Borealis"), track(3, "Cinder"))

  @Test
  fun long_press_enters_multi_select_and_shows_count_one() {
    composeRule.setContent {
      Surface {
        TracksListContent(
          tracks = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          onTrackClick = { _, _ -> },
          onComingSoon = {},
          onDeleteTracks = {},
        )
      }
    }
    composeRule.onAllNodesWithTag("track_row")[0].performTouchInput { longClick() }
    composeRule.onNodeWithTag("multi_select_bar").assertIsDisplayed()
    composeRule.onNodeWithText("1 selected").assertIsDisplayed()
  }

  @Test
  fun multi_select_delete_button_invokes_callback() {
    val captured = mutableListOf<List<Track>>()
    composeRule.setContent {
      Surface {
        TracksListContent(
          tracks = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          onTrackClick = { _, _ -> },
          onComingSoon = {},
          onDeleteTracks = { captured += it },
        )
      }
    }
    composeRule.onAllNodesWithTag("track_row")[0].performTouchInput { longClick() }
    composeRule.onNodeWithTag("multi_select_delete").performClick()
    assertEquals(1, captured.size)
    assertTrue(
      "delete callback should pass at least one track",
      captured.first().isNotEmpty(),
    )
  }

  @Test
  fun overflow_delete_entry_invokes_callback_with_single_track() {
    val captured = mutableListOf<List<Track>>()
    composeRule.setContent {
      Surface {
        TracksListContent(
          tracks = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          onTrackClick = { _, _ -> },
          onComingSoon = {},
          onDeleteTracks = { captured += it },
        )
      }
    }
    composeRule.onAllNodesWithTag("track_row_overflow")[0].performClick()
    composeRule.onNodeWithTag("track_context_delete").performClick()
    assertEquals(1, captured.size)
    assertEquals(1, captured.first().size)
  }
}
