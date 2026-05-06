package com.eight87.tonearmboy.data.albumart

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Minimal client for the public iTunes Search API.
 *
 * `https://itunes.apple.com/search?term=<query>&entity=album&limit=N`
 *
 * Free, no auth, no rate limit beyond standard fair-use (Apple's
 * documentation advises ~20 queries / minute / IP). The endpoint
 * returns a JSON document with `results: [{ artworkUrl100, ... }]`.
 *
 * The artwork URL it hands out is a 100x100 thumbnail; we rewrite
 * the path to request a larger size — the Apple CDN serves any
 * `<n>x<n>bb.jpg` size variant that exists for the asset, and 600x600
 * is the typical "high-DPI cover" tier. We cap at 600 so the cache
 * doesn't bloat with full-res master assets the player will never
 * draw at native size.
 *
 * **Privacy:** every call sends artist + album text to Apple. Wired
 * via the `coverArtService = ITunes` setting which defaults
 * `Disabled` so the user opts in explicitly.
 */
class ITunesClient {
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Search iTunes for an album cover URL matching [artist] + [album].
   * Returns the rewritten 600x600 artwork URL or null on no match.
   *
   * Falls back to a search without the artist when [artist] is blank
   * — common case where MediaStore has no album-artist tag, only the
   * track-artist (or nothing). iTunes' fuzzy match still finds
   * popular albums by title alone.
   */
  suspend fun findCoverUrl(artist: String?, album: String): String? = withContext(Dispatchers.IO) {
    if (album.isBlank()) return@withContext null
    val term = listOfNotNull(artist?.takeIf { it.isNotBlank() }, album)
      .joinToString(" ")
    val url = "https://itunes.apple.com/search?term=" +
      java.net.URLEncoder.encode(term, "UTF-8") +
      "&entity=album&limit=5"
    val req = Request.Builder()
      .url(url)
      .header("Accept", "application/json")
      .build()
    runCatching {
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        val body = resp.body?.string() ?: return@use null
        val parsed = json.decodeFromString<ITunesSearchResponse>(body)
        // Pick the first hit whose collectionName contains (or is
        // contained by) the requested album. iTunes search results
        // are not strictly ranked by relevance, but the top hit on a
        // direct album-name match is usually correct. Without this
        // guard a search for "Velvet Den" might return an unrelated
        // result whose closest match is some song titled the same.
        val albumLower = album.lowercase().trim()
        val match = parsed.results.firstOrNull { r ->
          val name = r.collectionName?.lowercase()?.trim().orEmpty()
          name.isNotEmpty() && (
            name.contains(albumLower) || albumLower.contains(name)
          )
        } ?: parsed.results.firstOrNull()
        match?.artworkUrl100?.let(::upscaleToSixHundred)
      }
    }.getOrNull()
  }

  /**
   * Apple CDN trick: the artwork URL embeds the size in a path
   * segment like `/100x100bb.jpg`; replacing it with `600x600bb.jpg`
   * pulls the higher-res variant. Falls back to the original URL
   * when the pattern doesn't match (defensive — Apple has changed
   * the format before).
   */
  internal fun upscaleToSixHundred(thumbUrl: String): String =
    thumbUrl.replace(Regex("""/\d+x\d+bb\.jpg$"""), "/600x600bb.jpg")
      .replace(Regex("""/\d+x\d+bb\.png$"""), "/600x600bb.png")
}

@Serializable
internal data class ITunesSearchResponse(
  val resultCount: Int = 0,
  val results: List<ITunesAlbumResult> = emptyList(),
)

@Serializable
internal data class ITunesAlbumResult(
  val collectionName: String? = null,
  val artistName: String? = null,
  val artworkUrl100: String? = null,
)
