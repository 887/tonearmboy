package com.eight87.tonearmboy.ui.settings

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Phase H.4 — System equalizer hand-off.
 *
 * Builds the canonical `ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`
 * intent the platform exposes for app-side control of system effects.
 * Stripped-down ROMs (some Pixel-AOSP variants, GrapheneOS without
 * vendor blobs, certain micro-ROMs) ship no handler — the caller
 * should branch on [resolves] before launching.
 */
object SystemEqualizer {
  /**
   * Build the intent. [audioSessionId] should be the active
   * [androidx.media3.exoplayer.ExoPlayer.audioSessionId] when known;
   * pass `AudioEffect.ERROR` (or `0`) only for tests — production
   * callers must wait for a real session id.
   */
  fun buildIntent(
    audioSessionId: Int,
    packageName: String,
  ): Intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  /** True iff the system has an activity that handles the EQ intent. */
  fun resolves(context: Context, intent: Intent): Boolean {
    val pm = context.packageManager
    return intent.resolveActivity(pm) != null
  }
}
