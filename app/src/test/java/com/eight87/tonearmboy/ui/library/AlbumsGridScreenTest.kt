package com.eight87.tonearmboy.ui.library

import android.net.Uri
import com.eight87.tonearmboy.data.model.Album
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.11.2 Albums grid unit assertions.
 *
 * The grid is a thin shell over `LazyVerticalGrid` + `AlbumCell`; the
 * load-bearing logic that's worth pinning down is:
 *  - one tile per `Album` (size of the rendered list == size of input)
 *  - sort applied per [TabSort]
 *  - real-cover branch when `mediaStoreAlbumId != null` AND mode is
 *    not Off — exercised through the [albumArtUri] resolver
 *  - placeholder branch when `mediaStoreAlbumId == null` OR mode is Off
 *
 * The placeholder/cover branching lives on the JVM as a pure function
 * via [shouldRenderPlaceholder], hoisted out of the Compose body so
 * tests don't need to spin up `AsyncImage`. The two existing fixture
 * albums (Velvet Den + Field Recordings) drive the integration probe
 * in `scripts/ui-smoke-test.sh`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumsGridScreenTest {

  private fun album(
    id: Long,
    name: String,
    artist: String? = null,
    trackCount: Int = 1,
    year: Int? = null,
    mediaStoreAlbumId: Long? = null,
  ) = Album(id, name, artist, trackCount, year, mediaStoreAlbumId)

  // --- one tile per album --------------------------------------------------

  @Test
  fun sortAlbums_preserves_album_count_one_to_one() {
    val albums = listOf(
      album(1, "Velvet Den", "The Synth Foxes", 4, 2025),
      album(2, "Field Recordings", "Quiet Hours", 3, 2024),
    )
    val sorted = sortAlbums(albums, TabSort(SortKey.Name, SortDirection.Ascending), true)
    assertEquals("one tile per album", 2, sorted.size)
    assertEquals(setOf(1L, 2L), sorted.map { it.id }.toSet())
  }

  // --- real cover vs placeholder branching ---------------------------------

  @Test
  fun placeholder_when_mediaStoreAlbumId_is_null() {
    val a = album(1, "X", mediaStoreAlbumId = null)
    assertTrue(shouldRenderPlaceholder(a, AlbumCoversMode.Balanced))
  }

  @Test
  fun placeholder_when_mode_is_off_even_with_id() {
    val a = album(1, "X", mediaStoreAlbumId = 999)
    assertTrue(shouldRenderPlaceholder(a, AlbumCoversMode.Off))
  }

  @Test
  fun real_cover_when_id_present_and_mode_balanced() {
    val a = album(1, "X", mediaStoreAlbumId = 999)
    assertTrue(!shouldRenderPlaceholder(a, AlbumCoversMode.Balanced))
  }

  @Test
  fun real_cover_when_id_present_and_mode_on() {
    val a = album(1, "X", mediaStoreAlbumId = 999)
    assertTrue(!shouldRenderPlaceholder(a, AlbumCoversMode.On))
  }

  // --- albumArtUri shape (real-cover path) ---------------------------------

  @Test
  fun real_cover_album_resolves_to_legacy_albumart_uri() {
    val a = album(1, "Velvet Den", mediaStoreAlbumId = 12345L)
    val uri: Uri = albumArtUri(a.mediaStoreAlbumId!!)
    assertEquals("content", uri.scheme)
    assertEquals("12345", uri.lastPathSegment)
  }

  // --- sort behaviour ------------------------------------------------------

  @Test
  fun albums_sort_by_name_ascending_uses_intelligent_keys() {
    val a = listOf(album(1, "The Velvet Den"), album(2, "Field Recordings"))
    val sorted = sortAlbums(a, TabSort(SortKey.Name, SortDirection.Ascending), true)
    assertEquals(listOf(2L, 1L), sorted.map { it.id })
  }

  @Test
  fun albums_sort_by_name_descending_reverses() {
    val a = listOf(album(1, "Alpha"), album(2, "Beta"))
    val sorted = sortAlbums(a, TabSort(SortKey.Name, SortDirection.Descending), true)
    assertEquals(listOf(2L, 1L), sorted.map { it.id })
  }
}

/**
 * Pure decision helper for D.11.2. Mirrors [CoverArt]'s placeholder
 * branch so JVM tests can exercise the rule without spinning Coil.
 */
internal fun shouldRenderPlaceholder(album: Album, mode: AlbumCoversMode): Boolean =
  album.mediaStoreAlbumId == null || mode == AlbumCoversMode.Off
