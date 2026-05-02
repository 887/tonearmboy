package com.eight87.tonearm.ui.playing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.playback.PlaybackUiState
import com.eight87.tonearm.playback.QueueItem
import com.eight87.tonearm.playback.QueueSnapshot
import com.eight87.tonearm.ui.settings.AlbumCoversMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.24.3 / D.24.6 — assert the merged NowPlaying surface has exactly
 * *one* shuffle button and *one* repeat button. The pre-D.24 layout
 * had a second copy of each living in the queue header (`queue_shuffle_toggle`
 * + `queue_repeat_toggle`). After the merge the queue surface is a
 * pure list of upcoming tracks and the transport row is the single
 * source of truth for shuffle / repeat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h3000dp")
class QueueRemovedDuplicateControlsTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun playing() = PlaybackUiState(
    hasMedia = true,
    title = "Cipher Light",
    artist = "The Synth Foxes",
    album = "Velvet Den",
    isPlaying = true,
    positionMs = 5_000,
    durationMs = 120_000,
    hasNext = true,
    hasPrevious = true,
  )

  private fun snapshot() = QueueSnapshot(
    items = listOf(
      QueueItem(mediaId = "1", title = "Cipher Light", artist = "The Synth Foxes"),
      QueueItem(mediaId = "2", title = "Velvet Den", artist = "The Synth Foxes"),
    ),
    currentIndex = 0,
  )

  private fun render() {
    composeRule.setContent {
      MaterialTheme {
        Surface(modifier = Modifier.size(width = 400.dp, height = 4000.dp)) {
          val listState = rememberLazyListState()
          NowPlayingMergedSurface(
            state = playing(),
            queueSnapshot = snapshot(),
            listState = listState,
            albumCoversMode = AlbumCoversMode.Off,
            onSeek = {},
            onTogglePlayPause = {},
            onSeekBackward = {},
            onSeekForward = {},
            onSeekToPrevious = {},
            onSeekToNext = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onJumpToQueueIndex = {},
            onRemoveQueueItem = {},
            onMoveQueueItem = { _, _ -> },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }

  @Test
  fun exactly_one_shuffle_toggle_renders_in_merged_surface() {
    render()
    composeRule.waitForIdle()
    composeRule.onAllNodesWithTag("now_playing_shuffle", useUnmergedTree = true)
      .assertCountEquals(1)
  }

  @Test
  fun exactly_one_repeat_toggle_renders_in_merged_surface() {
    render()
    composeRule.waitForIdle()
    composeRule.onAllNodesWithTag("now_playing_repeat", useUnmergedTree = true)
      .assertCountEquals(1)
  }

  @Test
  fun no_legacy_queue_shuffle_or_repeat_toggles_remain() {
    render()
    // The pre-D.24 queue header carried `queue_shuffle_toggle` and
    // `queue_repeat_toggle` testTags. The merge dropped them entirely.
    composeRule.onAllNodesWithTag("queue_shuffle_toggle", useUnmergedTree = true)
      .assertCountEquals(0)
    composeRule.onAllNodesWithTag("queue_repeat_toggle", useUnmergedTree = true)
      .assertCountEquals(0)
  }

  @Test
  fun no_legacy_queue_active_track_header_remains() {
    // The active-track pinning concept is absorbed by the now-playing
    // card at the top of the LazyColumn. The queue section no longer
    // renders its own active-track row.
    render()
    composeRule.onAllNodesWithTag("queue_active_header", useUnmergedTree = true)
      .assertCountEquals(0)
    composeRule.onAllNodesWithTag("queue_active_seek", useUnmergedTree = true)
      .assertCountEquals(0)
  }
}
