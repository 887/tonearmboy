package com.eight87.tonearm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.theme.TonearmTheme
import com.eight87.tonearm.ui.nav.TonearmApp
import com.eight87.tonearm.ui.settings.ThemePreference

@UnstableApi
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val graph = AppGraph.get(applicationContext)

    setContent {
      val storedPref by graph.themePreferenceStore.flow.collectAsState(initial = ThemePreference.Default)
      val systemDark = isSystemInDarkTheme()
      val resolvedDark = when (storedPref) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
      }
      TonearmTheme(darkTheme = resolvedDark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          TonearmApp(graph)
        }
      }
    }
  }
}
