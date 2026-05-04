package com.eight87.tonearmboy.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.eight87.tonearmboy.data.db.LibraryDatabase
import com.eight87.tonearmboy.data.mediastore.MediaStoreScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Phase C smoke-test entry point. Runs a synchronous library scan and
 * dumps the result counts + a sample row to logcat. The
 * `scripts/library-smoke-test.sh` script greps for these tagged lines
 * to assert the fixtures were picked up.
 *
 * Triggered with:
 * ```
 * adb shell am broadcast \
 *   -a com.eight87.tonearmboy.action.LIBRARY_SCAN \
 *   -n com.eight87.tonearmboy/.data.LibraryScanReceiver
 * ```
 */
class LibraryScanReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext
    Log.i(TAG, "library-smoke: scan requested")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
      try {
        val scanner = MediaStoreScanner(app)
        val tracks = scanner.scanTracks()
        Log.i(TAG, "library-smoke: scanner returned ${tracks.size} tracks")
        for (t in tracks.take(MAX_DUMP)) {
          Log.i(
            TAG,
            "library-smoke: track id=${t.id} title=${t.title} artist=${t.artist} " +
              "album=${t.album} genre=${t.genre} data=${t.data}",
          )
        }

        // Round-trip through Room so we exercise the persistence path
        // the repository takes during a real scan.
        // R.A.5 — pass the real ScanConfigSource so the smoke test
        // exercises the same scan path production uses. The
        // `SettingsRepository` lives in `ui.settings`, but this is a
        // smoke-test receiver, not the library implementation, so
        // crossing the layer boundary here is fine.
        val repo = LibraryRepository(
          context = app,
          scanner = scanner,
          db = LibraryDatabase.get(app),
          externalScope = scope,
          scanConfig = com.eight87.tonearmboy.ui.settings.SettingsRepository(app),
        )
        repo.rescanNow()
        val cached = LibraryDatabase.get(app).trackDao().allIds()
        Log.i(TAG, "library-smoke: room cache holds ${cached.size} tracks")
        Log.i(TAG, "library-smoke: SCAN_COMPLETE")
      } catch (t: Throwable) {
        Log.e(TAG, "library-smoke: scan failed", t)
      }
    }
  }

  companion object {
    private const val TAG = "tonearmboy"
    private const val MAX_DUMP = 16
  }
}
