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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
 * D.24.1 layout — three rows, total height ≤ 96 dp:
 *  - **Info row** (48-dp art + title + "artist · album" + close-X). The
 *    info row's `clickable` opens NowPlaying — tapping art / title /
 *    subtitle is the expand affordance. The close-X stays tappable in
 *    place to stop playback without expanding.
 *  - **Transport row** — centered prev / play-pause / next icon
 *    buttons, sized for the mini-player density. Long-press on
 *    play-pause keeps the existing custom-bar-action behaviour.
 *  - **Progress strip** — slim 2-dp `LinearProgressIndicator` flush
 *    against the bottom edge.
 *
 * The `mini_player` testTag is attached to the info row (which carries
 * the `clickable`) so tap-to-expand semantics in tests still resolve to
 * a single semantics node with a click action.
 */
@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
fun MiniPlayer(
  state: PlaybackUiState,
  onTogglePlayPause: () -> Unit,
  onClose: () -> Unit,
  onExpand: () -> Unit,
  onSkipNext: () -> Unit = {},
  onSkipPrevious: () -> Unit = {},
  onPlayButtonLongPress: () -> Unit = {},
  albumCoversMode: AlbumCoversMode = AlbumCoversMode.Balanced,
) {
  if (!state.hasMedia) return
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    // -- Info row ------------------------------------------------------
    // testTag `mini_player` lives here (with the clickable for onExpand)
    // so tests that `performClick` on the row dispatch the expand
    // handler. Tapping the play / prev / next buttons or close-X lands
    // on those inner clickables instead.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onExpand)
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .semantics { testTag = "mini_player" },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // D.24.1: 48-dp album thumb (down from 56 dp in D.21.1) to make
      // room for the transport row while keeping total height ≤ 96 dp.
      CoverArt(
        albumId = state.mediaStoreAlbumId,
        size = 48.dp,
        mode = albumCoversMode,
        contentDescription = null,
        modifier = Modifier
          .size(48.dp)
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
      IconButton(
        onClick = onClose,
        modifier = Modifier.semantics { testTag = "mini_player_close" },
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop")
      }
    }

    // -- Transport row -------------------------------------------------
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
        .semantics { testTag = "mini_player_transport_row" },
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onSkipPrevious,
        enabled = state.hasPrevious,
        modifier = Modifier.semantics { testTag = "mini_player_prev" },
      ) {
        Icon(
          imageVector = Icons.Filled.SkipPrevious,
          contentDescription = "Previous",
        )
      }

      // D.9a.1 — `IconButton` doesn't expose a long-press hook, so build
      // a 40-dp tap target by hand using `combinedClickable`. Long-press
      // triggers the user's chosen Custom playback bar action; tap
      // toggles play/pause.
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

      IconButton(
        onClick = onSkipNext,
        enabled = state.hasNext,
        modifier = Modifier.semantics { testTag = "mini_player_next" },
      ) {
        Icon(
          imageVector = Icons.Filled.SkipNext,
          contentDescription = "Next",
        )
      }
    }

    // -- Progress strip ------------------------------------------------
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
