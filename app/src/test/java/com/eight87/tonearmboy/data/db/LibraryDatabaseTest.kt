package com.eight87.tonearmboy.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryDatabaseTest {

  private lateinit var db: LibraryDatabase
  private lateinit var trackDao: TrackDao
  private lateinit var libraryDao: LibraryDao
  private lateinit var albumDao: AlbumDao
  private lateinit var artistDao: ArtistDao
  private lateinit var genreDao: GenreDao
  private lateinit var playlistDao: PlaylistDao

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      LibraryDatabase::class.java,
    )
      .allowMainThreadQueries()
      .build()
    trackDao = db.trackDao()
    libraryDao = db.libraryDao()
    albumDao = db.albumDao()
    artistDao = db.artistDao()
    genreDao = db.genreDao()
    playlistDao = db.playlistDao()
  }

  @After fun tearDown() { db.close() }

  private fun track(
    id: Long,
    title: String = "Title $id",
    artist: String? = "Artist",
    album: String? = "Album",
    albumArtist: String? = null,
    genre: String? = "Rock",
    data: String = "/storage/emulated/0/Music/track-$id.mp3",
    year: Int? = 2020,
  ) = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = 180_000L,
    trackNumber = 1,
    year = year,
    genre = genre,
    data = data,
    dateAddedSeconds = 1_700_000_000L,
  )

  @Test fun `replaceAll persists tracks and rollups`() = runTest {
    val tracks = listOf(
      track(1L, title = "A song", album = "Album One", artist = "Alice", genre = "Rock"),
      track(2L, title = "B song", album = "Album One", artist = "Alice", genre = "Rock"),
      track(3L, title = "C song", album = "Album Two", artist = "Bob", genre = "Jazz"),
    )
    val albums = listOf(
      AlbumEntity(name = "Album One", artist = "Alice", year = 2020),
      AlbumEntity(name = "Album Two", artist = "Bob", year = 2021),
    )
    val artists = listOf(ArtistEntity(name = "Alice"), ArtistEntity(name = "Bob"))
    val genres = listOf(GenreEntity(name = "Rock"), GenreEntity(name = "Jazz"))

    libraryDao.replaceAll(tracks, albums, artists, genres)

    assertEquals(3, trackDao.observeAll().first().size)

    val albumsWithCounts = albumDao.observeAlbumsWithCounts().first()
    assertEquals(2, albumsWithCounts.size)
    val albumOne = albumsWithCounts.first { it.name == "Album One" }
    assertEquals(2, albumOne.trackCount)
    val albumTwo = albumsWithCounts.first { it.name == "Album Two" }
    assertEquals(1, albumTwo.trackCount)

    val artistsWithCounts = artistDao.observeArtistsWithCounts().first()
    val alice = artistsWithCounts.first { it.name == "Alice" }
    assertEquals(2, alice.trackCount)
    assertEquals(1, alice.albumCount)

    val genresWithCounts = genreDao.observeGenresWithCounts().first()
    assertEquals(2, genresWithCounts.size)
  }

  @Test fun `applyDelta removes gone tracks and adds new ones`() = runTest {
    val initial = listOf(track(1L), track(2L))
    libraryDao.replaceAll(initial, emptyList(), emptyList(), emptyList())
    assertEquals(2, trackDao.allIds().size)

    val nextSnapshot = listOf(track(2L, title = "renamed"), track(3L))
    libraryDao.applyDelta(
      removed = listOf(1L),
      upserted = nextSnapshot,
      albums = emptyList(),
      artists = emptyList(),
      genres = emptyList(),
    )
    val ids = trackDao.allIds().toSet()
    assertEquals(setOf(2L, 3L), ids)
    assertEquals("renamed", trackDao.getById(2L)?.title)
  }

  @Test fun `fts search matches title artist and album`() = runTest {
    val tracks = listOf(
      track(1L, title = "Walking on Sunshine", artist = "Katrina", album = "Singles"),
      track(2L, title = "Sunshine of Your Love", artist = "Cream", album = "Disraeli Gears"),
      track(3L, title = "Rainy Day", artist = "Various", album = "Compilation"),
    )
    libraryDao.replaceAll(tracks, emptyList(), emptyList(), emptyList())

    val sunshine = trackDao.searchFts("sunshine*").first().map { it.id }.toSet()
    assertEquals(setOf(1L, 2L), sunshine)

    val katrinaPrefix = trackDao.searchFts("katr*").first().map { it.id }.toSet()
    assertEquals(setOf(1L), katrinaPrefix)
  }

  @Test fun `like fallback finds by partial title`() = runTest {
    libraryDao.replaceAll(
      listOf(track(1L, title = "Café del Mar")),
      emptyList(), emptyList(), emptyList(),
    )
    val rows = trackDao.searchLike("%Café%").first()
    assertEquals(1, rows.size)
  }

  @Test fun `playlist crud roundtrip`() = runTest {
    libraryDao.replaceAll(
      listOf(track(1L), track(2L), track(3L)),
      emptyList(), emptyList(), emptyList(),
    )

    val playlistId = playlistDao.insert(
      PlaylistEntity(name = "Mix", createdAtSeconds = 1_700_000_000L),
    )
    assertNotNull(playlistDao.getById(playlistId))

    playlistDao.replaceJoins(playlistId, listOf(1L, 2L, 3L))
    val initial = playlistDao.observeTracks(playlistId).first().map { it.id }
    assertEquals(listOf(1L, 2L, 3L), initial)

    // reorder
    playlistDao.replaceJoins(playlistId, listOf(3L, 1L, 2L))
    val reordered = playlistDao.observeTracks(playlistId).first().map { it.id }
    assertEquals(listOf(3L, 1L, 2L), reordered)

    // counts
    val counts = playlistDao.observePlaylistsWithCounts().first()
    assertEquals(3, counts.first { it.id == playlistId }.trackCount)

    // cascade: deleting the playlist removes the joins
    playlistDao.delete(playlistId)
    assertNull(playlistDao.getById(playlistId))
    assertTrue(playlistDao.observeTracks(playlistId).first().isEmpty())
  }

  @Test fun `playlist track foreign key cascades on track delete`() = runTest {
    libraryDao.replaceAll(
      listOf(track(1L), track(2L)),
      emptyList(), emptyList(), emptyList(),
    )
    val playlistId = playlistDao.insert(
      PlaylistEntity(name = "Mix", createdAtSeconds = 1L),
    )
    playlistDao.replaceJoins(playlistId, listOf(1L, 2L))

    libraryDao.deleteTracksById(listOf(1L))
    val left = playlistDao.observeTracks(playlistId).first().map { it.id }
    assertEquals(listOf(2L), left)
  }
}
