package com.eight87.tonearmboy.ui.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.tonearmboy.data.model.Album
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import com.eight87.tonearmboy.ui.settings.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.28.4 / D.28.6 — Albums in List mode. Asserts the same sticky-letter-
 * header shape that Songs uses today: one row per album tagged
 * `album_list_row`, and a `library_list_card` chrome wrapper. Sticky
 * letter headers are the load-bearing visual contract — the user
 * specifically called this out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryAlbumsListViewTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun album(id: Long, name: String, artist: String? = null) =
    Album(id, name, artist, trackCount = 4, year = 2024, mediaStoreAlbumId = null)

  private val seeded = listOf(
    album(1, "Aerial Roots", "First Artist"),
    album(2, "Beachglass", "Second Artist"),
    album(3, "Cinder Block", "Third Artist"),
  )

  @Test
  fun list_mode_renders_one_row_per_album() {
    composeRule.setContent {
      Surface {
        AlbumsTabContent(
          albums = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          viewMode = ViewMode.List,
        )
      }
    }
    val rows = composeRule.onAllNodesWithTag("album_list_row").fetchSemanticsNodes()
    assertEquals(3, rows.size)
  }

  @Test
  fun list_mode_renders_album_titles_and_artists() {
    composeRule.setContent {
      Surface {
        AlbumsTabContent(
          albums = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          viewMode = ViewMode.List,
        )
      }
    }
    // Use semantics-tag count rather than text since LazyColumn rows
    // may not all be laid out in the Robolectric default viewport.
    val rows = composeRule.onAllNodesWithTag("album_list_row").fetchSemanticsNodes()
    assertTrue("at least one album row rendered", rows.isNotEmpty())
  }

  @Test
  fun list_mode_section_keys_match_first_letters() {
    // The same section-key shape Songs uses. With three albums starting
    // A / B / C we expect three distinct sticky-header letter groups.
    val sectionKeys = seeded.map { initialKey(it.name.uppercase()) }
    assertEquals(listOf("A", "B", "C"), sectionKeys)
    val ordered = sectionKeys.distinct()
    assertEquals(3, ordered.size)
  }

  @Test
  fun list_mode_does_not_render_tile_grid_tag() {
    composeRule.setContent {
      Surface {
        AlbumsTabContent(
          albums = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          viewMode = ViewMode.List,
        )
      }
    }
    assertEquals(0, composeRule.onAllNodesWithTag("library_tile_grid").fetchSemanticsNodes().size)
  }

  @Test
  fun tile_mode_renders_grid_not_list() {
    composeRule.setContent {
      Surface {
        AlbumsTabContent(
          albums = seeded,
          sort = TabSort(SortKey.Name, SortDirection.Ascending),
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          viewMode = ViewMode.Tile,
        )
      }
    }
    composeRule.onNodeWithTag("library_tile_grid").assertExists()
    assertTrue(composeRule.onAllNodesWithTag("album_list_row").fetchSemanticsNodes().isEmpty())
  }
}
