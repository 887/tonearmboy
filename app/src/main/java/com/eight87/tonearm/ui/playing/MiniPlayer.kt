package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.playback.PlaybackUiState

/**
 * Persistent mini-player. Shown as the slot directly above the bottom
 * navigation bar whenever the controller has media queued.
 *
 * Tapping the mini-player expands into the full-screen Now Playing
 * surface; the play/pause and close icons act in place.
 */
@Composable
fun MiniPlayer(
  state: PlaybackUiState,
  onTogglePlayPause: () -> Unit,
  onClose: () -> Unit,
  onExpand: () -> Unit,
) {
  if (!state.hasMedia) return
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(onClick = onExpand)
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .semantics { testTag = "mini_player" },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = state.title.ifEmpty { "Unknown" },
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
      )
      Text(
        text = state.artist.ifEmpty { "Unknown artist" },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
      )
    }
    IconButton(onClick = onTogglePlayPause) {
      Icon(
        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = if (state.isPlaying) "Pause" else "Play",
      )
    }
    IconButton(onClick = onClose) {
      Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop")
    }
  }
}
