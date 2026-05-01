package com.eight87.tonearm.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Receiver that drives a Phase B smoke test of the playback stack.
 *
 * Triggered with:
 * ```
 * adb shell am broadcast \
 *   -a com.eight87.tonearm.action.SMOKE_PLAY \
 *   -n com.eight87.tonearm/.playback.SmokeTestReceiver \
 *   --es path /sdcard/Music/test.mp3
 * ```
 *
 * Connects a [androidx.media3.session.MediaController] to the running
 * [PlaybackService], sets a single [MediaItem] from the supplied path,
 * starts playback, and logs every [Player.STATE_READY] transition to
 * `logcat` under the `tonearm` tag. The smoke-test script greps for
 * those log lines to assert codec readiness.
 */
@UnstableApi
class SmokeTestReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val path = intent.getStringExtra(EXTRA_PATH)
    if (path.isNullOrBlank()) {
      Log.w(TAG, "smoke: missing 'path' extra")
      return
    }
    val uri = Uri.fromFile(java.io.File(path))
    Log.i(TAG, "smoke: requesting playback of $uri")

    val future = PlaybackController.connect(context.applicationContext)
    future.addListener(
      {
        try {
          val controller = future.get()
          controller.addListener(
            object : Player.Listener {
              override fun onPlaybackStateChanged(state: Int) {
                Log.i(TAG, "smoke: playbackState=${stateName(state)}")
                if (state == Player.STATE_READY) {
                  Log.i(TAG, "smoke: STATE_READY for $uri")
                }
              }

              override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "smoke: player error: ${error.errorCodeName}", error)
              }
            }
          )
          controller.setMediaItem(MediaItem.fromUri(uri))
          controller.prepare()
          controller.playWhenReady = true
        } catch (t: Throwable) {
          Log.e(TAG, "smoke: failed to obtain MediaController", t)
        }
      },
      Runnable::run,
    )
  }

  private fun stateName(state: Int): String =
    when (state) {
      Player.STATE_IDLE -> "IDLE"
      Player.STATE_BUFFERING -> "BUFFERING"
      Player.STATE_READY -> "READY"
      Player.STATE_ENDED -> "ENDED"
      else -> "UNKNOWN($state)"
    }

  companion object {
    private const val TAG = "tonearm"
    const val EXTRA_PATH = "path"
  }
}
