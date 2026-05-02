package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.eight87.tonearm.playback.PlaybackUiState

/**
 * Persistent mini-player. Shown as the slot directly above the bottom
 * navigation bar whenever the controller has media queued.
 *
 * Tapping the mini-player expands into the full-screen Now Playing
 * surface; the play/pause and close icons act in place.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
  state: PlaybackUiState,
  onTogglePlayPause: () -> Unit,
  onClose: () -> Unit,
  onExpand: () -> Unit,
  onPlayButtonLongPress: () -> Unit = {},
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
    // D.9a.1 — `IconButton` doesn't expose a long-press hook, so build a
    // 40-dp tap target by hand using `combinedClickable` over the same
    // Icon. Long-press triggers the user's chosen Custom playback bar
    // action; tap toggles play/pause as before.
    val interaction = remember { MutableInteractionSource() }
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .combinedClickable(
          interactionSource = interaction,
          indication = ripple(bounded = false, radius = 20.dp),
          onClick = onTogglePlayPause,
          onLongClick = onPlayButtonLongPress,
          onClickLabel = if (state.isPlaying) "Pause" else "Play",
          onLongClickLabel = "Custom action",
        )
        .semantics { testTag = "mini_player_play_button" },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = if (state.isPlaying) "Pause" else "Play",
        tint = LocalContentColor.current,
      )
    }
    IconButton(onClick = onClose) {
      Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop")
    }
  }
}
