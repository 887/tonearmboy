package com.eight87.tonearmboy.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread

/**
 * R.F.9 — shared `ContentObserver` plumbing used by both the
 * repository's in-process rescan loop and the foreground
 * `LibraryWatcherService`. Owns the HandlerThread + ContentObserver
 * pair and the bookkeeping for which URIs are currently registered
 * (so callers can rebind to a different URI set without leaking
 * observers). (Data-F7.)
 *
 * The debounce policy stays per-consumer — the repository uses
 * `Flow.debounce` against an in-process scan job; the watcher uses
 * `WorkManager` for survival across process death — but both now
 * route through the same observer wiring.
 */
class MediaChangeObserver(
  private val contentResolver: ContentResolver,
  threadName: String,
  private val onChange: (Uri?) -> Unit,
) {
  private val thread = HandlerThread(threadName).apply { start() }
  private val handler = Handler(thread.looper)
  private val observer = object : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean, uri: Uri?) {
      onChange.invoke(uri)
    }
  }
  private val registered = mutableSetOf<Uri>()

  /**
   * Register against [uri]. No-op if the same URI is already bound.
   * `notifyForDescendants = true` so subdirectory changes inside SAF
   * trees fire too.
   */
  fun register(uri: Uri) {
    if (uri in registered) return
    runCatching {
      contentResolver.registerContentObserver(uri, /* notifyForDescendants = */ true, observer)
      registered += uri
    }
  }

  /**
   * Replace the registered URI set with [uris]. Unregisters everything
   * first (ContentResolver doesn't expose unregister-by-URI), then
   * re-registers each entry.
   */
  fun rebindTo(uris: Set<Uri>) {
    runCatching { contentResolver.unregisterContentObserver(observer) }
    registered.clear()
    uris.forEach { register(it) }
  }

  /** Tear down: unregister + quit the HandlerThread. */
  fun close() {
    runCatching { contentResolver.unregisterContentObserver(observer) }
    registered.clear()
    runCatching { thread.quitSafely() }
  }
}
