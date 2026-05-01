package com.eight87.tonearm.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

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
      return created
    }
  }

  /** Releases and clears the player. Safe to call multiple times. */
  fun release() {
    synchronized(this) {
      instance?.release()
      instance = null
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
