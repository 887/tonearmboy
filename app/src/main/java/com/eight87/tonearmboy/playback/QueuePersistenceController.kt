package com.eight87.tonearmboy.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * R.F.11 — controller that owns every queue-persistence concern that
 * used to live inline in [PlaybackService]: snapshot writes via
 * [QueuePersistence], restore on cold start, the periodic position
 * ticker, the synchronous flush during shutdown, and the
 * [Player.Listener] that re-runs the snapshot on every queue / index
 * / shuffle / repeat change. (Playback-F7.)
 */
@UnstableApi
internal class QueuePersistenceController(
  private val storage: QueuePersistence,
  private val scope: CoroutineScope,
) {

  private var positionTickerJob: Job? = null
  private var lastPersistedIndex: Int = -1
  private var lastPersistedPositionMs: Long = -1L

  /**
   * Cold-start restore: load the persisted snapshot and seed [player]
   * (paused, seeked to the persisted position). Restore shuffle / repeat
   * too. **Call this BEFORE [attachListener]** so the seed itself
   * doesn't echo back through the listener and immediately re-write the
   * same JSON.
   */
  fun restoreInto(player: Player) {
    val snapshot = try {
      runBlocking { storage.load() }
    } catch (t: Throwable) {
      android.util.Log.w(TAG, "queue restore failed", t)
      return
    }
    if (!snapshot.isEmpty()) {
      val resolved = snapshot.toMediaItemsWithStartPosition()
      player.setMediaItems(
        resolved.mediaItems,
        resolved.startIndex,
        resolved.startPositionMs,
      )
      player.prepare()
      lastPersistedIndex = resolved.startIndex
      lastPersistedPositionMs = resolved.startPositionMs
      android.util.Log.i(
        TAG,
        "queue restored items=${snapshot.items.size} index=${resolved.startIndex} positionMs=${resolved.startPositionMs}",
      )
    }
    runCatching {
      runBlocking {
        player.shuffleModeEnabled = storage.loadShuffle()
        player.repeatMode = storage.loadRepeatMode()
      }
    }.onFailure { android.util.Log.w(TAG, "shuffle/repeat restore failed", it) }
  }

  /**
   * Returns a [Player.Listener] that persists the queue + position +
   * shuffle/repeat on every relevant change. The caller is responsible
   * for `player.addListener(...)` and (on shutdown) un-registering it
   * via the player's `release()`.
   */
  fun listener(playerProvider: () -> Player?): Player.Listener =
    object : Player.Listener {
      override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        persistAsync(playerProvider())
      }
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        persistAsync(playerProvider())
      }
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) persistPositionImmediate(playerProvider())
      }
      override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        scope.launch { storage.saveShuffle(shuffleModeEnabled) }
      }
      override fun onRepeatModeChanged(repeatMode: Int) {
        scope.launch { storage.saveRepeatMode(repeatMode) }
      }
    }

  /** Start the periodic position ticker. Cancels any previous ticker first. */
  fun startPositionTicker(playerProvider: () -> Player?) {
    positionTickerJob?.cancel()
    positionTickerJob = scope.launch {
      while (true) {
        delay(QueuePersistence.POSITION_DEBOUNCE_MS)
        val player = playerProvider() ?: continue
        if (!player.isPlaying) continue
        val index = player.currentMediaItemIndex
        val position = player.currentPosition.coerceAtLeast(0)
        if (index == lastPersistedIndex && position == lastPersistedPositionMs) continue
        storage.savePosition(index, position)
        lastPersistedIndex = index
        lastPersistedPositionMs = position
      }
    }
  }

  /** Synchronous flush — used from `onTaskRemoved` / `onDestroy`. */
  fun flushSync(player: Player?) {
    if (player == null) return
    val items = collectQueueEntries(player)
    val index = player.currentMediaItemIndex
    val position = player.currentPosition.coerceAtLeast(0)
    runBlocking {
      if (items.isEmpty()) {
        storage.clear()
      } else {
        storage.saveQueue(items, index)
        storage.savePosition(index, position)
      }
    }
    lastPersistedIndex = index
    lastPersistedPositionMs = position
  }

  fun cancel() {
    positionTickerJob?.cancel()
    positionTickerJob = null
  }

  /**
   * Synchronous load for `MediaSession.Callback.onPlaybackResumption`.
   * Returns null when no persisted queue exists. Caller wraps the
   * result in a `ListenableFuture` per Media3's contract.
   */
  fun loadResumptionSnapshot(): MediaSession.MediaItemsWithStartPosition? {
    val snapshot = runBlocking { storage.load() }
    if (snapshot.isEmpty()) return null
    return snapshot.toMediaItemsWithStartPosition()
  }

  private fun persistAsync(player: Player?) {
    if (player == null) return
    val items = collectQueueEntries(player)
    val index = player.currentMediaItemIndex
    val position = player.currentPosition.coerceAtLeast(0)
    scope.launch {
      if (items.isEmpty()) {
        storage.clear()
      } else {
        storage.saveQueue(items, index)
        storage.savePosition(index, position)
      }
      lastPersistedIndex = index
      lastPersistedPositionMs = position
    }
  }

  private fun persistPositionImmediate(player: Player?) {
    if (player == null) return
    val index = player.currentMediaItemIndex
    val position = player.currentPosition.coerceAtLeast(0)
    scope.launch {
      storage.savePosition(index, position)
      lastPersistedIndex = index
      lastPersistedPositionMs = position
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

  companion object {
    private const val TAG = "tonearmboy-queue-persist"
  }
}
