package com.eight87.tonearm.playback.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import com.eight87.tonearm.R

/**
 * Builds the [MediaNotification.Provider] used by `PlaybackService`.
 *
 * Phase E.1 / E.2 design note: starting with API 33 the System UI media
 * notification is populated directly from the active `MediaSession`'s
 * metadata + transport state — `MediaNotification.Provider` overrides
 * are only honored on pre-33 devices (per
 * `kb://android/media/media3/session/background-playback`). This means
 * the right path for "MediaStyle notification with album art and full
 * metadata" is to:
 *
 *   1. attach rich `MediaMetadata` (title / artist / album / artworkUri
 *      / artworkData) to every `MediaItem` we hand to the player;
 *   2. configure `MediaSession` button preferences for legacy /
 *      platform controllers via `MediaSession.Callback.onConnect`;
 *   3. only customize the *Provider* for things like the notification
 *      channel ID + name + importance — the bits that are still
 *      authored by us regardless of API level.
 *
 * Channel choice: `IMPORTANCE_LOW`, no sound, no vibration. The user
 * chose to play music; the OS already plays the audio — the
 * notification is a control surface, not an alert.
 */
@UnstableApi
object PlaybackNotificationProvider {

  /** Fixed channel id used by the playback notification. */
  const val CHANNEL_ID = "tonearm_playback"

  /** Notification id used by Media3 internally; exposed for tests. */
  const val NOTIFICATION_ID = 1001

  /**
   * Creates the channel (idempotent) and returns a configured
   * [DefaultMediaNotificationProvider]. Call this from
   * `MediaSessionService.onCreate` before
   * `setMediaNotificationProvider`.
   */
  fun build(context: Context): MediaNotification.Provider {
    ensureChannel(context)
    return DefaultMediaNotificationProvider.Builder(context)
      .setChannelId(CHANNEL_ID)
      .setChannelName(R.string.playback_notification_channel_name)
      .setNotificationId(NOTIFICATION_ID)
      .build()
  }

  /**
   * Create / upsert the notification channel ourselves. The default
   * provider also creates one, but it does so with the localized
   * library string "Now playing"; we want our own explicit name and
   * `IMPORTANCE_LOW` so the channel never makes sound and is reliably
   * named in the device's app-info screen.
   */
  private fun ensureChannel(context: Context) {
    val nm = context.getSystemService<NotificationManager>() ?: return
    if (nm.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Playback",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "Tonearm playback controls"
      setShowBadge(false)
      setSound(null, null)
      enableVibration(false)
    }
    nm.createNotificationChannel(channel)
  }

}
