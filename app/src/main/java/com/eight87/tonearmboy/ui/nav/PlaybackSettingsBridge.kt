package com.eight87.tonearmboy.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.tonearmboy.playback.ReplayGainCommands
import com.eight87.tonearmboy.playback.TransportCommands
import com.eight87.tonearmboy.ui.settings.PlaybackSettings
import com.eight87.tonearmboy.ui.settings.ReplayGainStrategy

/**
 * R.E.6 — single place that mirrors user [PlaybackSettings] into the
 * playback layer. Replaces the cluster of `LaunchedEffect(setting)`
 * blocks that lived inline in [TonearmboyApp].
 *
 * Takes the two narrow facets it actually pushes into ([TransportCommands]
 * for `setPauseOnRepeat`, [ReplayGainCommands] for `setReplayGain`),
 * plus the user-facing [PlaybackSettings] facet for the source-of-truth
 * Flows. The rest of the `PlaybackUiController` surface stays out of the
 * dependency graph here.
 */
@Composable
fun PlaybackSettingsBridge(
  transport: TransportCommands,
  replayGain: ReplayGainCommands,
  settings: PlaybackSettings,
) {
  val pauseOnRepeat by settings.pauseOnRepeat.flow
    .collectAsStateWithLifecycle(initialValue = false)
  val replayGainStrategy by settings.replayGainStrategy.flow
    .collectAsStateWithLifecycle(initialValue = ReplayGainStrategy.Default)
  val replayGainPreampDb by settings.replayGainPreampDb.flow
    .collectAsStateWithLifecycle(initialValue = 0f)

  LaunchedEffect(pauseOnRepeat) {
    transport.setPauseOnRepeat(pauseOnRepeat)
  }
  LaunchedEffect(replayGainStrategy, replayGainPreampDb) {
    replayGain.setReplayGain(replayGainStrategy, replayGainPreampDb)
  }
}
