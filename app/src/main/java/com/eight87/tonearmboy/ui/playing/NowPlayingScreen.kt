package com.eight87.tonearmboy.ui.playing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.media3.common.Player
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.eight87.tonearmboy.ui.common.FastScrollbar
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.playback.ConnectionPhase
import com.eight87.tonearmboy.playback.NowPlayingState
import com.eight87.tonearmboy.playback.PlaybackUiController
import com.eight87.tonearmboy.playback.QueueCommands
import com.eight87.tonearmboy.playback.TransportCommands
import com.eight87.tonearmboy.playback.PlaybackUiState
import com.eight87.tonearmboy.playback.QueueItem
import com.eight87.tonearmboy.playback.QueueSnapshot
import com.eight87.tonearmboy.ui.library.CoverArt
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen Now Playing.
 *
 * D.24.2 — the queue is no longer a separate `ModalBottomSheet`. The
 * whole surface is one scrollable `LazyColumn` with the now-playing
 * card on top (cover + title + scrubber + transport row) and the
 * queue inlined directly below. The queue-shortcut icon in the top
 * app bar is repurposed as `animateScrollToItem` to the "Up next"
 * header so users on a short device can jump to the queue without
 * scrolling.
 *
 * Owns its own connect/release pair: while the host activity also
 * keeps a long-lived [PlaybackUiController] connected, this screen
 * still calls `connect()` (idempotent) on entry to be safe in deep-
 * link scenarios where Now Playing is the first composable rendered.
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun NowPlayingScreen(
  // R.C.1 — narrow facets: read-only state + write-only commands.
  // The wholesale `PlaybackUiController` is no longer in this
  // signature; AppGraph hands the same instance through three
  // narrow contracts, but a stray ReplayGain or settings-mirror
  // call from this screen now fails to compile.
  nowPlayingState: NowPlayingState,
  transport: TransportCommands,
  queueCommands: QueueCommands,
  onBack: () -> Unit,
  albumCoversMode: AlbumCoversMode = AlbumCoversMode.Balanced,
  onSaveQueueAsPlaylist: ((mediaIds: List<String>) -> Unit)? = null,
) {
  val state by nowPlayingState.state.collectAsStateWithLifecycle()
  val queueSnapshot by nowPlayingState.queue.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()

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
          // D.29.1 — replaces D.24.2's scroll-to-queue affordance.
          // The queue lives inline (D.24.2) so a "scroll to queue"
          // button is redundant — a swipe gets the user there. Repurpose
          // the slot for "Save queue as playlist", which dispatches
          // through the existing `PlaylistPickerSheet` (the same one
          // multi-select uses) seeded with the queue's media ids.
          IconButton(
            onClick = {
              onSaveQueueAsPlaylist?.invoke(queueSnapshot.items.map { it.mediaId })
            },
            modifier = Modifier.semantics { testTag = "now_playing_save_queue" },
            enabled = state.hasMedia && queueSnapshot.items.isNotEmpty(),
          ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Save queue as playlist")
          }
        },
      )
    },
  ) { innerPadding ->
    when (resolveSubState(state)) {
      NowPlayingSubState.Connecting -> NowPlayingConnecting(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(24.dp),
      )
      NowPlayingSubState.ConnectedEmpty -> NowPlayingEmpty(
        onBack = onBack,
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(24.dp),
      )
      NowPlayingSubState.ConnectedWithMedia -> NowPlayingMergedSurface(
        state = state,
        queueSnapshot = queueSnapshot,
        listState = listState,
        albumCoversMode = albumCoversMode,
        onSeek = transport::seekTo,
        onTogglePlayPause = transport::togglePlayPause,
        onSeekBackward = transport::seekBackward,
        onSeekForward = transport::seekForward,
        onSeekToPrevious = transport::seekToPrevious,
        onSeekToNext = transport::seekToNext,
        onToggleShuffle = transport::toggleShuffle,
        onCycleRepeat = transport::cycleRepeatMode,
        onJumpToQueueIndex = queueCommands::seekToQueueIndex,
        onRemoveQueueItem = queueCommands::removeQueueItem,
        onMoveQueueItem = queueCommands::moveQueueItem,
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .semantics { testTag = "now_playing_screen" },
      )
    }
  }
}

