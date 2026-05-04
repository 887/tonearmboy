package com.eight87.tonearmboy.theme

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * D.20.4 — runtime palette source. The activity feeds it the
 * playing track's MediaStore album id; this class loads the cover
 * bitmap, runs `extractAlbumPalette`, and publishes the result on a
 * `StateFlow` the Compose tree provides through `LocalAlbumPalette`.
 *
 * Caches per-album palettes (LRU, 32 entries) so a track switch
 * inside the same album doesn't re-decode the bitmap.
 *
 * Falls back to [AlbumPalette.Empty] when the album id is null or
 * the cover URI returns no bitmap (Field Recordings case).
 */
class AlbumPaletteSource(private val context: Context) {

  private val _palette = MutableStateFlow(AlbumPalette.Empty)
  val palette: StateFlow<AlbumPalette> = _palette.asStateFlow()

  private val cache = LruCache<Long, AlbumPalette>(32)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  /**
   * Update the palette to reflect the now-playing track. Cheap when
   * [albumId] is unchanged from the previous call; the LRU cache
   * makes second-and-subsequent visits to the same album free.
   */
  fun setAlbumId(albumId: Long?) {
    if (albumId == null) {
      _palette.value = AlbumPalette.Empty
      return
    }
    cache[albumId]?.let {
      _palette.value = it
      return
    }
    scope.launch {
      val extracted = withContext(Dispatchers.IO) {
        loadCoverBitmap(context.contentResolver, albumId)?.let { bmp ->
          try {
            extractAlbumPalette(bmp)
          } finally {
            // Recycle the decoded bitmap as soon as the palette has
            // been derived. Cover art bitmaps are 600 ^ 2 ARGB_8888
            // (~1.4 MB) and we'd otherwise hold them until GC.
            bmp.recycle()
          }
        } ?: AlbumPalette.Empty
      }
      cache.put(albumId, extracted)
      _palette.value = extracted
    }
  }

  fun shutdown() {
    scope.cancel()
  }

  /**
   * Decode the legacy MediaStore album-art content URI for [albumId].
   * Returns null when the URI is missing or undecodable (e.g. the
   * album row exists but no cover was indexed). Pure I/O — caller
   * MUST run this on a non-Main dispatcher.
   */
  private fun loadCoverBitmap(resolver: ContentResolver, albumId: Long): Bitmap? {
    val uri: Uri = ContentUris.withAppendedId(LEGACY_ALBUM_ART_BASE, albumId)
    return runCatching {
      resolver.openInputStream(uri).use { input ->
        if (input == null) return null
        BitmapFactory.decodeStream(input)
      }
    }.onFailure {
      Log.d(TAG, "loadCoverBitmap failed for albumId=$albumId", it)
    }.getOrNull()
  }

  companion object {
    private const val TAG = "tonearmboy-palette"
    private val LEGACY_ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")
  }
}
