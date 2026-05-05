package com.eight87.tonearmboy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase A — per-album cover override (`docs/plans/album-art.md`).
 *
 * One row per album whose cover art the user has manually pinned.
 * Keyed by a stable string derived from `(albumName, albumArtist)` so
 * the override survives a rescan even if the album's MediaStore id
 * changes (which it does on some devices when files are renamed /
 * copied around).
 *
 * Tri-state, encoded as `(row presence, coverUri non-null)`:
 *
 *   - **Row missing** → "no user choice"; UI falls back to the default
 *     chain (MediaStore `albumart` URI → music-note placeholder). Future
 *     bulk auto-fetch (Phase D) is allowed to populate a cover here.
 *   - **Row present, `coverUri = "<uri>"`** → user pinned this image.
 *     Skip the fallback chain. Future auto-fetch leaves it alone.
 *   - **Row present, `coverUri = null`** → user explicitly chose
 *     "no cover here." Render the placeholder, **and** block future
 *     auto-fetch from overwriting the choice (the "intentional unset"
 *     the user asked for).
 */
@Entity(tableName = "album_covers")
data class AlbumCoverEntity(
  @PrimaryKey val albumKey: String,
  val coverUri: String?,
)
