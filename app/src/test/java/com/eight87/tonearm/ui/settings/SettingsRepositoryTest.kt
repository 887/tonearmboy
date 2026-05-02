package com.eight87.tonearm.ui.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-driven JVM tests for the unified [SettingsRepository].
 * Exercises the real DataStore Preferences file under
 * `Context.tonearmDataStore` so we know the keys, defaults, and
 * round-trip parsing all line up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val repo = SettingsRepository(context)

  @After
  fun tearDown() {
    context.filesDir.resolve("datastore").deleteRecursively()
  }

  @Test
  fun defaults_match_documented_snapshot() = runTest {
    val s = repo.snapshot.first()
    assertEquals(SettingsSnapshot.Default, s)
  }

  @Test
  fun toggles_round_trip() = runTest {
    repo.setIntelligentSorting(false)
    repo.setForceSquareCovers(true)
    repo.setBlackTheme(true)
    repo.setHeadsetAutoplay(true)
    val s = repo.snapshot.first()
    assertEquals(false, s.intelligentSorting)
    assertEquals(true, s.forceSquareCovers)
    assertEquals(true, s.blackTheme)
    assertEquals(true, s.headsetAutoplay)
  }

  @Test
  fun perTabSort_persistsIndependently() = runTest {
    repo.setTabSort(LibraryTab.Songs, TabSort(SortKey.Duration, SortDirection.Descending))
    repo.setTabSort(LibraryTab.Albums, TabSort(SortKey.Date, SortDirection.Ascending))
    val songs = repo.tabSort(LibraryTab.Songs).first()
    val albums = repo.tabSort(LibraryTab.Albums).first()
    val artists = repo.tabSort(LibraryTab.Artists).first()
    assertEquals(SortKey.Duration, songs.key)
    assertEquals(SortDirection.Descending, songs.direction)
    assertEquals(SortKey.Date, albums.key)
    assertEquals(TabSort.Default, artists)
  }

  @Test
  fun libraryTabsOrder_roundTrips_andTolerates_partial() = runTest {
    repo.setLibraryTabs(listOf(LibraryTab.Albums, LibraryTab.Songs))
    val order = repo.snapshot.first().libraryTabs
    // Visible tabs come first in order, missing tabs are appended.
    assertEquals(LibraryTab.Albums, order[0])
    assertEquals(LibraryTab.Songs, order[1])
    assertTrue(order.containsAll(LibraryTab.entries))
  }

  @Test
  fun autoDiscoverAlbumArt_defaultsOff_andRoundTrips() = runTest {
    val before = repo.snapshot.first()
    assertEquals(false, before.autoDiscoverAlbumArt)
    repo.setAutoDiscoverAlbumArt(true)
    val after = repo.snapshot.first()
    assertEquals(true, after.autoDiscoverAlbumArt)
  }

  @Test
  fun libraryTabsParser_handlesUnknownToken() {
    val parsed = SettingsRepository.parseLibraryTabs("Albums,Garbage,Songs")
    assertEquals(LibraryTab.Albums, parsed[0])
    assertEquals(LibraryTab.Songs, parsed[1])
    assertTrue(parsed.containsAll(LibraryTab.entries))
  }
}
