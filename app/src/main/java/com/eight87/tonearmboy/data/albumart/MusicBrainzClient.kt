package com.eight87.tonearmboy.data.albumart

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * album-art Phase D — minimal MusicBrainz / Cover Art Archive client.
 *
 * **Privacy note:** every call here sends artist + album text to a
 * third-party service. The user-facing toggle that activates this
 * client defaults OFF and the manual on-tap path is opt-in per album.
 *
 * **Rate limit:** MB's Terms of Service cap automated traffic at
 * 1 req/sec per identified user-agent. We serialise calls through
 * a [Mutex] + [delay] so even bulk passes stay under that cap;
 * concurrency is single-threaded by construction.
 *
 * **User-Agent:** MB requires a non-empty UA naming the app + version
 * + a contact URL. The TOS link is in `docs/plans/album-art.md`.
 *
 * Two endpoints we hit:
 *
 *   - `https://musicbrainz.org/ws/2/release/?query=...&fmt=json`
 *     — fuzzy text search returning candidate releases. We pick the
 *       top hit whose score ≥ 95.
 *   - `https://coverartarchive.org/release/{mbid}/front`
 *     — 302-redirects to the actual image URL on archive.org. The
 *       `front` size variant is a JPEG ≤ 1200 px on the longer side.
 */
class MusicBrainzClient(
  private val userAgent: String =
    "tonearmboy/1.0 ( https://github.com/887/tonearmboy )",
) {
  private val rateLimit = Mutex()
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Search MusicBrainz for a release matching [artist] + [album].
   * Returns the top-scoring MBID at or above [minScore] (default 70)
   * or null on no match. The user can dial [minScore] up or down via
   * Settings → Content → MusicBrainz match threshold.
   */
  suspend fun findReleaseId(artist: String, album: String, minScore: Int = 70): String? = withContext(Dispatchers.IO) {
    if (artist.isBlank() || album.isBlank()) return@withContext null
    rateLimit.withLock {
      // Mandatory inter-request delay per MB TOS.
      delay(1100)
      val q = buildQuery(artist, album)
      val url = "https://musicbrainz.org/ws/2/release/?query=" +
        java.net.URLEncoder.encode(q, "UTF-8") + "&fmt=json&limit=5"
      val req = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Accept", "application/json")
        .build()
      runCatching {
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@use null
          val body = resp.body?.string() ?: return@use null
          val parsed = json.decodeFromString<MbReleaseSearch>(body)
          // Threshold is user-configurable via Settings → Content
          // (default 70). The original hard-coded ≥ 95 was so strict
          // that fuzzy text matches almost never qualified (user
          // reported "0 hits across 2000 songs"). 70 still filters
          // obvious garbage but accepts typical tag-vs-canonical
          // delta — punctuation, article drift, "(Deluxe Edition)"
          // suffixes. The user can pull the slider up to be picky
          // or down to be permissive.
          parsed.releases
            .firstOrNull { (it.score ?: 0) >= minScore }
            ?.id
        }
      }.getOrNull()
    }
  }

  /**
   * Resolve [mbid] to a Cover Art Archive front-image URL. Returns
   * null when the release has no image. The CAA endpoint returns
   * `307 Temporary Redirect` to the actual file; OkHttp follows it
   * for us when [followRedirects] is true.
   */
  suspend fun coverArtUrl(mbid: String): String? = withContext(Dispatchers.IO) {
    rateLimit.withLock {
      delay(1100)
      val url = "https://coverartarchive.org/release/$mbid/front"
      val req = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .head()
        .build()
      runCatching {
        client.newCall(req).execute().use { resp ->
          if (resp.isSuccessful) resp.request.url.toString() else null
        }
      }.getOrNull()
    }
  }

  private fun buildQuery(artist: String, album: String): String {
    // Quote each component to avoid Lucene operator interpretation.
    val cleanArtist = artist.replace("\"", "")
    val cleanAlbum = album.replace("\"", "")
    return "release:\"$cleanAlbum\" AND artist:\"$cleanArtist\""
  }

  @Serializable
  private data class MbReleaseSearch(
    val releases: List<MbRelease> = emptyList(),
  )

  @Serializable
  private data class MbRelease(
    val id: String,
    val score: Int? = null,
  )
}
