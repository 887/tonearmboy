package com.eight87.tonearmboy.data.delete

import android.content.ContentUris
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.tonearmboy.data.LibraryRepository
import com.eight87.tonearmboy.data.db.AlbumEntity
import com.eight87.tonearmboy.data.db.ArtistEntity
import com.eight87.tonearmboy.data.db.GenreEntity
import com.eight87.tonearmboy.data.db.LibraryDatabase
import com.eight87.tonearmboy.data.db.TrackEntity
import com.eight87.tonearmboy.data.mediastore.MediaStoreScanner
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
 * Phase F.4 — verify [LibraryRepository.onTracksDeleted] removes the
 * track rows the system has just deleted, lets the FK-cascade clean
 * up the `playlist_tracks` join rows, and is a no-op when given an
 * empty list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryRepositoryDeleteTest {

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

  private fun audioUri(id: Long) =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

  @Test fun on_tracks_deleted_removes_rows() = runTest {
    val ids = seedTracks(3)
    repository.onTracksDeleted(listOf(audioUri(ids[0]), audioUri(ids[2])))
    val remaining = db.trackDao().allIds()
    assertEquals(listOf(ids[1]), remaining)
  }

  @Test fun on_tracks_deleted_cascades_to_playlist_joins() = runTest {
    val ids = seedTracks(2)
    val pid = repository.createPlaylist("Doomed mix")
    repository.addTrackToPlaylist(pid, ids[0])
    repository.addTrackToPlaylist(pid, ids[1])
    assertEquals(2, db.playlistDao().rawJoins(pid).size)

    repository.onTracksDeleted(listOf(audioUri(ids[0])))

    // Track row gone + the join row that referenced it cascaded away.
    val survivingTracks = db.trackDao().allIds()
    assertEquals(listOf(ids[1]), survivingTracks)
    assertEquals(1, db.playlistDao().rawJoins(pid).size)
    assertEquals(ids[1], db.playlistDao().rawJoins(pid).first().trackId)
  }

  @Test fun on_tracks_deleted_with_empty_list_is_no_op() = runTest {
    val ids = seedTracks(2)
    repository.onTracksDeleted(emptyList())
    assertEquals(ids.toSet(), db.trackDao().allIds().toSet())
  }

  @Test fun on_tracks_deleted_ignores_unknown_ids() = runTest {
    val ids = seedTracks(2)
    // URIs that look right but reference rows that are not in the DB
    // — should be a tolerated no-op.
    repository.onTracksDeleted(listOf(audioUri(9_999L)))
    assertEquals(ids.toSet(), db.trackDao().allIds().toSet())
    assertTrue(db.trackDao().allIds().isNotEmpty())
  }
}
