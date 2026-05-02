package com.eight87.tonearm.data.watcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.eight87.tonearm.AppGraph
import com.eight87.tonearm.MainActivity
import com.eight87.tonearm.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * D.9d.2 — low-priority foreground service that watches MediaStore and
 * every persisted SAF tree URI for changes and enqueues a debounced
 * [LibraryRescanWorker] on every notification.
 *
 * Why a foreground service rather than a plain registered observer:
 * Android imposes background-execution limits that revoke
 * `ContentObserver` callbacks once the app process is cached. The
 * "Automatic reloading" setting is opt-in, the user knows they're
 * paying for a sticky notification in exchange for the watcher
 * staying alive indefinitely.
 *
 * Foreground-service category: `dataSync` (Android 14+ requires a
 * type to be declared at startForeground time and at install time).
 * `mediaPlayback` is already used by [com.eight87.tonearm.playback.PlaybackService];
 * the watcher is not playing media so it does not qualify for that
 * category.
 */
@UnstableApi
class LibraryWatcherService : Service() {

  private val scope = CoroutineScope(SupervisorJob())

  private val observerThread by lazy {
    HandlerThread("tonearm-watcher").apply { start() }
  }
  private val observerHandler by lazy { Handler(observerThread.looper) }

  /** Single observer reused across every URI registration. */
  private val observer = object : ContentObserver(observerHandler) {
    override fun onChange(selfChange: Boolean, uri: Uri?) {
      Log.i(TAG, "onChange selfChange=$selfChange uri=$uri")
      enqueueDebouncedRescan(this@LibraryWatcherService)
    }
  }

  /** Track which URIs we've registered the observer against; needed for clean teardown. */
  private val registeredUris = mutableSetOf<Uri>()
  private var sourceWatchJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    ensureChannel(this)
    startForegroundCompat()
    registerMediaStoreObserver()
    watchSourceUris()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Sticky so the system keeps the watcher alive under memory pressure;
    // the user already opted in to the cost via Settings.
    return START_STICKY
  }

  override fun onDestroy() {
    Log.i(TAG, "onDestroy: unregistering observer + cancelling pending workers")
    sourceWatchJob?.cancel()
    runCatching { contentResolver.unregisterContentObserver(observer) }
    registeredUris.clear()
    runCatching { observerThread.quitSafely() }
    scope.cancel()
    // Cancel any pending debounced rescan so toggling the setting OFF
    // takes effect immediately, not 30 seconds from now.
    WorkManager.getInstance(this)
      .cancelUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME)
    super.onDestroy()
  }

  private fun startForegroundCompat() {
    val notification = buildNotification(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      // Android 14+: must specify the foreground service type at runtime.
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun registerMediaStoreObserver() {
    contentResolver.registerContentObserver(
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      /* notifyForDescendants = */ true,
      observer,
    )
    registeredUris += MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  }

  private fun watchSourceUris() {
    val settings = AppGraph.get(applicationContext).settingsRepository
    sourceWatchJob = scope.launch {
      settings.musicSourceUris.collectLatest { sources ->
        // Diff: drop observers for URIs we no longer track, register
        // observers for newly-added URIs.
        val targetUris = sources.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.toSet()
        // Always keep the MediaStore registration.
        val keep = registeredUris.filter {
          it == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI || it in targetUris
        }.toSet()
        // Unregister-then-register the observer to update the URI set.
        // ContentResolver does not expose "unregister-by-URI", so we
        // unregister all and re-register the keep + new sets — the
        // single observer instance is fine for both.
        runCatching { contentResolver.unregisterContentObserver(observer) }
        registeredUris.clear()
        // MediaStore + every (still-valid) source URI.
        registerMediaStoreObserver()
        for (sourceUri in targetUris) {
          runCatching {
            contentResolver.registerContentObserver(
              sourceUri,
              /* notifyForDescendants = */ true,
              observer,
            )
            registeredUris += sourceUri
          }.onFailure {
            Log.w(TAG, "failed to register observer for $sourceUri: ${it.message}")
          }
        }
        // Keep variable referenced.
        @Suppress("UNUSED_EXPRESSION") keep
        Log.i(TAG, "watching ${registeredUris.size} URIs (mediastore + ${targetUris.size} sources)")
      }
    }
  }

  companion object {
    private const val TAG = "tonearm-watcher"
    const val CHANNEL_ID = "tonearm.library.watcher"
    const val NOTIFICATION_ID = 4242
    const val NOTIFICATION_TITLE = "tonearm"
    const val NOTIFICATION_TEXT = "Watching for library changes. Tap to disable."

    /**
     * Idempotent — start the service if it isn't already running. Safe
     * to call from any context (uses an explicit Intent).
     */
    fun start(context: Context) {
      val intent = Intent(context, LibraryWatcherService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, LibraryWatcherService::class.java)
      context.stopService(intent)
    }

    /**
     * Enqueue the rescan worker as unique work with KEEP policy and a
     * 30-second initial delay. KEEP means a second observer firing
     * inside the debounce window is a no-op — the in-flight worker
     * already covers it.
     */
    fun enqueueDebouncedRescan(context: Context) {
      val request = OneTimeWorkRequestBuilder<LibraryRescanWorker>()
        .setInitialDelay(LibraryRescanWorker.DEBOUNCE_SECONDS, TimeUnit.SECONDS)
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(
        LibraryRescanWorker.UNIQUE_WORK_NAME,
        ExistingWorkPolicy.KEEP,
        request,
      )
    }

    private fun ensureChannel(context: Context) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (nm.getNotificationChannel(CHANNEL_ID) != null) return
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Library watcher",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Sticky notification while automatic reloading is enabled."
        setShowBadge(false)
      }
      nm.createNotificationChannel(channel)
    }

    /**
     * The sticky notification. Tap goes to [LibraryWatcherDisableReceiver]
     * which stops the service AND flips the persisted setting back to
     * off so the watcher does not re-arm on next boot.
     */
    fun buildNotification(context: Context): Notification {
      val disableIntent = Intent(context, LibraryWatcherDisableReceiver::class.java).apply {
        action = LibraryWatcherDisableReceiver.ACTION_DISABLE
      }
      val disablePending = PendingIntent.getBroadcast(
        context,
        /* requestCode = */ 0,
        disableIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      // Tap on the body opens the activity (so the user can confirm),
      // a separate "Disable" action does the stop + flip.
      val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val openPending = PendingIntent.getActivity(
        context,
        /* requestCode = */ 1,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      return NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(NOTIFICATION_TITLE)
        .setContentText(NOTIFICATION_TEXT)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setSilent(true)
        // Tap notification = disable directly (per the spec text "tap to disable").
        .setContentIntent(disablePending)
        .addAction(0, "Open tonearm", openPending)
        .addAction(0, "Disable", disablePending)
        .build()
    }
  }
}
