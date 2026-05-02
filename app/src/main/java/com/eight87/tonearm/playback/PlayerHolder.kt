package com.eight87.tonearm.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide owner of the [ExoPlayer] instance used by [PlaybackService].
 *
 * Singleton-by-default for Phase B per the build plan; will be replaced by
 * constructor injection in Phase H if/when manual wiring becomes painful.
 *
 * The player is created lazily on first access and released via [release],
 * which the service calls from `onDestroy`.
 */
object PlayerHolder {
  @Volatile private var instance: ExoPlayer? = null

  /**
   * Phase H.4 — current audio session id of the active [ExoPlayer], or
   * [androidx.media3.common.C.AUDIO_SESSION_ID_UNSET] when no player
   * exists or no audio track has been prepared yet. Surfaced as a
   * StateFlow so [PlaybackUiController] can re-emit it for the system
   * EQ row without touching the ExoPlayer instance from the UI thread.
   */
  private val _audioSessionId = MutableStateFlow(C.AUDIO_SESSION_ID_UNSET)
  val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

  private val sessionIdListener = object : Player.Listener {
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
      _audioSessionId.value = audioSessionId
    }
  }

  /** Returns the process-wide [ExoPlayer], creating it on first call. */
  @UnstableApi
  fun getOrCreate(context: Context): ExoPlayer {
    val existing = instance
    if (existing != null) return existing
    synchronized(this) {
      val maybe = instance
      if (maybe != null) return maybe
      val created = build(context.applicationContext)
      instance = created
      // Capture the initial id (may be UNSET until ExoPlayer attaches
      // an AudioTrack) and listen for subsequent changes.
      _audioSessionId.value = created.audioSessionId
      created.addListener(sessionIdListener)
      return created
    }
  }

  /** Releases and clears the player. Safe to call multiple times. */
  fun release() {
    synchronized(this) {
      instance?.removeListener(sessionIdListener)
      instance?.release()
      instance = null
      _audioSessionId.value = C.AUDIO_SESSION_ID_UNSET
    }
  }

  @UnstableApi
  private fun build(appContext: Context): ExoPlayer {
    val audioAttributes =
      AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    return ExoPlayer.Builder(appContext)
      // Media3 handles audio focus via the player when handleAudioFocus = true.
      // It ducks on transient focus loss, pauses on permanent loss, and resumes
      // on focus regain (when willPauseWhenReady is set by the player itself).
      .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
      // Pauses playback when headphones are unplugged (ACTION_AUDIO_BECOMING_NOISY).
      .setHandleAudioBecomingNoisy(true)
      // Holds a partial wake lock + WifiLock during playback so the OS does not
      // suspend the audio thread when the screen is off.
      .setWakeMode(C.WAKE_MODE_LOCAL)
      .build()
  }
}
