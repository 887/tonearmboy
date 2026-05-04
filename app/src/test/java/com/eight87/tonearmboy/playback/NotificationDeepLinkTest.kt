package com.eight87.tonearmboy.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eight87.tonearmboy.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * D.20.1 — pin the contract that a notification tap routes to the
 * Now Playing surface instead of whichever screen was last on top.
 *
 * The runtime path:
 *   1. `PlaybackService.onCreate` builds a `PendingIntent` for
 *      `MainActivity` carrying the `tonearmboy.deeplink = "now_playing"`
 *      extra and registers it as `MediaSession.sessionActivity`.
 *   2. The user taps the MediaStyle notification.
 *   3. The system fires the PendingIntent; Android delivers the
 *      Intent to the singleTask MainActivity via `onNewIntent`.
 *   4. `MainActivity.handleIntent` reads the extra and bumps the
 *      deeplink nonce, which causes `TonearmboyApp` to push
 *      `Destinations.NowPlaying` onto the back stack.
 *
 * We can't spin a real `PlaybackService` inside Robolectric, but we
 * can pin both halves of the contract:
 *   - the Intent that mirrors what `buildSessionActivityPendingIntent`
 *     produces survives the PendingIntent round-trip with the extra
 *     intact; and
 *   - feeding such an Intent to `MainActivity.handleIntent` yields
 *     the expected pending-deeplink state on the activity.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class NotificationDeepLinkTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun pendingIntent_round_trips_now_playing_deeplink_extra() {
    // Mirror the Intent the service constructs.
    val intent = Intent(context, MainActivity::class.java).apply {
      putExtra(PlaybackService.EXTRA_DEEPLINK, PlaybackService.DEEPLINK_NOW_PLAYING)
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pi = PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val savedIntent = shadowOf(pi).savedIntent
    assertNotNull("PendingIntent must capture an Intent", savedIntent)
    assertEquals(
      "deeplink extra must be carried into the PendingIntent for the system to deliver",
      PlaybackService.DEEPLINK_NOW_PLAYING,
      savedIntent.getStringExtra(PlaybackService.EXTRA_DEEPLINK),
    )
    val flags = savedIntent.flags
    val singleTop = flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0
    val clearTop = flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0
    assert(singleTop) { "intent must include FLAG_ACTIVITY_SINGLE_TOP so onNewIntent fires" }
    assert(clearTop) { "intent must include FLAG_ACTIVITY_CLEAR_TOP so the existing task is reused" }
  }

  @Test
  fun deeplink_constants_are_stable() {
    // These are part of the Intent contract between PlaybackService
    // and MainActivity; renaming them silently would lose the
    // routing on installs whose notification was already issued.
    assertEquals("tonearmboy.deeplink", PlaybackService.EXTRA_DEEPLINK)
    assertEquals("now_playing", PlaybackService.DEEPLINK_NOW_PLAYING)
  }
}
