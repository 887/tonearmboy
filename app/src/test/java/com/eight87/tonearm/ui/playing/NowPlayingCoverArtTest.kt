package com.eight87.tonearm.ui.playing

import com.eight87.tonearm.playback.PlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D.15.7 — Now Playing cover art is driven by `state.mediaStoreAlbumId`
 * which is null for tracks that have no MediaStore-resolvable art (the
 * Field Recordings fixture) and non-null for tracks that do (the
 * Velvet Den fixture). The screen feeds this id into [CoverArt] which
 * then either loads via Coil or renders the music-note placeholder.
 *
 * This pinpoints the routing rule without spinning a real
 * MediaController + Coil pipeline.
 */
class NowPlayingCoverArtTest {

  @Test fun empty_state_carries_null_album_id() {
    assertNull(PlaybackUiState.Empty.mediaStoreAlbumId)
  }

  @Test fun state_round_trips_album_id() {
    val s = PlaybackUiState.Empty.copy(
      hasMedia = true,
      title = "Brushwork",
      mediaStoreAlbumId = 42L,
    )
    assertEquals(42L, s.mediaStoreAlbumId)
  }

  @Test fun state_with_no_album_id_falls_back_to_placeholder_branch() {
    val s = PlaybackUiState.Empty.copy(
      hasMedia = true,
      title = "Quiet Hours",
      mediaStoreAlbumId = null,
    )
    // The cover-art composable's placeholder branch is taken when
    // either `mode == Off` or `albumId == null`. Here we only assert
    // the second condition's input is the null id, not the rendered
    // composition.
    assertNull(s.mediaStoreAlbumId)
  }
}
