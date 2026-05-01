package com.eight87.tonearm.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.playback.PlaybackUiState

/**
 * Landing surface. Intentionally light — the long-form library lives
 * one tab over, so the home screen is just a clear entry point and a
 * peek at what's playing.
 *
 * Plain factual copy, per the editorial line in CLAUDE.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  onBrowseLibrary: () -> Unit,
  onOpenNowPlaying: () -> Unit,
  playbackState: PlaybackUiState,
) {
  Scaffold(
    topBar = { TopAppBar(title = { Text("tonearm") }) },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp).semantics { testTag = "home_screen" },
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.Start,
    ) {
      Text(
        text = "Welcome",
        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
      )
      Text(
        text = "Local audio player. Pick a tab below to browse your library, search, or change settings.",
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
      )
      Spacer(Modifier.height(8.dp))
      Button(onClick = onBrowseLibrary) { Text("Browse library") }

      if (playbackState.hasMedia) {
        Spacer(Modifier.height(8.dp))
        Text(
          "Now playing: ${playbackState.title}",
          style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = onOpenNowPlaying) { Text("Open player") }
      }
    }
  }
}
