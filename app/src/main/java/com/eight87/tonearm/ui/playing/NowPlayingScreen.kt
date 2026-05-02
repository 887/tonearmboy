package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.playback.PlaybackUiController
import com.eight87.tonearm.ui.library.CoverArt
import com.eight87.tonearm.ui.settings.AlbumCoversMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Full-screen Now Playing.
 *
 * Owns its own connect/release pair: while the host activity also
 * keeps a long-lived [PlaybackUiController] connected, this screen
 * still calls `connect()` (idempotent) on entry to be safe in deep-
 * link scenarios where Now Playing is the first composable rendered.
 *
 * Album art is a placeholder Material icon for now — Phase E adds
 * real album art once it has the notification + lock-screen plumbing
 * to drive the same bitmap.
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun NowPlayingScreen(
  playback: PlaybackUiController,
  onBack: () -> Unit,
  albumCoversMode: AlbumCoversMode = AlbumCoversMode.Balanced,
) {
  val state by playback.state.collectAsStateWithLifecycle()
  val queueSnapshot by playback.queue.collectAsStateWithLifecycle()
  var showQueue by remember { mutableStateOf(false) }
  val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

  // Connect on first composition; do not release on dispose because
  // the activity owns the long-lived connection (mini-player needs it
  // to stay live across screens).
  DisposableEffect(playback) {
    scope.launch { playback.connect() }
    onDispose { /* do not release; activity owns the connection */ }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Now Playing") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = { showQueue = true },
            modifier = Modifier.semantics { testTag = "now_playing_queue" },
          ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(24.dp)
        .semantics { testTag = "now_playing_screen" },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // D.15.7 — drive the same CoverArt the library tabs use; falls
      // back to the music-note placeholder when albumId is null
      // (Field Recordings tracks have no embedded art).
      CoverArt(
        albumId = state.mediaStoreAlbumId,
        size = 96.dp,
        mode = albumCoversMode,
        contentDescription = state.title.ifEmpty { null },
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(12.dp))
          .semantics { testTag = "now_playing_cover" },
      )

      Text(
        text = state.title.ifEmpty { "No track" },
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 2,
        modifier = Modifier.semantics { testTag = "now_playing_title" },
      )
      Text(
        text = listOfNotNull(
          state.artist.takeIf { it.isNotBlank() },
          state.album.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifEmpty { "—" },
        style = MaterialTheme.typography.bodyMedium,
      )

      Scrubber(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        onSeek = { playback.seekTo(it) },
      )

      if (showQueue) {
        QueueSheet(
          snapshot = queueSnapshot,
          onDismiss = { showQueue = false },
          onJumpTo = { idx -> playback.seekToQueueIndex(idx) },
          onRemove = { idx -> playback.removeQueueItem(idx) },
          onMove = { from, to -> playback.moveQueueItem(from, to) },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = { playback.seekToPrevious() }, enabled = state.hasPrevious) {
          Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { playback.seekBackward() }) {
          Icon(Icons.Filled.Replay10, contentDescription = "Seek back 10 seconds", modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { playback.togglePlayPause() }) {
          Icon(
            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (state.isPlaying) "Pause" else "Play",
            modifier = Modifier.size(56.dp),
          )
        }
        IconButton(onClick = { playback.seekForward() }) {
          Icon(Icons.Filled.Forward10, contentDescription = "Seek forward 10 seconds", modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { playback.seekToNext() }, enabled = state.hasNext) {
          Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
        }
      }
    }
  }
}

@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
  val total = durationMs.coerceAtLeast(0L)
  val pos = positionMs.coerceIn(0L, total.coerceAtLeast(positionMs))
  // Slider takes Float in 0f..valueRange, anchor with current pos.
  var dragValue by remember(positionMs) { mutableStateOf<Float?>(null) }
  val sliderValue = dragValue ?: pos.toFloat()
  val sliderMax = total.toFloat().coerceAtLeast(1f)

  Column {
    Slider(
      value = sliderValue.coerceIn(0f, sliderMax),
      onValueChange = { dragValue = it },
      onValueChangeFinished = {
        dragValue?.let { onSeek(it.toLong()) }
        dragValue = null
      },
      valueRange = 0f..sliderMax,
      modifier = Modifier.fillMaxWidth().semantics { testTag = "now_playing_scrubber" },
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(formatMillis(sliderValue.toLong()), style = MaterialTheme.typography.labelMedium)
      Text(formatMillis(total), style = MaterialTheme.typography.labelMedium)
    }
  }
}

private fun formatMillis(ms: Long): String {
  if (ms <= 0) return "0:00"
  val totalSeconds = ms / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
