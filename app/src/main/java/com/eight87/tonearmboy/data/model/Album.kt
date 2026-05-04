package com.eight87.tonearmboy.data.model

data class Album(
  val id: Long,
  val name: String,
  val artist: String?,
  val trackCount: Int,
  val year: Int?,
  /**
   * D.9b.3 — MediaStore album id used to resolve cover art via the
   * legacy `content://media/external/audio/albumart/<id>` provider.
   * Null on rollups where the underlying tracks didn't carry an
   * album id (rare; guards against malformed scans).
   */
  val mediaStoreAlbumId: Long? = null,
)
