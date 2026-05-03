package com.eight87.tonearm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.playback.PlaybackService
import com.eight87.tonearm.theme.LocalAlbumPalette
import com.eight87.tonearm.theme.TonearmTheme
import com.eight87.tonearm.ui.nav.TonearmApp
import com.eight87.tonearm.ui.permission.RequireAudioPermission
import com.eight87.tonearm.ui.permission.RequirePostNotifications
import com.eight87.tonearm.data.watcher.LibraryWatcherService
import com.eight87.tonearm.ui.settings.BaseTheme
import com.eight87.tonearm.ui.settings.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
class MainActivity : ComponentActivity() {

  /**
   * D.20.1 — back-channel from `onNewIntent` (and the initial
   * `onCreate` intent) into the Compose tree. The TonearmApp
   * composable observes [deeplinkNonce] and pushes
   * `Destinations.NowPlaying` onto its back stack whenever the
   * value changes. We use a monotonically-increasing nonce so
   * re-receiving the same intent (e.g. the user taps the
   * notification twice) always produces a fresh recomposition.
   */
  private val deeplinkNonce = mutableStateOf(0)
  private val pendingDeeplink = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    // D.17.2 — install the SplashScreen-API hand-off BEFORE
    // super.onCreate. The 1.x compat library wires the Theme.SplashScreen
    // parent → postSplashScreenTheme transition; calling it any later
    // produces a brief white flash on Android 12+ as the activity
    // window swaps backgrounds.
    val splash = installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val graph = AppGraph.get(applicationContext)

    // D.22.2 — hold the system splash on screen until either the
    // Media3 controller has bound to the running PlaybackService or a
    // 600 ms safety-net timeout fires. This is the cold-start
    // coordination that prevents the "blank black activity" frame
    // race the user reported when the activity is killed but the
    // foreground service is still alive — without this hold, the
    // splash dismisses, NowPlaying mounts with `connectionPhase ==
    // Connecting`, and the user briefly sees a Compose tree that has
    // not yet learned about the playing track. The 600 ms cap is the
    // failsafe so a stuck `buildAsync` future cannot pin the splash.
    val splashHold = java.util.concurrent.atomic.AtomicBoolean(true)
    splash.setKeepOnScreenCondition { splashHold.get() }
    graph.applicationScope.launch {
      withTimeoutOrNull(SPLASH_HOLD_TIMEOUT_MS) {
        // The connect() call is also kicked off by TonearmApp's
        // LaunchedEffect, but we trigger it here too so the splash
        // hold doesn't depend on the Compose tree mounting first.
        // Both invocations are idempotent.
        graph.applicationScope.launch { graph.playbackUiController.connect() }
        graph.playbackUiController.awaitConnected()
      }
      splashHold.set(false)
    }

    // D.9d.2 — re-arm the watcher service if Automatic reloading was
    // on across a process restart. The service is the only owner of
    // the sticky notification + observers; toggling the setting in
    // the UI also start/stops it directly, this branch covers cold
    // start / boot.
    graph.applicationScope.launch {
      val on = graph.settingsRepository.automaticReloading.flow.first()
      if (on) LibraryWatcherService.start(applicationContext)
    }

    // D.17.3.6 — write the default music-source mode on a fresh
    // install so the first launch scans MediaStore (and therefore
    // populates the library) without the user opening a settings page.
    graph.applicationScope.launch {
      graph.settingsRepository.firstLaunchInitialise()
    }

    // D.20.1 — read the launch intent for the notification deeplink.
    handleIntent(intent)

    setContent {
      // R.B.5 — per-key Flow reads. Each subscription is its own
      // narrow stream so toggling, e.g., albumArtTintEnabled doesn't
      // recompose the theme-mode `when` block.
      val theme by graph.settingsRepository.theme.flow
        .collectAsState(initial = ThemePreference.Default)
      val baseTheme by graph.settingsRepository.baseTheme.flow
        .collectAsState(initial = BaseTheme.Default)
      val albumArtTintEnabled by graph.settingsRepository.albumArtTintEnabled.flow
        .collectAsState(initial = true)
      val systemDark = isSystemInDarkTheme()
      val resolvedDark = when (theme) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
      }

      // D.20.4 — observe the playback state so we can refresh the
      // album palette whenever the playing track changes.
      val playbackState by graph.playbackUiController.state.collectAsState()
      LaunchedEffect(playbackState.mediaStoreAlbumId) {
        graph.albumPaletteSource.setAlbumId(playbackState.mediaStoreAlbumId)
      }
      val albumPalette by graph.albumPaletteSource.palette.collectAsState()

      CompositionLocalProvider(LocalAlbumPalette provides albumPalette) {
        TonearmTheme(
          darkTheme = resolvedDark,
          baseTheme = baseTheme,
          albumArtTintEnabled = albumArtTintEnabled,
        ) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // D.19 — wrap the app in the runtime-permission gate so a
            // fresh install on a real phone walks the system grant flow
            // for READ_MEDIA_AUDIO. On grant we trigger a full library
            // rescan immediately so the user doesn't need to find a
            // "Rescan music" button in Settings to populate the library.
            RequireAudioPermission(
              onGranted = {
                graph.applicationScope.launch {
                  graph.scanner.rescanNow()
                }
              },
            ) {
              // D.23.3 — POST_NOTIFICATIONS runtime gate. API 33+ only;
              // pass-through on earlier versions. Denial does not block
              // playback; only the in-tray notification ribbon is
              // affected (Quick Settings + lock screen are
              // MediaSession-driven and don't need POST_NOTIFICATIONS).
              RequirePostNotifications {
                TonearmApp(
                  graph = graph,
                  deeplinkNonce = deeplinkNonce.value,
                  pendingDeeplink = pendingDeeplink.value,
                  onDeeplinkConsumed = { pendingDeeplink.value = null },
                )
              }
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  /**
   * D.20.1 — extract the `tonearm.deeplink` extra and bump the nonce
   * so the Compose layer reacts. The actual back-stack push happens
   * in `TonearmApp` because that's where the `TonearmBackStack`
   * lives.
   */
  internal fun handleIntent(intent: Intent?) {
    val deeplink = intent?.getStringExtra(PlaybackService.EXTRA_DEEPLINK) ?: return
    pendingDeeplink.value = deeplink
    deeplinkNonce.value = deeplinkNonce.value + 1
  }

  companion object {
    /**
     * D.22.2 — splash safety-net timeout. 600 ms is the canonical
     * Android guidance for "long enough to hide a fast handshake,
     * short enough to never feel like a hang." Beyond this, even on
     * a stuck `MediaController.Builder.buildAsync`, the splash
     * dismisses and the user lands on the connecting NowPlaying
     * sub-state (D.22.3) which is its own progress indicator.
     */
    internal const val SPLASH_HOLD_TIMEOUT_MS: Long = 600L
  }
}
