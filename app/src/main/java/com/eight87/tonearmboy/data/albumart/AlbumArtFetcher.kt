package com.eight87.tonearmboy.data.albumart

import android.content.Context
import com.eight87.tonearmboy.data.AlbumCoverChoice
import com.eight87.tonearmboy.data.AlbumSource
import com.eight87.tonearmboy.data.albumKey
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * album-art Phase D — orchestrator.
 *
 * Resolves an album to a Cover Art Archive image and stores the local
 * file URI in `album_covers` so the existing Phase A pipeline (Coil
 * via `CoverArt(coverUriOverride = ...)`) renders it.
 *
 * **Phase A precedence is preserved:**
 *
 *   - When the user already pinned a cover (`AlbumCoverChoice.Pinned`),
 *     we never overwrite it.
 *   - When the user explicitly cleared a cover
 *     (`AlbumCoverChoice.IntentionallyEmpty`), we treat that as
 *     "user has spoken — don't auto-fetch" and skip.
 *   - We only overwrite [AlbumCoverChoice.NoChoice] rows.
 *
 * The downloaded image lands in the app's private cache so we don't
 * need WRITE permission on shared storage.
 */
class AlbumArtFetcher(
  private val albumSource: AlbumSource,
  private val musicBrainz: MusicBrainzClient = MusicBrainzClient(),
) {
  private val downloader: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  /**
   * Look up [albumName] / [albumArtist] on MusicBrainz, fetch the
   * Cover Art Archive front image, save it to the app's private
   * cache, and pin it as the album's cover.
   *
   * Returns:
   *   - [Result.Saved] when a new cover was pinned.
   *   - [Result.AlreadyPinned] / [Result.IntentionallyEmpty] when the
   *     user has spoken and we left the album alone.
   *   - [Result.NotFound] when MB has no matching release or CAA
   *     has no front image.
   *   - [Result.Failed] on network / IO errors.
   */
  suspend fun fetch(
    context: Context,
    albumName: String,
    albumArtist: String?,
    overwriteUserChoice: Boolean = false,
  ): FetchResult {
    val key = albumKey(albumName, albumArtist)
    if (!overwriteUserChoice) {
      when (albumSource.albumCoverChoice(key).first()) {
        is AlbumCoverChoice.Pinned -> return FetchResult.AlreadyPinned
        AlbumCoverChoice.IntentionallyEmpty -> return FetchResult.IntentionallyEmpty
        AlbumCoverChoice.NoChoice -> Unit
      }
    }
    val artist = albumArtist?.takeIf { it.isNotBlank() }
      ?: return FetchResult.NotFound
    val mbid = musicBrainz.findReleaseId(artist, albumName)
      ?: return FetchResult.NotFound
    val coverUrl = musicBrainz.coverArtUrl(mbid) ?: return FetchResult.NotFound

    val cacheDir = File(context.cacheDir, "album_art").also { it.mkdirs() }
    val target = File(cacheDir, "${key.hashCode().toUInt()}.jpg")
    val request = Request.Builder().url(coverUrl).build()
    val downloaded = runCatching {
      downloader.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return@use false
        val body = resp.body ?: return@use false
        target.outputStream().use { out -> body.byteStream().copyTo(out) }
        true
      }
    }.getOrDefault(false)
    if (!downloaded) return FetchResult.Failed("download")

    albumSource.setAlbumCoverUri(key, target.toURI().toString())
    return FetchResult.Saved(target.toURI().toString())
  }

  sealed interface FetchResult {
    data class Saved(val uri: String) : FetchResult
    data object AlreadyPinned : FetchResult
    data object IntentionallyEmpty : FetchResult
    data object NotFound : FetchResult
    data class Failed(val reason: String) : FetchResult
  }
}

/** Convenience type alias for screens that consume the fetch result. */
typealias FetchResult = AlbumArtFetcher.FetchResult
