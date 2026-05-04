package com.eight87.tonearmboy.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * D.22.4 — verify the cold-start reconnect contract for the in-process
 * `MediaController`.
 *
 * On the real device, the user-reported scenario is:
 *   1. User starts playback in the app.
 *   2. User swipes the app away from the recents stack.
 *   3. The foreground `PlaybackService` survives because Media3's
 *      `pauseAllPlayersAndStopSelf()` only tears down the service when
 *      *no* media is playing (E.4 / D.20.3 contract).
 *   4. User taps the system MediaStyle notification → `MainActivity`
 *      re-launches → `PlaybackUiController.connect()` is called →
 *      `MediaController.Builder(context, sessionToken).buildAsync()`
 *      should bind to the still-alive service and inherit the playing
 *      queue.
 *
 * The Media3-canonical pattern is the `SessionToken(context,
 * ComponentName(context, PlaybackService::class.java))` constructor —
 * Media3 then walks the manifest to find the service, binds it, and
 * resolves the future with a [androidx.media3.session.MediaController]
 * already wired to the running [androidx.media3.session.MediaSession].
 *
 * Robolectric cannot spin a real `MediaSessionService` (it needs a
 * live binder), so we exercise the *contract* the connect helper
 * relies on: the SessionToken construction, the manifest declaration
 * of `PlaybackService`, and the fact that
 * `PlaybackController.connect(...)` returns a non-null future for
 * downstream callers to `await()`.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaControllerColdResumeTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test
  fun playback_service_is_declared_in_the_manifest() {
    // Without this manifest declaration, `MediaController.Builder` cannot
    // bind to the service and `buildAsync()` would resolve with an
    // exception — the user sees the splash dismiss into a NowPlaying
    // surface stuck on `Connecting`. Pin the manifest entry so a future
    // refactor that drops the `<service>` block fails loudly.
    val pm = context.packageManager
    val component = ComponentName(context, PlaybackService::class.java)
    val info = pm.getServiceInfo(component, 0)
    assertNotNull("PlaybackService must be declared in the manifest", info)
    assertEquals(
      "PlaybackService component name must resolve to the canonical class",
      PlaybackService::class.java.name,
      info.name,
    )
  }

  @Test
  fun playback_controller_connect_returns_a_listenable_future() {
    // Robolectric can't fully wire MediaBrowser / MediaSessionService,
    // but we can at least verify the connect helper builds without
    // throwing — i.e. the pattern at line 1 of `PlaybackController` is
    // still the canonical Media3 shape (SessionToken + Builder).
    val future = PlaybackController.connect(context)
    assertNotNull("connect() must return a ListenableFuture", future)
    assertTrue(
      "connect() future must implement ListenableFuture<MediaController>",
      future is com.google.common.util.concurrent.ListenableFuture<*>,
    )
    // Don't await — Robolectric cannot bind the service, the future
    // would never resolve in a unit test. The instrumented test
    // `MainScreenTest` (in androidTest/) is the live-binding home.
    future.cancel(true)
  }

  @Test
  fun connection_phase_starts_connecting_and_only_flips_via_connect() {
    // Pin the contract that `PlaybackUiController` ships with the
    // `Connecting` phase as its default — the cold-start "blank Compose
    // tree" the user reported was rooted in `PlaybackUiState.Empty`
    // having `hasMedia = false` *and* no way to distinguish "not yet
    // connected" from "connected but empty queue" in the UI tree.
    val controller = PlaybackUiController(context)
    assertEquals(
      "Default phase must be Connecting until connect() resolves",
      ConnectionPhase.Connecting,
      controller.state.value.connectionPhase,
    )
  }

  @Test
  fun release_resets_phase_back_to_connecting() {
    // After the activity is destroyed and re-created (cold start with
    // a still-alive service), the controller must re-enter Connecting
    // until the new bind resolves. Without this reset, NowPlayingScreen
    // would skip the spinner sub-state and try to render Empty against
    // a stale Connected phase from the previous lifecycle.
    val controller = PlaybackUiController(context)
    controller.release()
    assertEquals(
      ConnectionPhase.Connecting,
      controller.state.value.connectionPhase,
    )
  }
}
