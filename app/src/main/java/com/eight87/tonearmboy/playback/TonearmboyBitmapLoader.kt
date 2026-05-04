package com.eight87.tonearmboy.playback

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/**
 * D.23.2 — custom Media3 [BitmapLoader] that resolves cover art for
 * tonearmboy tracks in three escalating attempts:
 *
 *  1. **Embedded picture frame.** If the [Uri] is a `file://` URI we
 *     delegate to [DataSourceBitmapLoader] which extracts an embedded
 *     ID3v2 APIC / FLAC PICTURE / MP4 covr frame.
 *  2. **MediaStore legacy album-art content URI.** If the
 *     [MediaMetadata.extras] bundle carries a
 *     [PlaybackUiController.EXTRA_MEDIA_STORE_ALBUM_ID] value we fall
 *     back to `content://media/external/audio/albumart/<albumId>`,
 *     which is still functional through API 36 even though it's
 *     undocumented since N. This is the same fallback the in-app
 *     `CoverArt` composable uses.
 *  3. **Fail.** Both attempts exhausted → return a failed future and
 *     Media3 / SystemUI render no large icon (the platform default).
 *
 * The loader is wired in [PlaybackService.onCreate] via
 * [androidx.media3.session.MediaSession.Builder.setBitmapLoader] so
 * SystemUI's Quick Settings media card, the in-tray notification, and
 * the lock-screen MediaStyle surface all pick up artwork the same way.
 */
@UnstableApi
class TonearmboyBitmapLoader(
  private val context: Context,
  private val executor: ListeningExecutorService = DEFAULT_EXECUTOR,
  private val delegate: BitmapLoader = DataSourceBitmapLoader(context.applicationContext),
) : BitmapLoader {

  override fun supportsMimeType(mimeType: String): Boolean =
    delegate.supportsMimeType(mimeType)

  override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
    delegate.decodeBitmap(data)

  override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
    // No metadata context here — try the delegate (embedded frame for
    // file:// URIs, plus whatever the DataSource resolver supports for
    // http/content). If the delegate fails we have no album-id
    // fallback at this entry point because the metadata isn't
    // threaded through.
    return delegate.loadBitmap(uri)
  }

  override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
    val artworkUri = metadata.artworkUri
    val albumId = metadata.extras
      ?.getLong(PlaybackUiController.EXTRA_MEDIA_STORE_ALBUM_ID, -1L)
      ?.takeIf { it >= 0 }

    // Fast path: no artwork URI and no album id → let the framework
    // fall back to nothing. Returning null tells Media3 we have no
    // bitmap source for this metadata, which is the correct signal
    // for "render no large icon".
    if (artworkUri == null && albumId == null) return null

    val embeddedFuture: ListenableFuture<Bitmap>? = artworkUri?.let { delegate.loadBitmap(it) }

    if (embeddedFuture != null && albumId == null) {
      return embeddedFuture
    }
    if (embeddedFuture == null && albumId != null) {
      return loadFromAlbumArtContentUri(albumId)
    }
    // Both available: try embedded first, fall back to album-art uri
    // on failure. We chain via Futures.catchingAsync so a transparent
    // hop happens off the calling thread.
    val primary: ListenableFuture<Bitmap> = embeddedFuture!!
    val fallbackId: Long = albumId!!
    return Futures.catchingAsync(
      primary,
      Throwable::class.java,
      { _ -> loadFromAlbumArtContentUri(fallbackId) },
      executor,
    )
  }

  internal fun loadFromAlbumArtContentUri(albumId: Long): ListenableFuture<Bitmap> {
    val uri = Uri.parse("content://media/external/audio/albumart/$albumId")
    return executor.submit<Bitmap> {
      val resolver: ContentResolver = context.contentResolver
      val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw java.io.FileNotFoundException("album art not found for id=$albumId")
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw java.io.IOException("failed to decode album art for id=$albumId")
    }
  }

  companion object {
    private val DEFAULT_EXECUTOR: ListeningExecutorService =
      MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tonearmboy-bitmaploader").apply { isDaemon = true }
      })
  }
}
