package com.eight87.tonearm.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.playback.replaygain.ReplayGainTagReader
import com.eight87.tonearm.playback.replaygain.ReplayGainTags

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
@UnstableApi
class MediaStoreScanner(
  private val context: Context,
  private val replayGainReader: ReplayGainTagReader = ReplayGainTagReader(context),
) {

  /**
   * Snapshot every audio file MediaStore knows about. Returns an empty
   * list if the audio permission is missing or MediaStore returns no
   * cursor.
   *
   * D.9b.1: every track is also probed for `REPLAYGAIN_TRACK_*` and
   * `REPLAYGAIN_ALBUM_*` tags via [ReplayGainTagReader]. The probe
   * is best-effort — a missing or malformed tag yields a null gain
   * value, never an exception that breaks the scan.
   *
   * D.9d.1: when [scopePathPrefixes] is non-empty, the scan is
   * restricted to MediaStore rows whose `DATA` column begins with one
   * of the supplied filesystem prefixes (e.g. `/storage/emulated/0/Music`
   * derived from a SAF tree URI). When empty (the legacy default) the
   * scan covers every audio file MediaStore knows about, matching the
   * pre-D.9d.1 behaviour.
   */
  fun scanTracks(
    separators: Set<String> = emptySet(),
    scopePathPrefixes: Set<String> = emptySet(),
  ): List<Track> {
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
      MediaStore.Audio.Media.ALBUM_ID,
    )

    // Compose the WHERE clause. Always include the "is music" filter;
    // when sources are configured, AND it with an OR-chain of `DATA
    // LIKE ?` predicates. Each prefix maps to a single bind parameter
    // ending in `%` so the SQLite query plan can use the standard
    // prefix index on DATA.
    val (selection, selectionArgs) = if (scopePathPrefixes.isEmpty()) {
      "${MediaStore.Audio.Media.IS_MUSIC} != 0" to null
    } else {
      val likeClauses = scopePathPrefixes.joinToString(" OR ") { "${MediaStore.Audio.Media.DATA} LIKE ?" }
      val args = scopePathPrefixes.map { prefix ->
        // Normalize trailing slash; the LIKE pattern needs exactly one
        // separator before the wildcard so `/Music` matches `/Music/a.mp3`
        // but NOT `/MusicVideos/a.mp3`.
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
    )?.use { cursor ->
      buildTracks(cursor, genreById, separators)
    } ?: emptyList()
  }

  /** The content URI for an individual audio item, useful for callers. */
  fun uriFor(trackId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)

  private fun buildTracks(
    cursor: Cursor,
    genreById: Map<Long, String>,
    separators: Set<String>,
  ): List<Track> {
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

    val out = ArrayList<Track>(cursor.count.coerceAtLeast(0))
    while (cursor.moveToNext()) {
      val id = cursor.getLong(idIdx)
      val trackUri = uriFor(id)
      val rg: ReplayGainTags = try {
        replayGainReader.read(trackUri)
      } catch (t: Throwable) {
        Log.w(TAG, "ReplayGain read failed for id=$id: ${t.message}")
        ReplayGainTags.Empty
      }
      val rawArtist = cursor.getStringOrNull(artistIdx)
      val rawAlbumArtist = cursor.getStringOrNull(albumArtistIdx)
      val rawGenre = genreById[id]

      val artistsSplit = MultiValueSplitter.split(rawArtist, separators)
      val albumArtistsSplit = MultiValueSplitter.split(rawAlbumArtist, separators)
      val genresSplit = MultiValueSplitter.split(rawGenre, separators)

      // The primary value is the *first* split value (used for display);
      // the rest are folded into the rollup-derivation step.
      val primaryArtist = artistsSplit.firstOrNull() ?: rawArtist
      val primaryAlbumArtist = albumArtistsSplit.firstOrNull() ?: rawAlbumArtist
      val primaryGenre = genresSplit.firstOrNull() ?: rawGenre

      out += Track(
        id = id,
        title = cursor.getString(titleIdx) ?: "",
        artist = primaryArtist,
        album = cursor.getStringOrNull(albumIdx),
        albumArtist = primaryAlbumArtist,
        durationMs = cursor.getLong(durationIdx),
        trackNumber = cursor.getIntOrNull(trackIdx),
        year = cursor.getIntOrNull(yearIdx),
        genre = primaryGenre,
        data = cursor.getString(dataIdx) ?: "",
        dateAddedSeconds = cursor.getLong(dateAddedIdx),
        replayGainTrackDb = rg.trackGainDb,
        replayGainTrackPeak = rg.trackPeak,
        replayGainAlbumDb = rg.albumGainDb,
        replayGainAlbumPeak = rg.albumPeak,
        mediaStoreAlbumId = if (cursor.isNull(albumIdIdx)) null else cursor.getLong(albumIdIdx),
        additionalArtists = artistsSplit.drop(1),
        additionalAlbumArtists = albumArtistsSplit.drop(1),
        additionalGenres = genresSplit.drop(1),
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
