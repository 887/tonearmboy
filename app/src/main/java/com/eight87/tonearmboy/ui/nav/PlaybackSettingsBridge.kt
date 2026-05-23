package com.eight87.tonearmboy.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.tonearmboy.playback.ReplayGainCommands
import com.eight87.tonearmboy.ui.settings.PlaybackSettings
import com.eight87.tonearmboy.ui.settings.ReplayGainStrategy

/**
 * R.E.6 — single place that mirrors user [PlaybackSettings] into the
 * playback layer. Replaces the cluster of `LaunchedEffect(setting)`
 * blocks that lived inline in [TonearmboyApp].
 */
@Composable
fun PlaybackSettingsBridge(
  replayGain: ReplayGainCommands,
  settings: PlaybackSettings,
) {
  val replayGainStrategy by settings.replayGainStrategy.flow
    .collectAsStateWithLifecycle(initialValue = ReplayGainStrategy.Default)
  val replayGainPreampDb by settings.replayGainPreampDb.flow
    .collectAsStateWithLifecycle(initialValue = 0f)

  LaunchedEffect(replayGainStrategy, replayGainPreampDb) {
    replayGain.setReplayGain(replayGainStrategy, replayGainPreampDb)
  }
}
