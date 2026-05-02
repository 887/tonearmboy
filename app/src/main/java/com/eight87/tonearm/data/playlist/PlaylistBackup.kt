package com.eight87.tonearm.data.playlist

import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.model.Playlist
import com.eight87.tonearm.data.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Phase H.5 — playlist export / import.
 *
 * On-disk format is a single JSON envelope: a stable schema version, a
 * timestamp, and a list of playlists where each track is identified by
 * its `(title, artist, album)` triple instead of its Room id or
 * MediaStore id (those don't survive across devices / re-scans).
 *
 * This module is pure data plumbing — file I/O lives in the UI layer
 * via `ActivityResultContracts.CreateDocument` / `OpenDocument`. The
 * helpers here only deal with String <-> envelope transformations and
 * matching imported tracks against the live library.
 */

/** Top-level envelope; bump [version] if the schema breaks. */
@Serializable
data class PlaylistBackupEnvelope(
  val version: Int = CURRENT_VERSION,
  val exportedAt: String,
  val playlists: List<PlaylistBackup>,
) {
  companion object {
    const val CURRENT_VERSION = 1
  }
}

@Serializable
data class PlaylistBackup(
  val name: String,
  val tracks: List<TrackRef>,
)

/**
 * Track identity used in the backup. The triple `(title, artist, album)`
 * is the user-visible identity that survives re-scans. [durationMs] is
 * advisory — kept for diagnostics, not used in matching.
 */
@Serializable
data class TrackRef(
  val title: String,
  val artist: String? = null,
  val album: String? = null,
  val durationMs: Long? = null,
)

/** Plumbing-level result of an import resolve pass. */
data class PlaylistImportResolution(
  /** Playlist name -> resolved track ids (in the original backup order). */
  val resolved: Map<String, List<Long>>,
  /** Total tracks across the envelope that couldn't be matched. */
  val unmatchedCount: Int,
)

/**
 * Decision the user makes when an imported playlist's name collides
 * with an existing one in the library.
 */
enum class PlaylistImportCollisionPolicy {
  /** Drop the existing playlist, replace with imported tracks. */
  Overwrite,
  /** Keep existing tracks, append imported tracks not already present. */
  Merge,
  /** Skip importing playlists with name collisions. */
  Cancel,
}

object PlaylistBackupCodec {
  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
  }

  fun encode(envelope: PlaylistBackupEnvelope): String =
    json.encodeToString(PlaylistBackupEnvelope.serializer(), envelope)

  fun decode(raw: String): PlaylistBackupEnvelope =
    json.decodeFromString(PlaylistBackupEnvelope.serializer(), raw)

  /** Build the suggested file name for a `CreateDocument` SAF dialog. */
  fun defaultFileName(now: Date = Date()): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    return "tonearm-playlists-${fmt.format(now)}.json"
  }

  fun isoTimestamp(now: Date = Date()): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(now)
  }
}

/**
 * Pure resolver: given an envelope and the current library snapshot,
 * map each `TrackRef` to a real `Track.id` via case-insensitive
 * `(title, artist)` match with `album` as the tiebreaker. Tracks that
 * don't match anything are dropped from the resolved list and counted
 * in [PlaylistImportResolution.unmatchedCount].
 */
internal fun resolvePlaylistImport(
  envelope: PlaylistBackupEnvelope,
  libraryTracks: List<Track>,
): PlaylistImportResolution {
  // Index by (lowercased title, lowercased artist or "") for the
  // primary match, then collect album candidates lazily.
  val byTitleArtist: Map<Pair<String, String>, List<Track>> =
    libraryTracks.groupBy { it.title.lowercase() to (it.artist?.lowercase().orEmpty()) }
  val byTitle: Map<String, List<Track>> =
    libraryTracks.groupBy { it.title.lowercase() }

  var unmatched = 0
  val resolved = LinkedHashMap<String, List<Long>>()
  for (playlist in envelope.playlists) {
    val ids = ArrayList<Long>(playlist.tracks.size)
    for (ref in playlist.tracks) {
      val match = matchTrackRef(ref, byTitleArtist, byTitle)
      if (match == null) {
        unmatched++
      } else {
        ids += match.id
      }
    }
    resolved[playlist.name] = ids
  }
  return PlaylistImportResolution(resolved = resolved, unmatchedCount = unmatched)
}

