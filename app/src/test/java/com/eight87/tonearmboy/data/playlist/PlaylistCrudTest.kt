package com.eight87.tonearmboy.data.playlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.tonearmboy.data.LibraryRepository
import com.eight87.tonearmboy.data.db.AlbumEntity
import com.eight87.tonearmboy.data.db.ArtistEntity
import com.eight87.tonearmboy.data.db.GenreEntity
import com.eight87.tonearmboy.data.db.LibraryDatabase
import com.eight87.tonearmboy.data.db.TrackEntity
import com.eight87.tonearmboy.data.mediastore.MediaStoreScanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.15.4 — Playlist CRUD end-to-end against a real Room DB.
 *
 * Covers:
 *  - D.15.4.1 createPlaylist actually writes a row
 *  - D.15.4.2 observePlaylists emits the row after createPlaylist
 *  - D.15.4.3 observePlaylistTracks reflects addTrackToPlaylist
 *  - D.15.4.4 renamePlaylist updates the row; deletePlaylist cascades
 *    the join rows (per the FK on PlaylistTrackEntity).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistCrudTest {

  private lateinit var db: LibraryDatabase
  private lateinit var repository: LibraryRepository

  @Before fun setUp() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    db = Room.inMemoryDatabaseBuilder(ctx, LibraryDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = LibraryRepository(
      context = ctx,
      scanner = MediaStoreScanner(ctx),
      db = db,
      externalScope = kotlinx.coroutines.MainScope(),
      scanConfig = com.eight87.tonearmboy.data.EmptyScanConfigSource,
    )
  }

  @After fun tearDown() {
    repository.shutdown()
    db.close()
  }

  private suspend fun seedTracks(count: Int): List<Long> {
    val rows = (1..count).map { idx ->
      TrackEntity(
        id = idx.toLong(),
        title = "Track $idx",
        artist = "Test",
        album = "Album",
        albumArtist = null,
        durationMs = 180_000L,
        trackNumber = idx,
        year = 2024,
        genre = "Synthwave",
        data = "/audio/$idx.flac",
        dateAddedSeconds = 0L,
      )
    }
    db.libraryDao().replaceAll(
      rows,
      listOf(AlbumEntity(name = "Album", artist = "Test", year = 2024)),
      listOf(ArtistEntity(name = "Test")),
      listOf(GenreEntity(name = "Synthwave")),
    )
    return rows.map { it.id }
  }

  @Test fun create_persists_and_observes() = runTest {
    val id = repository.createPlaylist("Late night drives", nowSeconds = 100L)
    assertTrue(id > 0)
    val playlists = repository.observePlaylists().first()
    assertEquals(1, playlists.size)
    assertEquals("Late night drives", playlists[0].name)
    assertEquals(0, playlists[0].trackCount)
  }

  @Test fun add_tracks_updates_track_count_and_join_order() = runTest {
    val ids = seedTracks(3)
    val playlistId = repository.createPlaylist("Morning queue")
    repository.addTrackToPlaylist(playlistId, ids[2])
    repository.addTrackToPlaylist(playlistId, ids[0])
    val tracks = repository.observePlaylistTracks(playlistId).first()
    assertEquals(2, tracks.size)
    // Insertion order is preserved by `position`.
    assertEquals(ids[2], tracks[0].id)
    assertEquals(ids[0], tracks[1].id)
    val withCount = repository.observePlaylists().first().first { it.id == playlistId }
    assertEquals(2, withCount.trackCount)
  }

  @Test fun rename_updates_playlist_name() = runTest {
    val id = repository.createPlaylist("Old name")
    repository.renamePlaylist(id, "New name")
    val rows = repository.observePlaylists().first()
    assertEquals("New name", rows.first { it.id == id }.name)
  }

  @Test fun rename_preserves_track_order() = runTest {
    val ids = seedTracks(2)
    val id = repository.createPlaylist("Original")
    repository.addTrackToPlaylist(id, ids[0])
    repository.addTrackToPlaylist(id, ids[1])
    repository.renamePlaylist(id, "Renamed")
    val tracks = repository.observePlaylistTracks(id).first()
    assertEquals(listOf(ids[0], ids[1]), tracks.map { it.id })
  }

  @Test fun delete_removes_playlist_and_cascades_joins() = runTest {
    val ids = seedTracks(2)
    val pid = repository.createPlaylist("To delete")
    repository.addTrackToPlaylist(pid, ids[0])
    repository.addTrackToPlaylist(pid, ids[1])
    // Sanity: the joins exist before the delete.
    assertEquals(2, db.playlistDao().rawJoins(pid).size)
    repository.deletePlaylist(pid)
    val rows = repository.observePlaylists().first()
    assertTrue(rows.none { it.id == pid })
    // FK-cascade removes the join rows.
    assertEquals(0, db.playlistDao().rawJoins(pid).size)
    assertNull(db.playlistDao().getById(pid))
  }

  @Test fun reorder_updates_track_positions() = runTest {
    val ids = seedTracks(3)
    val pid = repository.createPlaylist("Reorder me")
    ids.forEach { repository.addTrackToPlaylist(pid, it) }
    val original = repository.observePlaylistTracks(pid).first().map { it.id }
    val reversed = original.reversed()
    repository.reorderPlaylist(pid, reversed)
    val after = repository.observePlaylistTracks(pid).first().map { it.id }
    assertEquals(reversed, after)
    assertNotEquals(original, after)
  }

  @Test fun creating_two_playlists_with_same_name_aborts_the_duplicate() = runTest {
    val first = repository.createPlaylist("Unique")
    assertTrue(first > 0)
    var threw = false
    try {
      repository.createPlaylist("Unique")
    } catch (t: Throwable) {
      threw = true
    }
    assertTrue("duplicate playlist name should abort", threw)
  }
}
