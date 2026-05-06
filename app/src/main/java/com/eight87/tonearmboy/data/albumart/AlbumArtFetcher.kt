package com.eight87.tonearmboy.data.albumart

import android.content.Context
import com.eight87.tonearmboy.data.AlbumCoverChoice
import com.eight87.tonearmboy.data.AlbumSource
import com.eight87.tonearmboy.data.albumKey
import com.eight87.tonearmboy.ui.settings.CoverArtService
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
  private val iTunes: ITunesClient = ITunesClient(),
) {
  private val downloader: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  /**
   * Resolve [albumName] / [albumArtist] to a cover image via the
   * caller-selected [service], download it to the app's private
   * cache, and pin it as the album's cover.
   *
   * **Privacy contract:** when [service] is [CoverArtService.Disabled]
   * this function makes ZERO web requests and returns
   * [FetchResult.ServiceDisabled]. Both the bulk auto-fetch worker
   * and the per-album manual "Search online" overflow funnel through
   * here, so the Disabled setting is the single switch that gates
   * every cover-art network round-trip.
   *
   * Returns:
   *   - [FetchResult.Saved] when a new cover was pinned.
   *   - [FetchResult.AlreadyPinned] / [FetchResult.IntentionallyEmpty]
   *     when the user has spoken and we left the album alone.
   *   - [FetchResult.NotFound] when the chosen service has no match.
   *   - [FetchResult.ServiceDisabled] when [service] is
   *     [CoverArtService.Disabled] — no requests fired.
   *   - [FetchResult.Failed] on network / IO errors.
   */
  suspend fun fetch(
    context: Context,
    albumName: String,
    albumArtist: String?,
    service: CoverArtService,
    musicBrainzMinScore: Int = 70,
    overwriteUserChoice: Boolean = false,
  ): FetchResult {
    if (service == CoverArtService.Disabled) return FetchResult.ServiceDisabled
    val key = albumKey(albumName, albumArtist)
    if (!overwriteUserChoice) {
      when (albumSource.albumCoverChoice(key).first()) {
        is AlbumCoverChoice.Pinned -> return FetchResult.AlreadyPinned
        AlbumCoverChoice.IntentionallyEmpty -> return FetchResult.IntentionallyEmpty
        AlbumCoverChoice.NoChoice -> Unit
      }
    }
    return AlbumArtFetchRegistry.withFetch(key) {
      doFetch(context, key, albumName, albumArtist, service, musicBrainzMinScore)
    }
  }

  private suspend fun doFetch(
    context: Context,
    key: String,
    albumName: String,
    albumArtist: String?,
    service: CoverArtService,
    musicBrainzMinScore: Int,
  ): FetchResult {
    val coverUrl = when (service) {
      CoverArtService.Disabled -> return FetchResult.ServiceDisabled  // unreachable, satisfies exhaustive when
      CoverArtService.MusicBrainz -> {
        // MusicBrainz' release search wants both artist + album to
        // produce a useful score; without album-artist we'd be
        // searching against every release in the catalogue.
        val artist = albumArtist?.takeIf { it.isNotBlank() }
          ?: return FetchResult.NotFound
        val mbid = musicBrainz.findReleaseId(artist, albumName, musicBrainzMinScore)
          ?: return FetchResult.NotFound
        musicBrainz.coverArtUrl(mbid)
      }
      CoverArtService.ITunes -> iTunes.findCoverUrl(albumArtist, albumName)
    } ?: return FetchResult.NotFound

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
    data object ServiceDisabled : FetchResult
    data class Failed(val reason: String) : FetchResult
  }
}

/** Convenience type alias for screens that consume the fetch result. */
typealias FetchResult = AlbumArtFetcher.FetchResult
