package com.eight87.tonearm.data.playlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.db.AlbumEntity
import com.eight87.tonearm.data.db.ArtistEntity
import com.eight87.tonearm.data.db.GenreEntity
import com.eight87.tonearm.data.db.LibraryDatabase
import com.eight87.tonearm.data.db.TrackEntity
import com.eight87.tonearm.data.mediastore.MediaStoreScanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class M3UImporterTest {

  @get:Rule val tmp = TemporaryFolder()

  private lateinit var db: LibraryDatabase
  private lateinit var repository: LibraryRepository

  @Before fun setUp() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    db = Room.inMemoryDatabaseBuilder(ctx, LibraryDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val scanner = MediaStoreScanner(ctx)
    repository = LibraryRepository(
      context = ctx,
      scanner = scanner,
      db = db,
      externalScope = kotlinx.coroutines.MainScope(),
      scanConfig = com.eight87.tonearm.data.EmptyScanConfigSource,
    )
  }

  @After fun tearDown() {
    repository.shutdown()
    db.close()
  }

  private suspend fun seedTracks(vararg paths: String): List<Long> {
    val rows = paths.mapIndexed { idx, path ->
      TrackEntity(
        id = (idx + 1).toLong(),
        title = "Track $idx",
        artist = "Test",
        album = "Album",
        albumArtist = null,
        durationMs = 180_000L,
        trackNumber = idx,
        year = 2024,
        genre = "Rock",
        data = path,
        dateAddedSeconds = 0L,
      )
    }
    db.libraryDao().replaceAll(
      rows,
      listOf(AlbumEntity(name = "Album", artist = "Test", year = 2024)),
      listOf(ArtistEntity(name = "Test")),
      listOf(GenreEntity(name = "Rock")),
    )
    return rows.map { it.id }
  }

  @Test fun `parseEntries skips comments blanks and EXTINF`() {
    val importer = M3UImporter(repository, db)
    val entries = importer.parseEntries(
      listOf(
        "#EXTM3U",
        "",
        "#EXTINF:180,Artist - Title",
        "/abs/path/file.mp3",
        "  ",
        "relative/song.flac",
      )
    )
    assertEquals(listOf("/abs/path/file.mp3", "relative/song.flac"), entries)
  }

  @Test fun `resolvePath joins relative entries against base dir`() {
    val importer = M3UImporter(repository, db)
    val base = tmp.newFolder("playlists")
    val resolved = importer.resolvePath("song.flac", base)
    assertTrue(resolved!!.endsWith("/song.flac"))
    assertTrue(resolved.contains(base.canonicalPath))
  }

  @Test fun `resolvePath returns null for http urls`() {
    val importer = M3UImporter(repository, db)
    assertEquals(null, importer.resolvePath("http://example.com/stream", null))
    assertEquals(null, importer.resolvePath("https://example.com/stream", null))
  }

  @Test fun `import matches by absolute path and orders by file order`() = runTest {
    val musicDir = tmp.newFolder("music")
    val a = File(musicDir, "a.mp3")
    val b = File(musicDir, "b.mp3")
    val c = File(musicDir, "c.mp3")
    seedTracks(a.canonicalPath, b.canonicalPath, c.canonicalPath)

    val playlistFile = tmp.newFile("favorites.m3u")
    playlistFile.writeText(
      """
      #EXTM3U
      ${b.canonicalPath}
      ${a.canonicalPath}
      ${c.canonicalPath}
      """.trimIndent()
    )

    val importer = M3UImporter(repository, db)
    val result = importer.import(playlistFile, "Favorites")

    assertEquals(3, result.matchedCount)
    assertTrue(result.skipped.isEmpty())

    val ids = db.playlistDao().observeTracks(result.playlistId).first().map { it.id }
    // Matches the order the entries appeared in the M3U file.
    assertEquals(listOf(2L, 1L, 3L), ids)
  }

  @Test fun `import resolves relative paths against the m3u directory`() = runTest {
    val musicDir = tmp.newFolder("music")
    val song = File(musicDir, "song.mp3")
    seedTracks(song.canonicalPath)

    val playlistFile = File(musicDir, "list.m3u")
    playlistFile.writeText("song.mp3\n")

    val importer = M3UImporter(repository, db)
    val result = importer.import(playlistFile, "Rel")
    assertEquals(1, result.matchedCount)
    assertTrue(result.skipped.isEmpty())
  }

  @Test fun `import collects unmatched entries into skipped`() = runTest {
    val musicDir = tmp.newFolder("music")
    val song = File(musicDir, "song.mp3")
    seedTracks(song.canonicalPath)

    val playlistFile = tmp.newFile("list.m3u")
    playlistFile.writeText(
      """
      ${song.canonicalPath}
      /no/such/file.mp3
      ${File(musicDir, "missing.mp3").canonicalPath}
      """.trimIndent()
    )
    val importer = M3UImporter(repository, db)
    val result = importer.import(playlistFile, "Mixed")
    assertEquals(1, result.matchedCount)
    assertEquals(2, result.skipped.size)
  }
}