/**
 * D.24.2 — merged now-playing + queue surface. Single `LazyColumn`,
 * scrollable from cover/transport at the top straight into the queue
 * below.
 *
 * Item layout:
 *  - 0: now-playing card (cover + title + subtitle + scrubber)
 *  - 1: transport row (shuffle / prev / -10 / play / +10 / next /
 *       repeat) — single source of truth for transport
 *  - 2: queue section (divider + "Up next" header + filter +
 *       drag-drop list)
 *
 * The queue-section item self-contains the divider, header, filter,
 * and the drag-reorder list of upcoming items. We keep it in a single
 * `item { ... }` block (rather than `items(...)`) because the drag-
 * reorder helper needs to own its own scroll-axis offset translations
 * and would conflict with `LazyColumn`'s recycler.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun NowPlayingMergedSurface(
  state: PlaybackUiState,
  queueSnapshot: QueueSnapshot,
  listState: LazyListState,
  albumCoversMode: AlbumCoversMode,
  onSeek: (Long) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekBackward: () -> Unit,
  onSeekForward: () -> Unit,
  onSeekToPrevious: () -> Unit,
  onSeekToNext: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onJumpToQueueIndex: (Int) -> Unit,
  onRemoveQueueItem: (Int) -> Unit,
  onMoveQueueItem: (Int, Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  // D.27.4 — measure the surrounding LazyColumn viewport so the queue
  // section can size itself to ≥ one viewport even when the queue has
  // a single item (the user's "can't scroll down one screen length"
  // bug). `BoxWithConstraints` here is bounded by the parent
  // (`Modifier.fillMaxSize()` from the caller) so `maxHeight` is the
  // actual viewport height, not Constraints.Infinity.
  // D.27.8 — while a queue row is being dragged, the outer LazyColumn's
  // own vertical-scroll gesture handler steals the pointer events
  // before `DragReorderColumn`'s `pointerInput` sees them, so the user
  // sees the surface scroll instead of the row lifting. Track drag
  // state and flip `userScrollEnabled` off for the duration of the
  // drag. The `DisposableEffect` cleanup resets the flag if the
  // composable disposes mid-drag (would otherwise leave parent scroll
  // permanently disabled across recompositions in edge cases).
  var isQueueDragging by remember { mutableStateOf(false) }
  DisposableEffect(Unit) {
    onDispose { isQueueDragging = false }
  }
  androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
    val viewport = maxHeight
  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(
      horizontal = 24.dp,
      vertical = 16.dp,
    ),
    userScrollEnabled = !isQueueDragging,
  ) {
    // -- Item 0: now-playing card ------------------------------------
    item(key = "now_playing_card") {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "now_playing_card" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
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
          onSeek = onSeek,
        )
      }
    }

    // -- Item 1: transport row ---------------------------------------
    item(key = "transport_row") {
      // R.F.3 — shared with MiniPlayer via PlaybackTransportRow.
      // NowPlaying inserts Replay10 / Forward10 around the play button
      // via the extraStart / extraEnd slots.
      PlaybackTransportRow(
        state = state,
        iconSize = 36.dp,
        playIconSize = 56.dp,
        onTogglePlayPause = onTogglePlayPause,
        onSkipPrevious = onSeekToPrevious,
        onSkipNext = onSeekToNext,
        onToggleShuffle = onToggleShuffle,
        onCycleRepeat = onCycleRepeat,
        testTagPrefix = "now_playing",
        modifier = Modifier.semantics { testTag = "now_playing_transport_row" },
        extraStart = {
          IconButton(onClick = onSeekBackward) {
            Icon(
              Icons.Filled.Replay10,
              contentDescription = "Seek back 10 seconds",
              modifier = Modifier.size(36.dp),
            )
          }
        },
        extraEnd = {
          IconButton(onClick = onSeekForward) {
            Icon(
              Icons.Filled.Forward10,
              contentDescription = "Seek forward 10 seconds",
              modifier = Modifier.size(36.dp),
            )
          }
        },
      )
    }

    // -- Item 2: queue section ---------------------------------------
    item(key = "queue_section") {
      // D.26.3: pass a `fillParentMaxHeight` modifier into the queue
      // section's no-match placeholder so a zero-match filter doesn't
      // collapse the LazyColumn content height (which would force the
      // scroll position back to the top of the surface).
      QueueSection(
        snapshot = queueSnapshot,
        onJumpTo = onJumpToQueueIndex,
        onRemove = onRemoveQueueItem,
        onMove = onMoveQueueItem,
        noMatchFillModifier = Modifier.fillParentMaxHeight(),
        parentViewportHeight = viewport,
        onDragStateChange = { isQueueDragging = it },
      )
    }
  }
    // R.A.Q — full-height fast-scroll thumb. Spans the entire merged
    // surface (cover/transport at the top through the queue at the
    // bottom) since they share one LazyColumn.
    FastScrollbar(
      state = listState,
      modifier = Modifier.align(Alignment.CenterEnd),
    )
  }
}

/**
 * D.24.2 — index of the queue-section item inside [NowPlayingMergedSurface].
 * Exposed as a constant so the top-app-bar queue button can
 * `animateScrollToItem` to the right slot, and so tests can assert the
 * scroll target without knowing the LazyColumn internals.
 */
internal const val QUEUE_LIST_INDEX: Int = 2

/**
 * D.22.3 — three rendering modes for [NowPlayingScreen].
 *
 *  - [Connecting]: the Media3 controller hasn't bound yet; show a
 *    spinner + "Connecting to playback…" caption. This is the cold-
 *    start branch that prevents the blank Compose tree.
 *  - [ConnectedEmpty]: the controller is bound but the session has
 *    no queue. We show an "empty card with CTA back to Library" and
 *    auto-pop after 300 ms so the user lands somewhere useful.
 *  - [ConnectedWithMedia]: the existing transport surface, now with
 *    the queue inlined below it (D.24.2).
 */
internal enum class NowPlayingSubState {
  Connecting,
  ConnectedEmpty,
  ConnectedWithMedia,
}

internal fun resolveSubState(state: PlaybackUiState): NowPlayingSubState = when {
  state.connectionPhase == ConnectionPhase.Connecting && !state.hasMedia ->
    NowPlayingSubState.Connecting
  !state.hasMedia -> NowPlayingSubState.ConnectedEmpty
  else -> NowPlayingSubState.ConnectedWithMedia
}

@Composable
private fun NowPlayingConnecting(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.semantics { testTag = "now_playing_connecting" },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
  ) {
    CircularProgressIndicator()
    Text(
      text = "Connecting to playback…",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun NowPlayingEmpty(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(Unit) {
    delay(EmptyAutoPopMs)
    onBack()
  }
  Column(
    modifier = modifier.semantics { testTag = "now_playing_empty" },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
  ) {
    Text(
      text = "Nothing playing",
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      text = "Pick a track in the library to start playback.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
      onClick = onBack,
      modifier = Modifier.semantics { testTag = "now_playing_empty_back" },
    ) { Text("Back to library") }
  }
}

private const val EmptyAutoPopMs: Long = 300L

@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
  val total = durationMs.coerceAtLeast(0L)
  val pos = positionMs.coerceIn(0L, total.coerceAtLeast(positionMs))
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
