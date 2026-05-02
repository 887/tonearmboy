package com.eight87.tonearm.playback

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutionException

/**
 * D.23.5 — three-branch coverage for [TonearmBitmapLoader]: embedded
 * picture loads, content-uri album-art fallback loads, both fail.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class TonearmBitmapLoaderTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun embedded_picture_loads_via_delegate() {
    val embeddedBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    val delegate = StubBitmapLoader(
      onLoadBitmap = { Futures.immediateFuture(embeddedBitmap) },
    )
    val loader = TonearmBitmapLoader(
      context = context,
      executor = MoreExecutors.newDirectExecutorService(),
      delegate = delegate,
    )

    val metadata = metadata(
      artworkUri = Uri.parse("file:///music/track.mp3"),
      albumId = 42L,
    )
    val future = loader.loadBitmapFromMetadata(metadata)
    assertNotNull(future)
    val result = future!!.get()
    assertTrue("expected delegate's embedded bitmap", result === embeddedBitmap)
  }

  @Test
  fun content_uri_fallback_loads_when_embedded_fails() {
    // Seed a real PNG byte stream behind the content://media/external/audio/albumart/77
    // URI by round-tripping a Bitmap through Bitmap.compress — this
    // produces bytes that Robolectric's javax.imageio-backed
    // BitmapFactory can decode.
    val seed = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    val baos = java.io.ByteArrayOutputStream()
    seed.compress(Bitmap.CompressFormat.PNG, 100, baos)
    val pngBytes = baos.toByteArray()
    val albumArtUri = Uri.parse("content://media/external/audio/albumart/77")
    shadowOf(context.contentResolver).registerInputStream(albumArtUri, ByteArrayInputStream(pngBytes))

    val delegate = StubBitmapLoader(
      onLoadBitmap = { Futures.immediateFailedFuture(RuntimeException("no embedded picture")) },
    )
    val loader = TonearmBitmapLoader(
      context = context,
      executor = MoreExecutors.newDirectExecutorService(),
      delegate = delegate,
    )

    val metadata = metadata(
      artworkUri = Uri.parse("file:///music/track.mp3"),
      albumId = 77L,
    )
    val future = loader.loadBitmapFromMetadata(metadata)
    assertNotNull(future)
    val result = future!!.get()
    assertNotNull("expected decoded album-art bitmap", result)
  }

  @Test
  fun both_paths_fail_returns_failed_future() {
    val albumArtUri = Uri.parse("content://media/external/audio/albumart/999")
    // Don't register any input stream — ContentResolver returns null.
    val delegate = StubBitmapLoader(
      onLoadBitmap = { Futures.immediateFailedFuture(RuntimeException("no embedded")) },
    )
    val loader = TonearmBitmapLoader(
      context = context,
      executor = MoreExecutors.newDirectExecutorService(),
      delegate = delegate,
    )

    val metadata = metadata(
      artworkUri = Uri.parse("file:///music/track.mp3"),
      albumId = 999L,
    )
    val future = loader.loadBitmapFromMetadata(metadata)
    assertNotNull(future)
    try {
      future!!.get()
      fail("expected failure when both embedded and content-uri loads fail")
    } catch (e: ExecutionException) {
      // expected
    }
  }

  @Test
  fun no_artwork_no_album_id_returns_null_future() {
    val delegate = StubBitmapLoader(
      onLoadBitmap = { Futures.immediateFailedFuture(RuntimeException("no embedded")) },
    )
    val loader = TonearmBitmapLoader(
      context = context,
      executor = MoreExecutors.newDirectExecutorService(),
      delegate = delegate,
    )
    val metadata = MediaMetadata.Builder().setTitle("title-only").build()
    val future = loader.loadBitmapFromMetadata(metadata)
    assertNull("no artwork + no albumId → null future (Media3 falls back to no large icon)", future)
  }

  private fun metadata(artworkUri: Uri?, albumId: Long?): MediaMetadata {
    val extras = albumId?.let {
      Bundle().apply {
        putLong(PlaybackUiController.EXTRA_MEDIA_STORE_ALBUM_ID, it)
      }
    }
    return MediaMetadata.Builder()
      .setTitle("Test Track")
      .setArtist("Test Artist")
      .also { if (artworkUri != null) it.setArtworkUri(artworkUri) }
      .also { if (extras != null) it.setExtras(extras) }
      .build()
  }

  /** Minimal in-memory [BitmapLoader] used to drive the three branches. */
  private class StubBitmapLoader(
    private val onLoadBitmap: (Uri) -> ListenableFuture<Bitmap>,
  ) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = true
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
      Futures.immediateFailedFuture(UnsupportedOperationException("not used"))
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = onLoadBitmap(uri)
  }

}
