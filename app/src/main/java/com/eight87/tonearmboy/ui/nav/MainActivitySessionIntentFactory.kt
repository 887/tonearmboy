package com.eight87.tonearmboy.ui.nav

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.eight87.tonearmboy.MainActivity
import com.eight87.tonearmboy.playback.PlaybackService
import com.eight87.tonearmboy.playback.SessionActivityIntentFactory

/**
 * R.E.8 — production [SessionActivityIntentFactory] binding. Lives in
 * the UI layer because it imports [MainActivity]; [PlaybackService]
 * only sees the interface.
 */
internal object MainActivitySessionIntentFactory : SessionActivityIntentFactory {
  override fun nowPlayingPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
      putExtra(PlaybackService.EXTRA_DEEPLINK, PlaybackService.DEEPLINK_NOW_PLAYING)
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }
}
