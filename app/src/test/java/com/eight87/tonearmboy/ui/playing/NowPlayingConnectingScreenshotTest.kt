package com.eight87.tonearmboy.ui.playing

import com.eight87.tonearmboy.playback.ConnectionPhase
import com.eight87.tonearmboy.playback.PlaybackUiState
import org.junit.Test

/**
 * D.22.5 — supplementary screenshot pinning. The live-AVD captures
 * (`131-d22-cold-start-from-notification.png`, `133-d22-now-playing-
 * empty-card.png`) are the canonical fixtures; the connecting state
 * is captured from a screen-recording extraction documented in the
 * commit body. This file remains as a state-machine sanity check.
 */
class NowPlayingConnectingScreenshotTest {

  @Test
  fun connecting_state_render_path_uses_circular_progress() {
    // Pin that the connecting sub-state resolves correctly — the
    // composable reads this and renders `CircularProgressIndicator`
    // plus the "Connecting to playback…" caption.
    val state = PlaybackUiState.Empty.copy(connectionPhase = ConnectionPhase.Connecting)
    org.junit.Assert.assertEquals(NowPlayingSubState.Connecting, resolveSubState(state))
  }
}
