package com.eight87.tonearm.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.eight87.tonearm.MainActivity
import com.eight87.tonearm.playback.notification.PlaybackNotificationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground media playback service.
 *
 * Phase E responsibilities (on top of Phase B's scaffolding):
 *
 *  - **E.1 / E.2** install a [PlaybackNotificationProvider]-built
 *    `MediaNotification.Provider` so we own the notification channel
 *    and id; rely on Media3's default `MediaStyle` rendering and on
 *    the `MediaSession`'s metadata + transport state for the actual
 *    notification + lock-screen surface (System UI populates both
 *    from the session on API 33+).
 *  - **E.3** headset / Bluetooth media-button intents flow through
 *    the `MediaSession` automatically; we additionally declare the
 *    `androidx.media3.session.MediaButtonReceiver` in the manifest so
 *    the session survives process death (required by the Media3
 *    `onPlaybackResumption` contract).
 *  - **E.4** `onTaskRemoved` uses Media3's
 *    `pauseAllPlayersAndStopSelf()` so swiping the app from recents
 *    pauses + tears down the foreground service when nothing is
 *    actively playing.
 *  - **E.5** queue + position are persisted via
 *    [QueuePersistence]; on `onCreate` we restore into the player so
 *    the next controller connection sees the previous queue, and we
 *    implement `MediaSession.Callback.onPlaybackResumption` so
 *    Bluetooth / system-UI resume requests can rebuild state without
 *    a controller already being alive.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

  private var mediaSession: MediaSession? = null
  private lateinit var queuePersistence: QueuePersistence
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var positionPersistJob: Job? = null
  private var lastPersistedPositionMs: Long = -1L
  private var lastPersistedIndex: Int = -1

  override fun onCreate() {
    super.onCreate()
    queuePersistence = QueuePersistence(applicationContext)
    val player = PlayerHolder.getOrCreate(this)

    setMediaNotificationProvider(PlaybackNotificationProvider.build(this))

    mediaSession =
      MediaSession.Builder(this, player)
        .setSessionActivity(buildSessionActivityPendingIntent())
        .setCallback(SessionCallback())
        .build()

    player.addListener(QueuePersistenceListener())
    schedulePositionPersistTicker()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    mediaSession

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Media3-canonical behaviour: if the user swipes the app away
    // while nothing is playing, pause and tear down the foreground
    // service. While playing, we leave it running so audio keeps
    // going and the notification stays around as a control surface.
    pauseAllPlayersAndStopSelf()
  }

  override fun onDestroy() {
    positionPersistJob?.cancel()
    serviceScope.cancel()
    mediaSession?.run {
      player.release()
      release()
      mediaSession = null
    }
    PlayerHolder.release()
    super.onDestroy()
  }

  private fun buildSessionActivityPendingIntent(): PendingIntent {
    val intent = Intent(this, MainActivity::class.java)
    return PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }

  // -- Persistence + restoration ---------------------------------------------

  /**
   * Listener attached to the player. Persists the queue on every
   * mediaItem-list change and the current index on every transition.
   * Position is debounced via [schedulePositionPersistTicker].
   */
  private inner class QueuePersistenceListener : Player.Listener {

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
      if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
      persistQueueSnapshotAsync()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      persistQueueSnapshotAsync()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      if (playbackState == Player.STATE_ENDED) {
        persistPositionImmediate()
      }
    }
  }

  private fun persistQueueSnapshotAsync() {
    val player = mediaSession?.player ?: return
    val items = collectQueueEntries(player)
    val index = player.currentMediaItemIndex
    val position = player.currentPosition.coerceAtLeast(0)
    serviceScope.launch {
      if (items.isEmpty()) {
        queuePersistence.clear()
      } else {
        queuePersistence.saveQueue(items, index)
        queuePersistence.savePosition(index, position)
      }
      lastPersistedIndex = index
      lastPersistedPositionMs = position
    }
  }

  private fun persistPositionImmediate() {
    val player = mediaSession?.player ?: return
    val index = player.currentMediaItemIndex
    val position = player.currentPosition.coerceAtLeast(0)
    serviceScope.launch {
      queuePersistence.savePosition(index, position)
      lastPersistedIndex = index
      lastPersistedPositionMs = position
    }
  }

  /**
   * Coroutine ticker that persists the current playback position every
   * [QueuePersistence.POSITION_DEBOUNCE_MS] ms while the player is
   * playing — and only if the position has actually advanced. This
   * keeps a process-kill at any random moment within a couple seconds
   * of the user-visible position.
   */
  private fun schedulePositionPersistTicker() {
    positionPersistJob?.cancel()
    positionPersistJob = serviceScope.launch {
      while (true) {
        delay(QueuePersistence.POSITION_DEBOUNCE_MS)
        val player = mediaSession?.player ?: continue
        if (!player.isPlaying) continue
        val index = player.currentMediaItemIndex
        val position = player.currentPosition.coerceAtLeast(0)
        if (index == lastPersistedIndex && position == lastPersistedPositionMs) continue
        queuePersistence.savePosition(index, position)
        lastPersistedIndex = index
        lastPersistedPositionMs = position
      }
    }
  }

  private fun collectQueueEntries(player: Player): List<QueuePersistence.Entry> {
    val count = player.mediaItemCount
    if (count == 0) return emptyList()
    val out = ArrayList<QueuePersistence.Entry>(count)
    for (i in 0 until count) {
      out += QueuePersistence.fromMediaItem(player.getMediaItemAt(i))
    }
    return out
  }

  /**
   * `MediaSession.Callback` that wires Media3's playback-resumption
   * contract: when a Bluetooth headset / Android System UI resumption
   * action wakes the service from cold, hand back the persisted
   * queue + position so Media3 can rebuild the player.
   *
   * The callback is also responsible for calling default behaviour
   * via the empty-init path of `AcceptedResultBuilder` for normal
   * (non-resumption) connects — Media3 takes care of the rest.
   */
  private inner class SessionCallback : MediaSession.Callback {

    override fun onPlaybackResumption(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
      // Run blocking on the main thread is fine here — DataStore reads
      // are tiny and `onPlaybackResumption` is allowed to return a
      // pre-resolved future. Media3 documents a "complete the future
      // as quickly as possible" requirement; the JSON we parse is at
      // most a few KB.
      return try {
        val snapshot = runBlocking { queuePersistence.load() }
        if (snapshot.isEmpty()) {
          Futures.immediateFailedFuture(IllegalStateException("no persisted queue"))
        } else {
          Futures.immediateFuture(snapshot.toMediaItemsWithStartPosition())
        }
      } catch (t: Throwable) {
        Futures.immediateFailedFuture(t)
      }
    }
  }
}
