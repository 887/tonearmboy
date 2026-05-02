package com.eight87.tonearm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.theme.TonearmTheme
import com.eight87.tonearm.ui.nav.TonearmApp
import com.eight87.tonearm.data.watcher.LibraryWatcherService
import com.eight87.tonearm.ui.settings.ColorScheme
import com.eight87.tonearm.ui.settings.SettingsSnapshot
import com.eight87.tonearm.ui.settings.ThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // D.17.2 — install the SplashScreen-API hand-off BEFORE
    // super.onCreate. The 1.x compat library wires the Theme.SplashScreen
    // parent → postSplashScreenTheme transition; calling it any later
    // produces a brief white flash on Android 12+ as the activity
    // window swaps backgrounds.
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val graph = AppGraph.get(applicationContext)

    // D.9d.2 — re-arm the watcher service if Automatic reloading was
    // on across a process restart. The service is the only owner of
    // the sticky notification + observers; toggling the setting in
    // the UI also start/stops it directly, this branch covers cold
    // start / boot.
    graph.applicationScope.launch {
      val on = graph.settingsRepository.automaticReloading.first()
      if (on) LibraryWatcherService.start(applicationContext)
    }

    // D.17.3.6 — write the default music-source mode on a fresh
    // install so the first launch scans MediaStore (and therefore
    // populates the library) without the user opening a settings page.
    graph.applicationScope.launch {
      graph.settingsRepository.firstLaunchInitialise()
    }

    setContent {
      val snapshot by graph.settingsRepository.snapshot.collectAsState(initial = SettingsSnapshot.Default)
      val systemDark = isSystemInDarkTheme()
      val resolvedDark = when (snapshot.theme) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
      }
      TonearmTheme(
        darkTheme = resolvedDark,
        dynamicColor = snapshot.colorScheme == ColorScheme.Dynamic,
        blackTheme = snapshot.blackTheme,
      ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          TonearmApp(graph)
        }
      }
    }
  }
}
