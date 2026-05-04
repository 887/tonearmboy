package com.eight87.tonearmboy.ui.playing

import com.eight87.tonearmboy.playback.ConnectionPhase
import com.eight87.tonearmboy.playback.PlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.22.3 — pin the three rendering modes [resolveSubState] can return.
 *
 *  - `Connecting`: the activity has just started and Media3's
 *    `MediaController.Builder.buildAsync` future has not yet resolved.
 *    UI shows a spinner + "Connecting to playback…" caption.
 *  - `ConnectedEmpty`: the controller is bound but the session has no
 *    queue. UI shows the "Nothing playing" empty card and auto-pops
 *    300 ms later so the user lands on Library.
 *  - `ConnectedWithMedia`: the existing transport surface (cover art,
 *    title, scrubber, transport row).
 *
 * Without this state machine, NowPlayingScreen rendered nothing when
 * `hasMedia == false` — the cold-start "blank black screen" the user
 * reported after task-switcher swipe + notification tap on a still-
 * alive PlaybackService.
 */
class NowPlayingConnectingStateTest {

  @Test
  fun connecting_phase_with_no_media_resolves_to_connecting() {
    val state = PlaybackUiState.Empty.copy(connectionPhase = ConnectionPhase.Connecting)
    assertEquals(NowPlayingSubState.Connecting, resolveSubState(state))
  }

  @Test
  fun connected_phase_with_no_media_resolves_to_empty_card() {
    val state = PlaybackUiState.Empty.copy(connectionPhase = ConnectionPhase.Connected)
    assertEquals(NowPlayingSubState.ConnectedEmpty, resolveSubState(state))
  }

  @Test
  fun connected_phase_with_media_resolves_to_full_transport() {
    val state = PlaybackUiState(
      hasMedia = true,
      title = "Cipher Light",
      artist = "The Synth Foxes",
      album = "Velvet Den",
      isPlaying = true,
      positionMs = 12_000,
      durationMs = 30_000,
      hasNext = true,
      hasPrevious = false,
      connectionPhase = ConnectionPhase.Connected,
    )
    assertEquals(NowPlayingSubState.ConnectedWithMedia, resolveSubState(state))
  }

  @Test
  fun connecting_phase_with_media_renders_full_transport() {
    // Edge case: if a state arrives with `hasMedia = true` but
    // `connectionPhase == Connecting` (a race the listener can't really
    // produce, but worth pinning), we still render the transport so
    // the user sees the track immediately.
    val state = PlaybackUiState(
      hasMedia = true,
      title = "Cipher Light",
      artist = "",
      album = "",
      isPlaying = false,
      positionMs = 0,
      durationMs = 1_000,
      hasNext = false,
      hasPrevious = false,
      connectionPhase = ConnectionPhase.Connecting,
    )
    assertEquals(NowPlayingSubState.ConnectedWithMedia, resolveSubState(state))
  }

  @Test
  fun default_state_is_connecting_until_a_controller_binds() {
    // The default `PlaybackUiState.Empty` ships with
    // `connectionPhase = Connecting` so a NowPlaying surface that
    // mounts before `PlaybackUiController.connect()` resolves shows
    // the spinner, not the blank Compose tree.
    assertEquals(ConnectionPhase.Connecting, PlaybackUiState.Empty.connectionPhase)
    assertEquals(NowPlayingSubState.Connecting, resolveSubState(PlaybackUiState.Empty))
  }
}
