package com.eight87.tonearmboy.playback

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.ui.settings.CustomNotificationAction
import com.eight87.tonearmboy.ui.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * R.F.11 — owns the [MediaSession.setCustomLayout] dance that keeps
 * the notification's secondary action button in sync with the user's
 * "Custom notification action" setting. (Playback-F7.) Lifted out of
 * `PlaybackService` so the service's `onCreate` is wiring only.
 */
@UnstableApi
internal class NotificationLayoutController(
  private val settings: SettingsRepository,
  private val scope: CoroutineScope,
) {

  private var collectJob: Job? = null

  /**
   * Start collecting the user's chosen secondary action; on every
   * change, call [MediaSession.setCustomLayout] with the matching
   * [CommandButton] list. [None][CustomNotificationAction.None] passes
   * an empty layout which removes the button.
   *
   * Cancels any previous collection — calling [start] twice is safe.
   */
  fun start(session: MediaSession) {
    collectJob?.cancel()
    collectJob = scope.launch {
      settings.customNotificationAction.flow
        .distinctUntilChanged()
        .collect { applyLayout(session, it) }
    }
  }

  fun stop() {
    collectJob?.cancel()
    collectJob = null
  }

  private fun applyLayout(session: MediaSession, action: CustomNotificationAction) {
    val buttons = when (action) {
      CustomNotificationAction.RepeatMode -> listOf(
        CommandButton.Builder()
          .setDisplayName("Repeat")
          .setSessionCommand(SessionCommand(PlaybackService.COMMAND_REPEAT_TOGGLE, Bundle.EMPTY))
          .setIconResId(R.drawable.ic_notif_repeat)
          .build(),
      )
      CustomNotificationAction.Shuffle -> listOf(
        CommandButton.Builder()
          .setDisplayName("Shuffle")
          .setSessionCommand(SessionCommand(PlaybackService.COMMAND_SHUFFLE_TOGGLE, Bundle.EMPTY))
          .setIconResId(R.drawable.ic_notif_shuffle)
          .build(),
      )
      CustomNotificationAction.None -> emptyList()
    }
    session.setCustomLayout(buttons)
  }
}
