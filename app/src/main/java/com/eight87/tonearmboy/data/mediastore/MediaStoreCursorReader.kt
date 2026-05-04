package com.eight87.tonearmboy.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.data.model.ScannedTrack

/**
 * R.F.10 — pure cursor walk extracted from [MediaStoreScanner].
 * (Data-F5.) Returns a list of pre-ReplayGain stubs (each a
 * [ScannedTrack] with `replayGain*` fields null) plus the per-track
 * content URI used by the second-pass enricher. No coroutine work,
 * no runBlocking — just the MediaStore query + the cursor-to-stub
 * conversion.
 */
@UnstableApi
internal class MediaStoreCursorReader(private val context: Context) {

  data class Stub(val track: ScannedTrack, val uri: Uri)

  fun read(separators: Set<String>, scopePathPrefixes: Set<String>): List<Stub> {
    if (!MediaStorePermissions.hasAudioPermission(context)) return emptyList()
    val genreById = readGenreMembership()
    val resolver = context.contentResolver

    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.ALBUM_ARTIST,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.TRACK,
      MediaStore.Audio.Media.YEAR,
      MediaStore.Audio.Media.DATA,
      MediaStore.Audio.Media.DATE_ADDED,
      MediaStore.Audio.Media.ALBUM_ID,
    )

    val (selection, selectionArgs) = if (scopePathPrefixes.isEmpty()) {
      "${MediaStore.Audio.Media.IS_MUSIC} != 0" to null
    } else {
      val likeClauses = scopePathPrefixes.joinToString(" OR ") { "${MediaStore.Audio.Media.DATA} LIKE ?" }
      val args = scopePathPrefixes.map { prefix ->
        val trimmed = prefix.trimEnd('/')
        "$trimmed/%"
      }.toTypedArray()
      "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ($likeClauses)" to args
    }

    return resolver.query(
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      projection,
      selection,
      selectionArgs,
      "${MediaStore.Audio.Media.TITLE} ASC",
    )?.use { cursor -> buildStubs(cursor, genreById, separators) } ?: emptyList()
  }

  fun uriFor(trackId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)

  private fun buildStubs(
    cursor: Cursor,
    genreById: Map<Long, String>,
    separators: Set<String>,
  ): List<Stub> {
    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
    val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
    val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
    val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
    val albumArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
    val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
    val trackIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
    val yearIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
    val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
    val dateAddedIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
    val albumIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

    val total = cursor.count.coerceAtLeast(0)
    val stubs = ArrayList<Stub>(total)
    while (cursor.moveToNext()) {
      val id = cursor.getLong(idIdx)
      val rawArtist = cursor.getStringOrNull(artistIdx)
      val rawAlbumArtist = cursor.getStringOrNull(albumArtistIdx)
      val rawGenre = genreById[id]
      val artistsSplit = MultiValueSplitter.split(rawArtist, separators)
      val albumArtistsSplit = MultiValueSplitter.split(rawAlbumArtist, separators)
      val genresSplit = MultiValueSplitter.split(rawGenre, separators)
      val primaryArtist = artistsSplit.firstOrNull() ?: rawArtist
      val primaryAlbumArtist = albumArtistsSplit.firstOrNull() ?: rawAlbumArtist
      val primaryGenre = genresSplit.firstOrNull() ?: rawGenre
      val title = cursor.getString(titleIdx) ?: ""

      stubs += Stub(
        track = ScannedTrack(
          id = id,
          title = title,
          artist = primaryArtist,
          album = cursor.getStringOrNull(albumIdx),
          albumArtist = primaryAlbumArtist,
          durationMs = cursor.getLong(durationIdx),
          trackNumber = cursor.getIntOrNull(trackIdx),
          year = cursor.getIntOrNull(yearIdx),
          genre = primaryGenre,
          data = cursor.getString(dataIdx) ?: "",
          dateAddedSeconds = cursor.getLong(dateAddedIdx),
          mediaStoreAlbumId = if (cursor.isNull(albumIdIdx)) null else cursor.getLong(albumIdIdx),
          additionalArtists = artistsSplit.drop(1),
          additionalAlbumArtists = albumArtistsSplit.drop(1),
          additionalGenres = genresSplit.drop(1),
        ),
        uri = uriFor(id),
      )
    }
    return stubs
  }

  /**
   * Build an [audioId -> genreName] map by walking every Genre and the
   * Members query under it. The MediaStore.Audio.Media.GENRE column is
   * deprecated in API 30+, so the per-genre membership join is the
   * canonical replacement.
   */
  private fun readGenreMembership(): Map<Long, String> {
    val resolver = context.contentResolver
    val out = HashMap<Long, String>()

    val genresCursor = resolver.query(
      MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
      arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
      null,
      null,
      null,
    ) ?: return out

    genresCursor.use { gc ->
      val gIdIdx = gc.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
      val gNameIdx = gc.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
      while (gc.moveToNext()) {
        val genreId = gc.getLong(gIdIdx)
        val genreName = gc.getString(gNameIdx) ?: continue
        val membersUri =
          MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
        resolver.query(
          membersUri,
          arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
          null,
          null,
          null,
        )?.use { mc ->
          val audioIdIdx = mc.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
          while (mc.moveToNext()) {
            val audioId = mc.getLong(audioIdIdx)
            out.putIfAbsent(audioId, genreName)
          }
        }
      }
    }
    return out
  }

  private fun Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

  private fun Cursor.getIntOrNull(index: Int): Int? =
    if (isNull(index)) null else getInt(index)
}
