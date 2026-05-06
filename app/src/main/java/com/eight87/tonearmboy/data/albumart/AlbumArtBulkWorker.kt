package com.eight87.tonearmboy.data.albumart

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eight87.tonearmboy.AppGraph
import com.eight87.tonearmboy.data.AlbumCoverChoice
import com.eight87.tonearmboy.data.albumKey
import kotlinx.coroutines.flow.first

/**
 * album-art Phase D — bulk auto-fetch worker.
 *
 * Walks every album in the cached library, asks the
 * [AlbumArtFetcher] to fill in covers for albums with no user choice
 * AND no MediaStore embedded art. Phase A's `IntentionallyEmpty`
 * sentinel is respected — those albums are skipped.
 *
 * **Triggering:** the user enables "Auto-discover missing album art
 * (MusicBrainz)" in Settings › Content. The bridge wires that toggle
 * to `WorkManager.enqueueUniqueWork` with a one-shot
 * [androidx.work.OneTimeWorkRequest] (no periodic schedule — the
 * bulk pass runs ONCE per toggle-on; subsequent rescans don't
 * re-invoke it). On the next library rescan, the toggle's "is on"
 * state is consulted again to decide whether to rerun.
 *
 * **Rate:** [MusicBrainzClient] enforces 1 req/sec serialised, so
 * even a 100-album library takes ~3 minutes. The worker runs as a
 * background-data-sync task (no foreground notification) since the
 * pace is slow and the user already opted-in via the toggle.
 */
class AlbumArtBulkWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val graph = AppGraph.get(applicationContext)
    // Honour the user's cover-art-service choice. When `Disabled`
    // the bulk worker exits immediately with no web requests — the
    // setting is the single switch that gates every cover-art
    // round-trip.
    val service = graph.settingsRepository.coverArtService.flow.first()
    if (service == com.eight87.tonearmboy.ui.settings.CoverArtService.Disabled) {
      return Result.success()
    }
    val mbMinScore = graph.settingsRepository.coverArtMatchScore.flow.first()
    val fetcher = AlbumArtFetcher(graph.albums)
    val albums = graph.albums.observeAlbums().first()

    var saved = 0
    var skipped = 0
    var failed = 0
    for (album in albums) {
      if (isStopped) break
      if (album.mediaStoreAlbumId != null) {
        skipped++
        continue
      }
      val key = albumKey(album.name, album.artist)
      val choice = graph.albums.albumCoverChoice(key).first()
      if (choice !is AlbumCoverChoice.NoChoice) {
        skipped++
        continue
      }
      val result = fetcher.fetch(
        applicationContext,
        album.name,
        album.artist,
        service,
        musicBrainzMinScore = mbMinScore,
      )
      when (result) {
        is AlbumArtFetcher.FetchResult.Saved -> saved++
        AlbumArtFetcher.FetchResult.NotFound,
        AlbumArtFetcher.FetchResult.ServiceDisabled,
        is AlbumArtFetcher.FetchResult.Failed -> failed++
        AlbumArtFetcher.FetchResult.AlreadyPinned,
        AlbumArtFetcher.FetchResult.IntentionallyEmpty -> skipped++
      }
    }
    return Result.success()
  }

  companion object {
    const val UNIQUE_WORK_NAME = "tonearmboy_album_art_bulk"
  }
}
