package com.eight87.tonearmboy.playback

import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.CustomBarAction
import com.eight87.tonearmboy.ui.settings.CustomNotificationAction
import com.eight87.tonearmboy.ui.settings.PlayFromItemDetails
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary
import com.eight87.tonearmboy.ui.settings.ReplayGainStrategy
import kotlinx.coroutines.flow.StateFlow

/**
 * R.C.1 — narrow facet interfaces over [PlaybackUiController].
 *
 * Each consumer takes only the slice it reads, not the wholesale
 * controller. Mirrors R.A.2 (LibraryRepository → 8 narrow data
 * sources) and R.B.3 (SettingsRepository → 5 facet interfaces) at
 * the playback layer.
 *
 * `PlaybackUiController` implements every facet. The composition
 * root (`AppGraph`) hands the appropriate facet to each consumer:
 *
 *   - `MiniPlayer` already takes function-typed params — no change.
 *   - `NowPlayingScreen` takes [NowPlayingState] + [TransportCommands]
 *     + [QueueCommands].
 *   - `SettingsAudioScreen` (Sleep timer + System EQ) takes the
 *     subset it needs — [NowPlayingState] for `audioSessionId`,
 *     `SleepTimer` directly (R.C.5 moves it out of the controller).
 *   - `TonearmboyApp`'s settings → playback `LaunchedEffect` mirrors
 *     keep using the wholesale controller for now; R.E.6 lifts them
 *     into a `rememberPlaybackSettingsBridge(playback, settings)`
 *     helper that takes [TransportCommands] + [ReplayGainCommands].
 *
 * Read-vs-write split: [NowPlayingState] is observation only; the
 * three command interfaces are imperative side effects on the
 * MediaController. A composable that only renders state should not
 * be able to issue transport commands by accident.
 */

/** R.C.1 — Read surface for any consumer that observes playback. */
interface NowPlayingState {
  val state: StateFlow<PlaybackUiState>
  val queue: StateFlow<QueueSnapshot>
  /** Active audio session id; -1 when no MediaController is bound. */
  val audioSessionId: StateFlow<Int>
}

/**
 * R.C.1 — Transport (play / pause / seek / skip / shuffle / repeat
 * + the start-from-X helpers + the customisable bar / notification
 * actions). No queue mutation here — see [QueueCommands].
 */
interface TransportCommands {
  fun togglePlayPause()
  fun seekTo(positionMs: Long)
  fun seekBackward()
  fun seekForward()
  fun seekToPrevious()
  fun seekToNext()
  fun stop()
  fun toggleShuffle()
  fun cycleRepeatMode()
  /** D.9a.3 — playback-controller mirror of the user's pause-on-repeat setting. */
  fun setPauseOnRepeat(enabled: Boolean)
  fun playTrack(track: Track)
  fun playQueue(tracks: List<Track>, index: Int = 0)
  /**
   * D.9a.4 — start playback from a list-context tap. [allSongs] is
   * the canonical "everything in the library" set used by the
   * `AllSongs` strategy when the surrounding list is itself filtered.
   */
  fun playFromLibrary(
    surroundingList: List<Track>,
    tappedIndex: Int,
    strategy: PlayFromLibrary,
    allSongs: List<Track> = surroundingList,
  )
  /** D.9a.5 — start playback from a detail-surface tap (album / artist / playlist). */
  fun playFromDetail(
    surroundingList: List<Track>,
    tappedIndex: Int,
    strategy: PlayFromItemDetails,
  )
  fun performCustomBarAction(action: CustomBarAction)
  fun performCustomNotificationAction(action: CustomNotificationAction)
}

/**
 * R.C.1 — Queue mutation surface. `seekToQueueIndex` lives here too
 * because every "jump to queue position" use site is from the queue
 * UI, not from the transport row.
 */
interface QueueCommands {
  fun addToQueue(track: Track)
  fun seekToQueueIndex(index: Int)
  fun removeQueueItem(index: Int)
  /**
   * Phase F — bulk removal by media-id. Used by the file-deletion
   * flow to drop deleted tracks out of the live queue without
   * walking each index. Returns the number of items removed.
   */
  fun removeQueueItemsByMediaIds(deletedMediaIds: Set<String>): Int
  fun moveQueueItem(from: Int, to: Int)
}

/**
 * R.C.1 — ReplayGain settings-mirror surface. The two flag-mirror
 * settings (strategy + pre-amp dB) push through here and the
 * controller re-applies the volume immediately + on every track
 * transition. R.C.4 will extract the actual computation into a
 * dedicated `ReplayGainController(library, scope)`; this interface
 * is the consumer-facing contract for both before and after that
 * extraction.
 */
interface ReplayGainCommands {
  fun setReplayGain(strategy: ReplayGainStrategy, preampDb: Float)
}
