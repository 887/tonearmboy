package com.eight87.tonearmboy.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Convenience for the canonical Media3 pattern of connecting a
 * [MediaController] to the running [PlaybackService] from the UI layer.
 *
 * The returned `ListenableFuture` resolves with a connected controller
 * once the service has bound. Callers must release the controller
 * (`MediaController.releaseFuture(...)`) when their lifecycle ends.
 *
 * `MediaController.Builder` binds the service automatically. The actual
 * transition to a true foreground service (with the `MediaStyle`
 * notification that survives the activity going away) is wired in
 * Phase E — Phase B's responsibility is the service + session + player
 * scaffolding, not the polished notification.
 */
@UnstableApi
object PlaybackController {
  fun connect(context: Context): ListenableFuture<MediaController> {
    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    return MediaController.Builder(context, token).buildAsync()
  }
}
