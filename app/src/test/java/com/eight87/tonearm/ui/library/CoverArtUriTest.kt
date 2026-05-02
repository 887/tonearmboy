package com.eight87.tonearm.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the album-art URI for D.9b.3. The legacy
 * `content://media/external/audio/albumart/<id>` path is supported
 * across our supported API range (26..36), so we don't branch on
 * `Build.VERSION.SDK_INT`. The Robolectric runner gives us a real
 * `android.net.Uri` impl rather than the mocked variant the JVM
 * project would otherwise need.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverArtUriTest {

  @Test
  fun resolved_uri_includes_album_id_segment() {
    val uri = albumArtUri(12345L)
    assertEquals("content", uri.scheme)
    assertEquals("media", uri.authority)
    assertEquals(
      listOf("external", "audio", "albumart", "12345"),
      uri.pathSegments,
    )
  }

  @Test
  fun zero_album_id_is_appended_verbatim() {
    val uri = albumArtUri(0L)
    assertEquals("0", uri.lastPathSegment)
  }

  @Test
  fun very_large_album_id_is_appended_verbatim() {
    // Some MediaStore implementations use 32-bit ids that pack into
    // the upper byte; we want a verbatim Long so the provider can
    // round-trip the value.
    val uri = albumArtUri(Long.MAX_VALUE)
    assertEquals(Long.MAX_VALUE.toString(), uri.lastPathSegment)
  }
}
