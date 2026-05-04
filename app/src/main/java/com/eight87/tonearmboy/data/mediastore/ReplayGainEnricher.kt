package com.eight87.tonearmboy.data.mediastore

import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.data.model.ScannedTrack
import com.eight87.tonearmboy.playback.replaygain.ReplayGainTagReader
import com.eight87.tonearmboy.playback.replaygain.ReplayGainTags
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * R.F.10 — pass-2 ReplayGain enrichment extracted from the
 * scanner. (Data-F5.) Takes a list of pre-RG stubs from
 * [MediaStoreCursorReader] and reads the `REPLAYGAIN_*` tags out of
 * each track's audio file in parallel, merging them back into a
 * `List<ScannedTrack>`.
 *
 * Bounded concurrency = 4 — the Media3 [ReplayGainTagReader] uses a
 * MetadataRetriever per file with a 1500 ms timeout; sequentially
 * that's ~25 min on a 1k-track library and ANRs the IO threadpool.
 *
 * Suspends instead of `runBlocking` (the pre-R.F.10 shape). The
 * scanner's caller is already a coroutine so no thread-blocking
 * shim is needed.
 */
@UnstableApi
internal class ReplayGainEnricher(
  private val reader: ReplayGainTagReader,
  private val parallelism: Int = DEFAULT_PARALLELISM,
) {

  suspend fun enrich(
    stubs: List<MediaStoreCursorReader.Stub>,
    onProgress: ((scanned: Int, total: Int, currentTitle: String?) -> Unit)? = null,
  ): List<ScannedTrack> = coroutineScope {
    val total = stubs.size
    onProgress?.invoke(0, total, null)
    val sem = Semaphore(parallelism)
    val scanned = AtomicInteger(0)
    val out = stubs.map { stub ->
      async(Dispatchers.IO) {
        sem.withPermit {
          val rg = try {
            reader.read(stub.uri)
          } catch (t: Throwable) {
            Log.w(TAG, "ReplayGain read failed for id=${stub.track.id}: ${t.message}")
            ReplayGainTags.Empty
          }
          val done = scanned.incrementAndGet()
          onProgress?.invoke(done, total, stub.track.title)
          stub.track.copy(
            replayGainTrackDb = rg.trackGainDb,
            replayGainTrackPeak = rg.trackPeak,
            replayGainAlbumDb = rg.albumGainDb,
            replayGainAlbumPeak = rg.albumPeak,
          )
        }
      }
    }.awaitAll()
    onProgress?.invoke(total, total, null)
    out
  }

  companion object {
    private const val TAG = "tonearmboy-rg-enricher"
    const val DEFAULT_PARALLELISM = 4
  }
}
