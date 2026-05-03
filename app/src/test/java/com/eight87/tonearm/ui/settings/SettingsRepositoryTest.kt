package com.eight87.tonearm.ui.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

  @Before
  fun resetState() = runTest {
    // The `preferencesDataStore` delegate caches the DataStore by name,
    // so deleting the on-disk file isn't enough — we explicitly reset
    // every key the suite touches before each test runs. This keeps
    // tests independent of declaration order and JUnit's per-JVM
    // method-ordering quirks.
    repo.setBlackTheme(false)
    repo.setRememberShuffle(false)
    repo.setIntelligentSorting(true)
    repo.setForceSquareCovers(false)
    repo.setHeadsetAutoplay(false)
    repo.setRewindBeforeSkipBack(true)
    repo.setRememberPause(false)
    repo.setAutoDiscoverAlbumArt(false)
    repo.setCustomBarAction(CustomBarAction.Default)
    repo.setCustomNotificationAction(CustomNotificationAction.Default)
    repo.setPauseOnRepeat(false)
    repo.setPlayFromLibrary(PlayFromLibrary.Default)
    repo.setPlayFromItemDetails(PlayFromItemDetails.Default)
    repo.setHideCollaborators(false)
    repo.setLibraryTabs(LibraryTab.DefaultOrder)
    LibraryTab.entries.forEach { repo.setTabSort(it, TabSort.Default) }
    repo.setTheme(ThemePreference.System)
    repo.setColorScheme(ColorScheme.Default)
    repo.setMultiValueSeparators(MultiValueSeparator.Default)
    repo.setMusicSourceUris(emptySet())
    repo.setMusicSourceMode(MusicSourceMode.Default)
    repo.setAutomaticReloading(false)
    repo.setBaseTheme(BaseTheme.Default)
    repo.setAlbumArtTintEnabled(true)
  }

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
  fun zz_d9a_pickers_and_toggles_default_correctly_and_round_trip() = runTest {
    val before = repo.snapshot.first()
    assertEquals(CustomBarAction.SkipNext, before.customBarAction)
    assertEquals(CustomNotificationAction.RepeatMode, before.customNotificationAction)
    assertEquals(false, before.pauseOnRepeat)
    assertEquals(PlayFromLibrary.AllSongs, before.playFromLibrary)
    assertEquals(PlayFromItemDetails.ShownItem, before.playFromItemDetails)
    assertEquals(false, before.hideCollaborators)

    repo.setCustomBarAction(CustomBarAction.ShuffleToggle)
    repo.setCustomNotificationAction(CustomNotificationAction.Shuffle)
    repo.setPauseOnRepeat(true)
    repo.setPlayFromLibrary(PlayFromLibrary.ItemOnly)
    repo.setPlayFromItemDetails(PlayFromItemDetails.Album)
    repo.setHideCollaborators(true)

    val after = repo.snapshot.first()
    assertEquals(CustomBarAction.ShuffleToggle, after.customBarAction)
    assertEquals(CustomNotificationAction.Shuffle, after.customNotificationAction)
    assertEquals(true, after.pauseOnRepeat)
    assertEquals(PlayFromLibrary.ItemOnly, after.playFromLibrary)
    assertEquals(PlayFromItemDetails.Album, after.playFromItemDetails)
    assertEquals(true, after.hideCollaborators)
  }

  @Test
  fun zz_hideCollaborators_flow_emits_independently() = runTest {
    assertEquals(false, repo.hideCollaborators.flow.first())
    repo.setHideCollaborators(true)
    assertEquals(true, repo.hideCollaborators.flow.first())
  }

  @Test
  fun libraryTabsParser_handlesUnknownToken() {
    val parsed = SettingsRepository.parseLibraryTabs("Albums,Garbage,Songs")
    assertEquals(LibraryTab.Albums, parsed[0])
    assertEquals(LibraryTab.Songs, parsed[1])
    assertTrue(parsed.containsAll(LibraryTab.entries))
  }

  @Test
  fun zz_multiValueSeparators_default_is_semicolon_and_slash() = runTest {
    val initial = repo.multiValueSeparators.flow.first()
    assertEquals(MultiValueSeparator.Default, initial)
    assertEquals(setOf(MultiValueSeparator.Semicolon, MultiValueSeparator.Slash), initial)
  }

  @Test
  fun zz_multiValueSeparators_round_trip_persists_user_selection() = runTest {
    val picked = setOf(
      MultiValueSeparator.Comma,
      MultiValueSeparator.Ampersand,
      MultiValueSeparator.Feat,
    )
    repo.setMultiValueSeparators(picked)
    assertEquals(picked, repo.multiValueSeparators.flow.first())
    assertEquals(picked, repo.snapshot.first().multiValueSeparators)
  }

  @Test
  fun zz_multiValueSeparators_empty_set_disables_all_splitting() = runTest {
    repo.setMultiValueSeparators(emptySet())
    assertEquals(emptySet<MultiValueSeparator>(), repo.multiValueSeparators.flow.first())
  }

  // R.B.1 — smoke tests for Setting<T>: prove the new abstraction
  // shares storage with the legacy mutators (writes either way are
  // visible from the other side) and survives the round trip.

  @Test
  fun zz_blackThemeSetting_round_trips_and_shares_storage_with_legacy() = runTest {
    assertEquals(false, repo.blackTheme.flow.first())
    repo.blackTheme.set(true)
    assertEquals(true, repo.blackTheme.flow.first())
    // legacy snapshot reader sees the new write
    assertEquals(true, repo.snapshot.first().blackTheme)
    // legacy mutator is visible to the new Setting handle
    repo.setBlackTheme(false)
    assertEquals(false, repo.blackTheme.flow.first())
  }

  @Test
  fun zz_colorSchemeSetting_round_trips_and_shares_storage_with_legacy() = runTest {
    assertEquals(ColorScheme.Default, repo.colorScheme.flow.first())
    repo.colorScheme.set(ColorScheme.Brand)
    assertEquals(ColorScheme.Brand, repo.colorScheme.flow.first())
    assertEquals(ColorScheme.Brand, repo.snapshot.first().colorScheme)
    repo.setColorScheme(ColorScheme.Dynamic)
    assertEquals(ColorScheme.Dynamic, repo.colorScheme.flow.first())
  }
}
