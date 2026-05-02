package com.eight87.tonearm.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.eight87.tonearm.MainActivity
import com.eight87.tonearm.R
import com.eight87.tonearm.playback.notification.PlaybackNotificationProvider
import com.eight87.tonearm.ui.settings.CustomNotificationAction
import com.eight87.tonearm.ui.settings.SettingsRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        // D.23.2 — custom BitmapLoader that tries embedded picture
        // frame first, falls back to the MediaStore legacy album-art
        // content URI keyed off the EXTRA_MEDIA_STORE_ALBUM_ID extras
        // we already attach in `Track.toMediaItem`. Without this,
        // tracks with no embedded picture frame render no artwork in
        // SystemUI / lock screen / notification.
        .setBitmapLoader(TonearmBitmapLoader(applicationContext))
        .build()

    // D.20.3 — restore the persisted queue + position into the player
    // on cold start so the next in-app `MediaController` connect sees
    // the previous state. `MediaSession.Callback.onPlaybackResumption`
    // is only invoked by the system / Bluetooth resume request — an
    // in-app controller binding doesn't trigger it, which is why the
    // queue appeared empty after closing and reopening the app.
    //
    // We do this BEFORE attaching the persistence listener so the
    // restore itself doesn't immediately re-write the same JSON back
    // through `onTimelineChanged`.
    restorePersistedQueueIntoPlayer(player)

    player.addListener(QueuePersistenceListener())
    schedulePositionPersistTicker()

    // D.9a.2 — keep the notification's secondary CustomLayout action
    // button in sync with the user's setting. Re-running this on every
    // change re-runs `setCustomLayout`, which Media3 picks up and
    // re-renders the notification with.
    val settingsRepo = SettingsRepository(applicationContext)
    serviceScope.launch {
      settingsRepo.snapshot
        .map { it.customNotificationAction }
        .distinctUntilChanged()
        .collect { applyCustomNotificationLayout(it) }
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    mediaSession

  override fun onTaskRemoved(rootIntent: Intent?) {
    // D.20.3 — flush the latest queue + position synchronously before
    // the service tears down. The 500 ms debounce ticker may have a
    // pending unwritten position; without this flush, swiping the app
    // from recents loses the last few seconds of progress (the user-
    // reported queue + position regression).
    persistQueueSnapshotBlocking()
    // Media3-canonical behaviour: if the user swipes the app away
    // while nothing is playing, pause and tear down the foreground
    // service. While playing, we leave it running so audio keeps
    // going and the notification stays around as a control surface.
    pauseAllPlayersAndStopSelf()
  }

  override fun onDestroy() {
    // D.20.3 — synchronous flush before the player + session release,
    // mirroring `onTaskRemoved`. `runBlocking` is acceptable here:
    // service `onDestroy` runs on the main thread, the DataStore write
    // is small (single JSON string + two scalar prefs), and skipping
    // it loses user-visible state on a process exit.
    persistQueueSnapshotBlocking()
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

  /**
   * D.20.3 — synchronous variant of [persistQueueSnapshotAsync] used
   * during shutdown paths so the latest queue + position lands on disk
   * before the service goes away. We do not call this on every
   * transition (the async path is the hot path for Player.Listener
   * callbacks); only on `onTaskRemoved` and `onDestroy`.
   */
  private fun persistQueueSnapshotBlocking() {
    val player = mediaSession?.player ?: return
    val items = collectQueueEntries(player)
    val index = player.currentMediaItemIndex
    val position = player.currentPosition.coerceAtLeast(0)
    runBlocking {
      if (items.isEmpty()) {
        queuePersistence.clear()
      } else {
        queuePersistence.saveQueue(items, index)
        queuePersistence.savePosition(index, position)
      }
    }
    lastPersistedIndex = index
    lastPersistedPositionMs = position
  }

  private fun buildSessionActivityPendingIntent(): PendingIntent {
    // D.20.1 — when the user taps the MediaStyle notification, route them
    // to the Now Playing surface instead of whichever screen they were
    // last on. The deep-link extra is read by `MainActivity.handleIntent`
    // (which runs on both `onCreate` and `onNewIntent`) and pushes
    // `Destinations.NowPlaying` onto the back stack. We use
    // `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP` so a tap on
    // an already-running activity reuses it and dispatches `onNewIntent`
    // rather than spawning a duplicate.
    val intent = Intent(this, MainActivity::class.java).apply {
      putExtra(EXTRA_DEEPLINK, DEEPLINK_NOW_PLAYING)
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
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

  /**
   * D.20.3 — load the persisted snapshot synchronously and seed the
   * player with it (paused, seeked to the persisted position). Called
   * from `onCreate` exactly once per service lifetime. We do not start
   * playback on restore — the user explicitly closed the app, so the
   * canonical Auxio behaviour is "show the queue, don't auto-play".
   */
  private fun restorePersistedQueueIntoPlayer(player: Player) {
    val snapshot = try {
      runBlocking { queuePersistence.load() }
    } catch (t: Throwable) {
      android.util.Log.w("tonearm", "queue restore failed", t)
      return
    }
    if (snapshot.isEmpty()) return
    val resolved = snapshot.toMediaItemsWithStartPosition()
    player.setMediaItems(
      resolved.mediaItems,
      resolved.startIndex,
      resolved.startPositionMs,
    )
    // Don't auto-play; let the user press play. Calling `prepare()`
    // here is fine — Media3 buffers without producing audio until
    // `playWhenReady` flips, and the UI's pushState() reads current
    // metadata immediately so the mini-player surface populates.
    player.prepare()
    lastPersistedIndex = resolved.startIndex
    lastPersistedPositionMs = resolved.startPositionMs
    android.util.Log.i(
      "tonearm",
      "queue restored items=${snapshot.items.size} index=${resolved.startIndex} positionMs=${resolved.startPositionMs}",
    )
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
  /**
   * D.9a.2 — push the user's chosen secondary action button into the
   * `MediaStyle` notification via [MediaSession.setCustomLayout].
   *
   * Media3 surfaces the first one or two CommandButtons on the
   * notification compact view (Auxio places shuffle/repeat as the
   * second button to the right of play/pause). We add ours after
   * Media3's default play/pause so it shows in the same slot.
   *
   * Picking [CustomNotificationAction.None] passes an empty layout
   * which removes the button.
   */
  private fun applyCustomNotificationLayout(action: CustomNotificationAction) {
    val session = mediaSession ?: return
    val buttons = when (action) {
      CustomNotificationAction.RepeatMode -> listOf(
        CommandButton.Builder()
          .setDisplayName("Repeat")
          .setSessionCommand(SessionCommand(COMMAND_REPEAT_TOGGLE, Bundle.EMPTY))
          .setIconResId(R.drawable.ic_notif_repeat)
          .build(),
      )
      CustomNotificationAction.Shuffle -> listOf(
        CommandButton.Builder()
          .setDisplayName("Shuffle")
          .setSessionCommand(SessionCommand(COMMAND_SHUFFLE_TOGGLE, Bundle.EMPTY))
          .setIconResId(R.drawable.ic_notif_shuffle)
          .build(),
      )
      CustomNotificationAction.None -> emptyList()
    }
    session.setCustomLayout(buttons)
  }

  private inner class SessionCallback : MediaSession.Callback {

    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
      // D.23.1 — register the custom session commands so the
      // notification's secondary action button (and any caller
      // MediaController) can dispatch them.
      val available = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        .add(SessionCommand(COMMAND_REPEAT_TOGGLE, Bundle.EMPTY))
        .add(SessionCommand(COMMAND_SHUFFLE_TOGGLE, Bundle.EMPTY))
        .build()
      // D.23.1 — explicitly advertise the full default player command
      // set. Without this call, the System UI Quick Settings media card
      // sees an empty available-player-command set on connect and
      // silently drops repeat / shuffle / prev / next / play-pause
      // taps. `DEFAULT_PLAYER_COMMANDS` is Media3's canonical
      // "everything the player supports" preset and includes
      // COMMAND_SET_REPEAT_MODE + COMMAND_SET_SHUFFLE_MODE.
      return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailableSessionCommands(available)
        .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
        .build()
    }

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      val player = session.player
      return when (customCommand.customAction) {
        COMMAND_REPEAT_TOGGLE -> {
          player.repeatMode = nextRepeatMode(player.repeatMode)
          Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        COMMAND_SHUFFLE_TOGGLE -> {
          player.shuffleModeEnabled = !player.shuffleModeEnabled
          Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
      }
    }

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
      android.util.Log.i("tonearm", "onPlaybackResumption invoked isForPlayback=$isForPlayback")
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

  private fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
  }

  companion object {
    /** D.9a.2 custom session command identifiers. */
    const val COMMAND_REPEAT_TOGGLE = "com.eight87.tonearm.action.REPEAT_TOGGLE"
    const val COMMAND_SHUFFLE_TOGGLE = "com.eight87.tonearm.action.SHUFFLE_TOGGLE"

    /**
     * D.20.1 — extra carried on the `setSessionActivity` PendingIntent
     * so MainActivity can route the user to Now Playing on notification
     * tap instead of the last-active screen.
     */
    const val EXTRA_DEEPLINK = "tonearm.deeplink"
    const val DEEPLINK_NOW_PLAYING = "now_playing"
  }
}
