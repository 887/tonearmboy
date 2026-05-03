package com.eight87.tonearm.data.playlist

import com.eight87.tonearm.data.PlaylistStore
import com.eight87.tonearm.data.db.LibraryDatabase
import com.eight87.tonearm.data.db.TrackDao
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Result of an M3U / M3U8 import.
 *
 * @property playlistId   id of the freshly created playlist row
 * @property matchedCount number of M3U entries that resolved to a track in the library
 * @property skipped      M3U entries we couldn't resolve, in source order
 */
data class M3UImportResult(
  val playlistId: Long,
  val matchedCount: Int,
  val skipped: List<String>,
)

/**
 * Imports M3U / M3U8 playlists into Room.
 *
 * Format: each non-blank, non-comment (`#`-prefixed) line is a path to
 * an audio file. Paths can be absolute or relative to the M3U file's
 * directory. We match against the cached `MediaStore.Audio.Media.DATA`
 * column. Lines that don't match a known track are returned in
 * [M3UImportResult.skipped] so callers can surface them.
 *
 * `.m3u` is conventionally Latin-1 / system-default; `.m3u8` is UTF-8.
 * We honor both by selecting the charset from the file extension. The
 * `EXTM3U` and `EXTINF` directives are tolerated (and ignored — we
 * don't trust their metadata, MediaStore is the source of truth).
 */
class M3UImporter(
  private val repository: PlaylistStore,
  private val trackDao: TrackDao,
) {

  /** Convenience constructor that pulls the DAO out of [LibraryDatabase]. */
  constructor(repository: PlaylistStore, db: LibraryDatabase) :
    this(repository, db.trackDao())

  /**
   * Import [file] as a new playlist named [playlistName]. Throws if the
   * playlist name already exists (DAO is INSERT ABORT).
   */
  suspend fun import(file: File, playlistName: String): M3UImportResult {
    val charset = charsetFor(file.name)
    return file.inputStream().use { stream ->
      importFromStream(stream, file.parentFile, playlistName, charset)
    }
  }

  /**
   * Import directly from [InputStream]. [baseDir] is used to resolve
   * relative paths; pass null when the playlist only contains
   * absolute paths (or you don't have a base, e.g. when importing
   * via a `content://` URI).
   */
  suspend fun importFromStream(
    stream: InputStream,
    baseDir: File?,
    playlistName: String,
    charset: Charset = StandardCharsets.UTF_8,
  ): M3UImportResult {
    val rawLines = BufferedReader(InputStreamReader(stream, charset)).useLines { it.toList() }
    val entries = parseEntries(rawLines)

    val resolved = ArrayList<Long>(entries.size)
    val skipped = ArrayList<String>()

    for (entry in entries) {
      val resolvedPath = resolvePath(entry, baseDir)
      val match = if (resolvedPath != null) findTrackByData(resolvedPath) else null
      if (match != null) {
        resolved += match
      } else {
        skipped += entry
      }
    }

    val playlistId = repository.createPlaylist(playlistName)
    if (resolved.isNotEmpty()) {
      repository.reorderPlaylist(playlistId, resolved)
    }
    return M3UImportResult(playlistId, resolved.size, skipped)
  }

  internal fun parseEntries(lines: List<String>): List<String> {
    val out = ArrayList<String>()
    for (rawLine in lines) {
      // Strip BOM + trim whitespace.
      val trimmed = rawLine.removePrefix("﻿").trim()
      if (trimmed.isEmpty()) continue
      if (trimmed.startsWith("#")) continue   // EXTM3U, EXTINF, comments
      out += trimmed
    }
    return out
  }

  internal fun resolvePath(entry: String, baseDir: File?): String? {
    // M3U paths can be Windows-style (\); normalize to forward slashes
    // so relative resolution behaves under java.io.File on Linux/Android.
    val normalized = entry.replace('\\', '/')
    if (normalized.startsWith("http://", ignoreCase = true) ||
      normalized.startsWith("https://", ignoreCase = true)
    ) {
      // Streaming URLs aren't backed by MediaStore — skip.
      return null
    }
    val file = if (File(normalized).isAbsolute || baseDir == null) {
      File(normalized)
    } else {
      File(baseDir, normalized)
    }
    return runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
  }

  private suspend fun findTrackByData(absolutePath: String): Long? {
    // Linear scan over the cached track set. The library is at most a
    // few thousand rows in practice; we trade O(n) for not having to
    // add and maintain an index just for import. If this shows up on a
    // profile we can cache a Map<String, Long> in the repository.
    val all = trackDao.allIds()
    if (all.isEmpty()) return null
    // Pull rows in reasonable chunks to avoid SQLITE_MAX_VARIABLE_NUMBER.
    val chunkSize = 500
    for (chunk in all.chunked(chunkSize)) {
      val rows = trackDao.getByIds(chunk)
      val hit = rows.firstOrNull { it.data == absolutePath }
      if (hit != null) return hit.id
    }
    return null
  }

  private fun charsetFor(fileName: String): Charset {
    val lower = fileName.lowercase()
    return if (lower.endsWith(".m3u8")) StandardCharsets.UTF_8 else StandardCharsets.ISO_8859_1
  }
}
