package com.eight87.tonearmboy.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bidirectional cover-art prefetcher — bypasses Coil's pipeline.
 *
 * Mirrors shutterboy's `ThumbnailPrefetcher`. Workers load bitmaps
 * directly via `ContentResolver.openInputStream` + `BitmapFactory`
 * (with `inSampleSize` for downscaling), copy to HARDWARE bitmap on
 * API 26+, and stuff the result straight into Coil's
 * [MemoryCache.set]. The on-screen `AsyncImage` gets an INSTANT
 * memory-cache hit when it composes — no fetch round-trip, no
 * decode flash, no contention with Coil's bounded fetcher / decoder
 * pool that visible tiles also use.
 *
 *  - LIFO order: newest submission pops first (grid scheduler
 *    submits items farthest from viewport center first so closer
 *    items land on top of the LIFO).
 *  - Bounded ([maxPending]): oldest entries drop on overflow.
 *  - Per-key memory-cache short-circuit on submit.
 *  - 8 workers on [Dispatchers.IO], independent of Coil's pool.
 */
class CoverArtPrefetcher(
  private val context: Context,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  private val maxPending: Int = 200,
  workerCount: Int = 8,
) {

  private data class Task(val uri: String, val cacheKey: String, val targetPx: Int)

  private val deque = ArrayDeque<Task>()
  private val signal = Channel<Unit>(Channel.UNLIMITED)
  private val lock = Mutex()

  private val loader by lazy { SingletonImageLoader.get(context) }

  init {
    repeat(workerCount) {
      scope.launch(Dispatchers.IO) { worker() }
    }
  }

  /**
   * Submit a prefetch for [uri] at [targetPx]. Items already in
   * Coil's memory cache short-circuit; otherwise the task is pushed
   * to the LIFO front so a worker picks it up next.
   */
  suspend fun submit(uri: String, targetPx: Int) {
    if (uri.isBlank() || targetPx <= 0) return
    val key = "cover-$uri-$targetPx"
    if (loader.memoryCache?.get(MemoryCache.Key(key)) != null) return
    lock.withLock {
      deque.addLast(Task(uri, key, targetPx))
      while (deque.size > maxPending) deque.removeFirst()
    }
    signal.trySend(Unit)
  }

  suspend fun clear() {
    lock.withLock { deque.clear() }
  }

  private suspend fun worker() {
    while (true) {
      val task = lock.withLock { deque.removeLastOrNull() }
      if (task == null) {
        signal.receive()
        continue
      }
      runCatching { decodeAndCache(task) }
      // Failures are swallowed — non-fatal per tile; on-screen
      // AsyncImage will retry via the normal path.
    }
  }

  private fun decodeAndCache(task: Task) {
    val parsed = Uri.parse(task.uri)
    // First pass: inJustDecodeBounds to read the source dimensions
    // without allocating a full bitmap.
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use {
      BitmapFactory.decodeStream(it, null, boundsOpts)
    } ?: return
    val sampleSize = computeInSampleSize(
      srcW = boundsOpts.outWidth,
      srcH = boundsOpts.outHeight,
      targetPx = task.targetPx,
    )
    val decodeOpts = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      // Decode straight to ARGB_8888 — we'll copy to HARDWARE
      // below where supported.
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val raw = context.contentResolver.openInputStream(parsed)?.use {
      BitmapFactory.decodeStream(it, null, decodeOpts)
    } ?: return
    // HARDWARE copy on API 26+ — bitmap lives in GPU memory, draws
    // are zero-copy. Software bitmaps re-upload to GPU every frame,
    // which is the exact micro-stutter signature we want to avoid
    // when many tiles paint at once.
    val final = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      runCatching { raw.copy(Bitmap.Config.HARDWARE, false) }
        .getOrNull()
        ?.also { if (it !== raw) raw.recycle() }
        ?: raw
    } else raw
    loader.memoryCache?.set(
      MemoryCache.Key(task.cacheKey),
      MemoryCache.Value(final.asImage()),
    )
  }

  private fun computeInSampleSize(srcW: Int, srcH: Int, targetPx: Int): Int {
    if (srcW <= 0 || srcH <= 0 || targetPx <= 0) return 1
    var inSampleSize = 1
    var halfW = srcW / 2
    var halfH = srcH / 2
    while ((halfW / inSampleSize) >= targetPx && (halfH / inSampleSize) >= targetPx) {
      inSampleSize *= 2
    }
    return inSampleSize
  }
}

/**
 * Composition local for the app-scoped [CoverArtPrefetcher].
 */
val LocalCoverArtPrefetcher = staticCompositionLocalOf<CoverArtPrefetcher?> { null }
