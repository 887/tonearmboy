package com.eight87.tonearmboy.ui.library

import android.content.ContentUris
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode

/**
 * Phase D.9b.3 — render the cover art for an album, with a music-note
 * placeholder when the load fails or the user has covers turned off.
 *
 * The album art URI is the legacy MediaStore `albumart` content path:
 *
 *   content://media/external/audio/albumart/<albumId>
 *
 * That path is supported on API 26+ (it predates scoped storage) and
 * still works on API 36 — Android resolves it through MediaStore's
 * built-in album-art thumbnail provider. The post-API-29 alternative,
 * `ContentResolver.loadThumbnail(albumUri, size, signal)`, exists but
 * is awkward to drive from a Compose async image — Coil already
 * handles the legacy URI directly via `ContentResolver.openInputStream`.
 *
 * Behaviour by [mode]:
 *  - [AlbumCoversMode.Off] — render the placeholder, no network/disk
 *    work at all
 *  - [AlbumCoversMode.Balanced] (default) — Coil scales the request
 *    to the cell `size` so disk + decode cost matches the visible
 *    cell, not the full-resolution embedded image
 *  - [AlbumCoversMode.On] — Coil decodes at the original size
 */
@Composable
fun CoverArt(
  albumId: Long?,
  size: Dp,
  mode: AlbumCoversMode,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
  /**
   * Phase A — per-album cover override URI. When non-null, Coil
   * loads this directly (skipping the legacy MediaStore `albumart`
   * path). Pass null to keep the existing fallback chain. Pulled
   * from `LibraryRepository.albumCoverUri` upstream.
   */
  coverUriOverride: String? = null,
) {
  Box(
    modifier = modifier
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    val showPlaceholder = mode == AlbumCoversMode.Off ||
      (albumId == null && coverUriOverride.isNullOrBlank())
    if (showPlaceholder) {
      Placeholder(size)
      return@Box
    }

    val context = LocalContext.current
    val uri: Any = remember(albumId, coverUriOverride) {
      coverUriOverride?.takeIf { it.isNotBlank() }
        ?: albumArtUri(albumId ?: 0L)
    }
    var failed by remember(uri) { mutableStateOf(false) }

    if (failed) {
      Placeholder(size)
      return@Box
    }

    val request = remember(uri, mode) {
      val builder = ImageRequest.Builder(context).data(uri)
      // Balanced: ask Coil for a thumbnail-sized result. We pass
      // ORIGINAL only when the user explicitly opts in to "On".
      if (mode == AlbumCoversMode.Balanced) builder.size(Size.ORIGINAL)
      builder.build()
    }

    AsyncImage(
      model = request,
      contentDescription = contentDescription,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
      onState = { state ->
        // Coil emits Error / Empty when the album genuinely has no art
        // — the legacy URI returns a FileNotFoundException through the
        // ContentResolver. We fall back to the placeholder silently.
        if (state is AsyncImagePainter.State.Error) failed = true
      },
    )
  }
}

@Composable
private fun Placeholder(size: Dp) {
  // Explicit `onSurfaceVariant` tint — the Icon default is
  // `LocalContentColor.current`, which is unstable across the chrome-
  // tint pipeline (the album palette nudges `surfaceVariant`, and a
  // default-black icon on a tinted-but-still-dark surface lands
  // invisible). Pinning to the M3 token guarantees the placeholder
  // always has the canonical-pair contrast against the tile bg.
  // Size at 60% of the tile so the music-note centres cleanly instead
  // of expanding edge-to-edge (the previous `size(size)` rendered the
  // icon at full tile width, which clipped against rounded corners).
  Icon(
    imageVector = Icons.Filled.MusicNote,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(size * 0.6f),
  )
}

/**
 * Build a content URI for the album art of [albumId]. Backed by the
 * legacy MediaStore `albumart` thumbnail provider.
 *
 * Visible for the Robolectric `CoverArtUriTest`.
 */
internal fun albumArtUri(albumId: Long): Uri =
  ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

/**
 * The MediaStore album-art base URI. This is the legacy path — the
 * post-API-29 documented replacement is
 * `ContentResolver.loadThumbnail(...)`, but the legacy URI is
 * supported through API 36 inclusive (verified on `emulator-5554`)
 * and integrates cleanly with Coil's `ContentResolver` fetcher.
 */
private val ALBUM_ART_BASE: Uri =
  Uri.parse("content://media/external/audio/albumart")
