package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.tonearmboy.ui.library.TileItem
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
 * R.D.3 — Compose-level tests for [LibraryTabRenderer]. We use a
 * fake [TabSpec] with a deterministic shape so the engine itself —
 * empty state / list rendering / tile-grid dispatch / testTag wiring —
 * is exercised independently of the five real specs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryTabRendererTest {

  @get:Rule
  val composeRule = createComposeRule()

  private data class FakeItem(val id: Long, val label: String, val letter: String)

  private object FakeSpec : TabSpec<FakeItem> {
    override val testTag: String = "fake_tab"
    // T.A.3 — point at any string resource; the test only asserts that
    // the empty-state composable renders, not on the resolved text.
    override val emptyMessageRes: Int = com.eight87.tonearmboy.R.string.library_empty_songs
    override val supportsTileMode: Boolean = true
    override fun id(item: FakeItem): Long = item.id
    override fun sectionKey(item: FakeItem, sort: TabSort, intelligentSorting: Boolean): String? =
      if (sort.key == SortKey.Name) item.letter else null
    override fun toTile(item: FakeItem, resources: android.content.res.Resources): TileItem =
      TileItem(id = item.id, title = item.label, subtitle = null, artUri = null, albumArtId = null)

    @Composable
    override fun ListRow(
      item: FakeItem,
      selected: Boolean,
      inSelectionMode: Boolean,
      onClick: () -> Unit,
      onLongClick: () -> Unit,
    ) {
      Text(
        text = item.label,
        modifier = Modifier.semantics { testTag = "fake_row_${item.id}" },
      )
    }
  }

  private val items = listOf(
    FakeItem(1L, "Apricot", "A"),
    FakeItem(2L, "Banana", "B"),
    FakeItem(3L, "Cherry", "C"),
  )
  private val byName = TabSort(SortKey.Name, SortDirection.Ascending)
  private val byDuration = TabSort(SortKey.Duration, SortDirection.Ascending)

  @Test
  fun empty_items_renders_empty_message_and_no_test_tag_root() {
    composeRule.setContent {
      Surface {
        LibraryTabRenderer(
          spec = FakeSpec,
          items = emptyList(),
          sort = byName,
          viewMode = ViewMode.List,
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          onItemClick = {},
        )
      }
    }
    // T.A.3 — empty-state copy now resolved from R.string.library_empty_songs.
    composeRule.onNodeWithText("No tracks yet.").assertIsDisplayed()
    assertEquals(0, composeRule.onAllNodesWithTag("fake_tab").fetchSemanticsNodes().size)
  }

  @Test
  fun list_mode_renders_one_row_per_item_with_outer_test_tag() {
    composeRule.setContent {
      Surface {
        LibraryTabRenderer(
          spec = FakeSpec,
          items = items,
          sort = byName,
          viewMode = ViewMode.List,
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          onItemClick = {},
        )
      }
    }
    composeRule.onNodeWithTag("fake_tab").assertExists()
    items.forEach {
      composeRule.onNodeWithTag("fake_row_${it.id}").assertExists()
    }
  }

  @Test
  fun tile_mode_renders_grid_not_rows() {
    composeRule.setContent {
      Surface {
        LibraryTabRenderer(
          spec = FakeSpec,
          items = items,
          sort = byName,
          viewMode = ViewMode.Tile,
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          onItemClick = {},
        )
      }
    }
    composeRule.onNodeWithTag("library_tile_grid").assertExists()
    assertTrue(composeRule.onAllNodesWithTag("fake_row_1").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun spec_without_tile_support_falls_back_to_list_in_tile_mode() {
    val listOnlySpec = object : TabSpec<FakeItem> by FakeSpec {
      override val supportsTileMode: Boolean = false
    }
    composeRule.setContent {
      Surface {
        LibraryTabRenderer(
          spec = listOnlySpec,
          items = items,
          sort = byName,
          viewMode = ViewMode.Tile,
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          onItemClick = {},
        )
      }
    }
    composeRule.onNodeWithTag("fake_row_1").assertExists()
    assertTrue(composeRule.onAllNodesWithTag("library_tile_grid").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun null_section_keys_skip_sticky_headers() {
    // FakeSpec returns null for sectionKey when sort is Duration —
    // engine should fall through to the un-grouped items() branch.
    composeRule.setContent {
      Surface {
        LibraryTabRenderer(
          spec = FakeSpec,
          items = items,
          sort = byDuration,
          viewMode = ViewMode.List,
          intelligentSorting = false,
          albumCoversMode = AlbumCoversMode.Off,
          onItemClick = {},
        )
      }
    }
    items.forEach {
      composeRule.onNodeWithTag("fake_row_${it.id}").assertExists()
    }
  }
}
