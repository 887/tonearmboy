package com.eight87.tonearmboy.data.watcher

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eight87.tonearmboy.AppGraph

/**
 * D.9d.2 — debounced rescan worker.
 *
 * The watcher service registers a [android.database.ContentObserver]
 * on [android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI]
 * and on every persisted SAF tree URI; on every `onChange` it
 * enqueues this worker as unique work named [UNIQUE_WORK_NAME] under
 * [androidx.work.ExistingWorkPolicy.KEEP] with a 30-second initial
 * delay. The combination of "keep" (skip new requests while one is
 * pending) plus the initial delay coalesces a flurry of changes —
 * e.g. a user pasting a directory of tracks — into a single rescan.
 *
 * The worker delegates to [com.eight87.tonearmboy.data.LibraryRepository.rescanNow],
 * which already does the right "diff-and-apply" work used by every
 * other rescan trigger (the in-process MediaStore observer and the
 * Settings > Library > Rescan music action).
 */
@UnstableApi
class LibraryRescanWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    return try {
      AppGraph.get(applicationContext).scanner.rescanNow()
      Log.i(TAG, "rescanNow completed")
      Result.success()
    } catch (t: Throwable) {
      Log.w(TAG, "rescanNow failed: ${t.message}")
      // Retry once — transient permission / IO failures recover quickly.
      if (runAttemptCount < 1) Result.retry() else Result.failure()
    }
  }

  companion object {
    /** Stable work name. The watcher uses `KEEP` policy on this name. */
    const val UNIQUE_WORK_NAME = "tonearmboy.library.rescan"
    /** Debounce window before the worker actually runs. */
    const val DEBOUNCE_SECONDS = 30L
    private const val TAG = "tonearmboy-watcher-worker"
  }
}
