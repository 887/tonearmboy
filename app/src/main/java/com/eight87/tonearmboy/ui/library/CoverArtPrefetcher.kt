package com.eight87.tonearmboy.ui.library

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.size.Precision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bidirectional cover-art prefetcher — bounded LIFO queue + worker pool.
 *
 * Shutterboy's `ThumbnailPrefetcher` shape, simplified for tonearmboy:
 * we don't need `loadThumbnail` (the cover URIs are mixed
 * `file:///` / `content://media/.../albumart/<id>` / pinned-file —
 * `loadThumbnail` doesn't cover them). Instead each worker submits a
 * full `ImageRequest` through Coil's normal pipeline, which puts the
 * decoded bitmap straight into the memory cache.
 *
 * The grid scheduler in [LibraryTileGrid] feeds this with items in a
 * window around the visible viewport. When a tile actually scrolls
 * into view its `AsyncImage` request becomes a memory-cache hit — no
 * spinner, no decode flash, no main-thread decode work.
 *
 *  - LIFO order: newest submission pops first (the grid prioritises
 *    items closest to the viewport center).
 *  - Bounded ([maxPending]): oldest entries drop when over capacity.
 *  - Per-key memory-cache short-circuit on submit so already-warm
 *    items don't re-queue.
 *  - 4 workers, off the main thread.
 */
class CoverArtPrefetcher(
  private val context: Context,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  private val maxPending: Int = 80,
  workerCount: Int = 4,
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
      val req = ImageRequest.Builder(context)
        .data(task.uri)
        .size(coil3.size.Size(task.targetPx, task.targetPx))
        .precision(Precision.INEXACT)
        .memoryCacheKey(task.cacheKey)
        .diskCacheKey(task.cacheKey)
        .build()
      runCatching { loader.execute(req) }
      // Failures are swallowed — non-fatal per tile; on-screen
      // AsyncImage will retry via the normal path.
    }
  }
}

/**
 * Composition local for the app-scoped [CoverArtPrefetcher]. Library
 * surfaces ([LibraryTileGrid]) read this to schedule prefetches off
 * the visible viewport. Null when no prefetcher is bound (tests,
 * Robolectric, previews).
 */
val LocalCoverArtPrefetcher = staticCompositionLocalOf<CoverArtPrefetcher?> { null }
