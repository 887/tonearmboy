package com.eight87.tonearmboy.ui.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.28.6 — pin per-tab persistence isolation for the new view-mode toggle.
 *
 *  - Defaults match the pre-D.28 shape: Songs / Artists / Genres /
 *    Playlists default to [ViewMode.List]; Albums to [ViewMode.Tile].
 *  - Toggling Songs to Tile does NOT flip Albums (the "no leak"
 *    assertion the user explicitly asked for).
 *  - Switching tabs reads each tab's saved mode independently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryViewModeToggleTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val repo = SettingsRepository(context)

  @Before
  fun resetState() = runTest {
    // DataStore is keyed by file name in-process, so explicitly reset
    // every per-tab key the test touches before each method runs.
    LibraryTab.entries.forEach { tab ->
      repo.setViewModeFor(tab, ViewMode.defaultFor(tab))
    }
  }

  @After
  fun tearDown() {
    context.filesDir.resolve("datastore").deleteRecursively()
  }

  @Test
  fun defaults_match_pre_d28_shape() = runTest {
    val modes = repo.viewModes.first()
    assertEquals(ViewMode.List, modes[LibraryTab.Songs])
    assertEquals(ViewMode.Tile, modes[LibraryTab.Albums])
    assertEquals(ViewMode.List, modes[LibraryTab.Artists])
    assertEquals(ViewMode.List, modes[LibraryTab.Genres])
    assertEquals(ViewMode.List, modes[LibraryTab.Playlists])
  }

  @Test
  fun toggling_songs_to_tile_does_not_leak_to_albums() = runTest {
    repo.setViewModeFor(LibraryTab.Songs, ViewMode.Tile)
    val modes = repo.viewModes.first()
    assertEquals(ViewMode.Tile, modes[LibraryTab.Songs])
    // Albums must still report its default Tile (unchanged).
    assertEquals(ViewMode.Tile, modes[LibraryTab.Albums])
    // The other defaults stay List.
    assertEquals(ViewMode.List, modes[LibraryTab.Artists])
    assertEquals(ViewMode.List, modes[LibraryTab.Genres])
    assertEquals(ViewMode.List, modes[LibraryTab.Playlists])
  }

  @Test
  fun toggling_albums_to_list_does_not_leak_to_songs() = runTest {
    repo.setViewModeFor(LibraryTab.Albums, ViewMode.List)
    val modes = repo.viewModes.first()
    assertEquals(ViewMode.List, modes[LibraryTab.Albums])
    // Songs unchanged on its default.
    assertEquals(ViewMode.List, modes[LibraryTab.Songs])
  }

  @Test
  fun every_tab_persists_independently_round_trip() = runTest {
    repo.setViewModeFor(LibraryTab.Songs, ViewMode.Tile)
    repo.setViewModeFor(LibraryTab.Albums, ViewMode.List)
    repo.setViewModeFor(LibraryTab.Artists, ViewMode.Tile)
    repo.setViewModeFor(LibraryTab.Genres, ViewMode.Tile)
    repo.setViewModeFor(LibraryTab.Playlists, ViewMode.Tile)
    val modes = repo.viewModes.first()
    assertEquals(ViewMode.Tile, modes[LibraryTab.Songs])
    assertEquals(ViewMode.List, modes[LibraryTab.Albums])
    assertEquals(ViewMode.Tile, modes[LibraryTab.Artists])
    assertEquals(ViewMode.Tile, modes[LibraryTab.Genres])
    assertEquals(ViewMode.Tile, modes[LibraryTab.Playlists])
  }

  @Test
  fun viewModeFor_single_tab_flow_emits_persisted_value() = runTest {
    assertEquals(ViewMode.List, repo.viewModeFor(LibraryTab.Songs).first())
    repo.setViewModeFor(LibraryTab.Songs, ViewMode.Tile)
    assertEquals(ViewMode.Tile, repo.viewModeFor(LibraryTab.Songs).first())
  }

  @Test
  fun toggle_helper_flips_each_direction() {
    assertEquals(ViewMode.Tile, ViewMode.List.toggle())
    assertEquals(ViewMode.List, ViewMode.Tile.toggle())
  }
}
