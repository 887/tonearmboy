package com.eight87.tonearmboy.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * D.23.1 — verify the player-command set our [PlaybackService.SessionCallback.onConnect]
 * advertises is the full default that includes repeat / shuffle /
 * play-pause / next / previous. Without `setAvailablePlayerCommands`
 * the SystemUI Quick Settings card silently drops repeat-mode and
 * shuffle-mode taps because the controller-side
 * `availablePlayerCommands` flagset comes back empty for those.
 *
 * This test pins the contract by asserting against
 * [MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS] directly —
 * the same constant we hand to the AcceptedResultBuilder. If a future
 * Media3 release ever rewrites that constant to drop one of the
 * commands SystemUI wires to its UI (repeat / shuffle / prev / play /
 * next) we want to fail loudly here rather than silently regress on
 * the device.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class SystemMediaCommandsTest {

  @Test
  fun default_player_commands_include_quick_settings_buttons() {
    val cmds = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
    val required = intArrayOf(
      Player.COMMAND_PLAY_PAUSE,
      Player.COMMAND_SET_REPEAT_MODE,
      Player.COMMAND_SET_SHUFFLE_MODE,
      Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
      Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )
    val missing = required.filter { !cmds.contains(it) }
    assertTrue(
      "DEFAULT_PLAYER_COMMANDS missing entries required by SystemUI Quick Settings: $missing",
      missing.isEmpty(),
    )
  }

  @Test
  fun every_required_command_individually() {
    // One-by-one for readable failure output if Media3 drops one of
    // these in a future minor.
    val cmds = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
    assertTrue(
      "COMMAND_PLAY_PAUSE missing",
      cmds.contains(Player.COMMAND_PLAY_PAUSE),
    )
    assertTrue(
      "COMMAND_SET_REPEAT_MODE missing",
      cmds.contains(Player.COMMAND_SET_REPEAT_MODE),
    )
    assertTrue(
      "COMMAND_SET_SHUFFLE_MODE missing",
      cmds.contains(Player.COMMAND_SET_SHUFFLE_MODE),
    )
    assertTrue(
      "COMMAND_SEEK_TO_NEXT_MEDIA_ITEM missing",
      cmds.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
    )
    assertTrue(
      "COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM missing",
      cmds.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
    )
  }
}