/**
 * Match precedence:
 *  1. Exact (title, artist) lowercase match. If multiple candidates,
 *     prefer the one with the same album; otherwise pick the first.
 *  2. Title-only lowercase match. Same album-tiebreak rule.
 *  3. No match.
 */
private fun matchTrackRef(
  ref: TrackRef,
  byTitleArtist: Map<Pair<String, String>, List<Track>>,
  byTitle: Map<String, List<Track>>,
): Track? {
  val titleKey = ref.title.lowercase()
  val artistKey = ref.artist?.lowercase().orEmpty()
  val albumKey = ref.album?.lowercase()

  byTitleArtist[titleKey to artistKey]?.let { candidates ->
    return preferAlbum(candidates, albumKey) ?: candidates.firstOrNull()
  }
  byTitle[titleKey]?.let { candidates ->
    return preferAlbum(candidates, albumKey) ?: candidates.firstOrNull()
  }
  return null
}

private fun preferAlbum(candidates: List<Track>, albumKey: String?): Track? {
  if (albumKey == null) return null
  return candidates.firstOrNull { it.album?.lowercase() == albumKey }
}

/**
 * Build an envelope from the current library state. Reads the live
 * playlist list once + each playlist's track list once.
 */
suspend fun buildPlaylistBackup(repository: LibraryRepository): PlaylistBackupEnvelope {
  val playlists: List<Playlist> = repository.observePlaylists().first()
  val backups = playlists.map { p ->
    val tracks = repository.observePlaylistTracks(p.id).first()
    PlaylistBackup(
      name = p.name,
      tracks = tracks.map { t ->
        TrackRef(
          title = t.title,
          artist = t.artist,
          album = t.album,
          durationMs = t.durationMs,
        )
      },
    )
  }
  return PlaylistBackupEnvelope(
    exportedAt = PlaylistBackupCodec.isoTimestamp(),
    playlists = backups,
  )
}

/**
 * Apply [resolution] against the library, persisting playlists per
 * [collisionPolicy]. Returns the import summary the UI surfaces in a
 * snackbar.
 */
suspend fun applyPlaylistImport(
  repository: LibraryRepository,
  envelope: PlaylistBackupEnvelope,
  resolution: PlaylistImportResolution,
  collisionPolicy: PlaylistImportCollisionPolicy,
): PlaylistImportSummary {
  val existing: List<Playlist> = repository.observePlaylists().first()
  val byName = existing.associateBy { it.name }

  var imported = 0
  var skipped = 0
  for ((name, ids) in resolution.resolved) {
    val collision = byName[name]
    if (collision != null) {
      when (collisionPolicy) {
        PlaylistImportCollisionPolicy.Cancel -> {
          skipped++
          continue
        }
        PlaylistImportCollisionPolicy.Overwrite -> {
          repository.deletePlaylist(collision.id)
          val newId = repository.createPlaylist(name)
          for (trackId in ids) repository.addTrackToPlaylist(newId, trackId)
        }
        PlaylistImportCollisionPolicy.Merge -> {
          val existingTrackIds = repository
            .observePlaylistTracks(collision.id).first().map { it.id }.toSet()
          for (trackId in ids) {
            if (trackId !in existingTrackIds) {
              repository.addTrackToPlaylist(collision.id, trackId)
            }
          }
        }
      }
    } else {
      val newId = repository.createPlaylist(name)
      for (trackId in ids) repository.addTrackToPlaylist(newId, trackId)
    }
    imported++
  }

  return PlaylistImportSummary(
    importedCount = imported,
    skippedCount = skipped,
    unmatchedTrackCount = resolution.unmatchedCount,
  )
}

data class PlaylistImportSummary(
  val importedCount: Int,
  val skippedCount: Int,
  val unmatchedTrackCount: Int,
)
