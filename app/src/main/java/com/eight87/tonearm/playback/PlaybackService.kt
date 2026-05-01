package com.eight87.tonearm.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.eight87.tonearm.MainActivity

/**
 * Foreground media playback service.
 *
 * Hosts the process-wide [androidx.media3.exoplayer.ExoPlayer] (via
 * [PlayerHolder]) and a [MediaSession] that external controllers
 * (notification, lock screen, headset, Bluetooth, Auto, Wear, the in-app
 * `MediaController`) connect to.
 *
 * Notification: Media3's `MediaSessionService` provides a default
 * `MediaStyle` notification automatically once the session has playable
 * content, which is sufficient for the Phase B stub. Phase E replaces it
 * with a fully customised `MediaStyle` notification including album art.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

  private var mediaSession: MediaSession? = null

  override fun onCreate() {
    super.onCreate()
    val player = PlayerHolder.getOrCreate(this)
    mediaSession =
      MediaSession.Builder(this, player)
        // Tapping the system notification opens MainActivity.
        .setSessionActivity(buildSessionActivityPendingIntent())
        .build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    mediaSession

  override fun onTaskRemoved(rootIntent: Intent?) {
    // If the user swipes the app away while nothing is playing, stop the
    // service so we do not linger as a zombie foreground process.
    val player = mediaSession?.player
    if (player == null || (!player.playWhenReady) || player.mediaItemCount == 0) {
      stopSelf()
    }
  }

  override fun onDestroy() {
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
}
