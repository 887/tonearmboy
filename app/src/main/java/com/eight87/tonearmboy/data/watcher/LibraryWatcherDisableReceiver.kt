package com.eight87.tonearmboy.data.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.AppGraph
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * D.9d.2 — receiver invoked from the watcher's sticky notification.
 *
 * The notification advertises "tap to disable", which means we have to
 * do *both* things here:
 *   1. stop the foreground service (`LibraryWatcherService.stop`),
 *      removing the sticky notification.
 *   2. flip the persisted "Automatic reloading" setting back to off
 *      so the watcher does not re-arm on next process restart.
 *
 * The `GlobalScope.launch` is intentional — the receiver is a
 * short-lived component, the DataStore write is a single suspend
 * call, and we use `goAsync()` to keep the receiver alive long
 * enough for the write to complete.
 */
@UnstableApi
class LibraryWatcherDisableReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_DISABLE) return
    Log.i(TAG, "tap-to-disable received")

    val pending = goAsync()
    val appCtx = context.applicationContext
    LibraryWatcherService.stop(appCtx)

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    GlobalScope.launch {
      try {
        AppGraph.get(appCtx).settingsRepository.setAutomaticReloading(false)
      } catch (t: Throwable) {
        Log.w(TAG, "failed to flip setting: ${t.message}")
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    const val ACTION_DISABLE = "com.eight87.tonearmboy.action.WATCHER_DISABLE"
    private const val TAG = "tonearmboy-watcher-disable"
  }
}
