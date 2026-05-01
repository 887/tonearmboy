package com.eight87.tonearm.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.eight87.tonearm.data.model.Track

/**
 * Pulls audio metadata out of [MediaStore]. Returns domain [Track]
 * instances; callers never see a raw [Cursor].
 *
 * Genre is fetched via [MediaStore.Audio.Genres.Members] rather than
 * the column on the main audio table, which has been deprecated since
 * API 30. We do one query for the audio table and one supplementary
 * query per genre for the membership join, then merge the results.
 *
 * The scanner does no caching of its own — it always returns the
 * authoritative MediaStore snapshot. Caching is the repository's job.
 */
class MediaStoreScanner(private val context: Context) {

  /**
   * Snapshot every audio file MediaStore knows about. Returns an empty
   * list if the audio permission is missing or MediaStore returns no
   * cursor.
   */
  fun scanTracks(): List<Track> {
    if (!MediaStorePermissions.hasAudioPermission(context)) {
      Log.w(TAG, "scanTracks: no audio permission, skipping")
      return emptyList()
    }
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
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    return resolver.query(
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      projection,
      selection,
      /* selectionArgs = */ null,
      "${MediaStore.Audio.Media.TITLE} ASC",
    )?.use { cursor ->
      buildTracks(cursor, genreById)
    } ?: emptyList()
  }

  /** The content URI for an individual audio item, useful for callers. */
  fun uriFor(trackId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)

  private fun buildTracks(cursor: Cursor, genreById: Map<Long, String>): List<Track> {
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

    val out = ArrayList<Track>(cursor.count.coerceAtLeast(0))
    while (cursor.moveToNext()) {
      val id = cursor.getLong(idIdx)
      out += Track(
        id = id,
        title = cursor.getString(titleIdx) ?: "",
        artist = cursor.getStringOrNull(artistIdx),
        album = cursor.getStringOrNull(albumIdx),
        albumArtist = cursor.getStringOrNull(albumArtistIdx),
        durationMs = cursor.getLong(durationIdx),
        trackNumber = cursor.getIntOrNull(trackIdx),
        year = cursor.getIntOrNull(yearIdx),
        genre = genreById[id],
        data = cursor.getString(dataIdx) ?: "",
        dateAddedSeconds = cursor.getLong(dateAddedIdx),
      )
    }
    return out
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
            // First write wins; tracks normally only belong to one genre.
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

  companion object {
    private const val TAG = "tonearm-scanner"
  }
}
