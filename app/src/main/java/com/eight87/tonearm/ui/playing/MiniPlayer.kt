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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.playback.PlaybackUiState
import com.eight87.tonearm.ui.library.CoverArt
import com.eight87.tonearm.ui.settings.AlbumCoversMode

/**
 * Persistent mini-player. Shown as the slot directly above the bottom
 * navigation bar whenever the controller has media queued.
 *
 * D.21.1 polish — denser hierarchy modeled on Auxio:
 *  - 56-dp album thumbnail (real `CoverArt` driven by
 *    `state.mediaStoreAlbumId`, falling back to the music-note
 *    placeholder for tracks without album art)
 *  - title in `bodyLarge`, "artist · album" in `bodySmall`
 *  - thin `LinearProgressIndicator` flush against the bottom edge,
 *    fed by `state.positionMs / state.durationMs`
 *  - tap the row body to open NowPlaying (no separate chevron); the
 *    play/pause button and close-X stay tappable in place
 */
@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
fun MiniPlayer(
  state: PlaybackUiState,
  onTogglePlayPause: () -> Unit,
  onClose: () -> Unit,
  onExpand: () -> Unit,
  onPlayButtonLongPress: () -> Unit = {},
  albumCoversMode: AlbumCoversMode = AlbumCoversMode.Balanced,
) {
  if (!state.hasMedia) return
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(onClick = onExpand)
      .semantics { testTag = "mini_player" },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // D.21.1: real album thumb at 56 dp, matching Auxio's row height.
      // CoverArt handles the placeholder fallback when albumId is null.
      CoverArt(
        albumId = state.mediaStoreAlbumId,
        size = 56.dp,
        mode = albumCoversMode,
        contentDescription = null,
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(6.dp))
          .semantics { testTag = "mini_player_cover" },
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = state.title.ifEmpty { "Unknown" },
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          modifier = Modifier.semantics { testTag = "mini_player_title" },
        )
        // D.21.1: "artist · album" in bodySmall. Drop empty fields so
        // single-tag tracks don't render a stray separator.
        val subtitle = listOfNotNull(
          state.artist.takeIf { it.isNotBlank() },
          state.album.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifEmpty { "Unknown artist" }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.semantics { testTag = "mini_player_subtitle" },
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
    // D.21.1: slim under-bar progress strip flush against the bottom
    // edge of the mini-player surface. Indeterminate-looking when
    // duration is unknown (use 0 progress so the bar stays empty
    // rather than spinning).
    val total = state.durationMs
    val progress = if (total > 0L) {
      (state.positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else 0f
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier
        .fillMaxWidth()
        .height(2.dp)
        .semantics { testTag = "mini_player_progress" },
    )
  }
}
