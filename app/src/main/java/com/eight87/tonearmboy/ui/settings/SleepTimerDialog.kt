package com.eight87.tonearmboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.playback.SleepTimer
import com.eight87.tonearmboy.playback.SleepTimerState

/**
 * Phase H.3 — sleep timer settings dialog.
 *
 * Two visual states based on [state]:
 *  - [SleepTimerState.Idle]: presets row + custom-minute stepper +
 *    "Wait for end of song" checkbox + Start.
 *  - [SleepTimerState.Running] / [SleepTimerState.WaitingForTrackEnd]:
 *    show the live remaining time (or the "waiting" caption) + Cancel.
 */
@Composable
fun SleepTimerDialog(
  state: SleepTimerState,
  onStart: (durationMs: Long, waitForEndOfTrack: Boolean) -> Unit,
  onCancel: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Sleep timer") },
    text = {
      Column(modifier = Modifier.semantics { testTag = "sleep_timer_dialog" }) {
        when (state) {
          is SleepTimerState.Running -> RunningBody(state)
          SleepTimerState.WaitingForTrackEnd -> Text(
            "Waiting for the current song to end…",
            style = MaterialTheme.typography.bodyMedium,
          )
          SleepTimerState.Idle -> IdleBody(onStart = onStart)
        }
      }
    },
    confirmButton = {
      when (state) {
        is SleepTimerState.Running, SleepTimerState.WaitingForTrackEnd ->
          TextButton(onClick = { onCancel(); onDismiss() }) { Text("Cancel timer") }
        SleepTimerState.Idle -> {
          // Idle commit lives inside IdleBody; here we just close.
          TextButton(onClick = onDismiss) { Text("Close") }
        }
      }
    },
    dismissButton = {
      if (state !is SleepTimerState.Running && state !is SleepTimerState.WaitingForTrackEnd) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    },
  )
}

@Composable
private fun IdleBody(
  onStart: (durationMs: Long, waitForEndOfTrack: Boolean) -> Unit,
) {
  var customMinutes by remember { mutableStateOf(20) }
  var waitForEndOfTrack by remember { mutableStateOf(false) }

  Text(
    "Choose a duration:",
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.padding(bottom = 8.dp),
  )
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    SleepTimer.PRESET_MINUTES.forEach { minutes ->
      FilledTonalButton(
        onClick = { onStart(minutes * 60_000L, waitForEndOfTrack) },
        modifier = Modifier
          .weight(1f)
          .semantics { testTag = "sleep_timer_preset_${minutes}m" },
      ) {
        Text("${minutes}m", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Custom:", modifier = Modifier.padding(end = 8.dp))
    IconButton(
      onClick = { customMinutes = (customMinutes - 5).coerceAtLeast(1) },
      modifier = Modifier.semantics { testTag = "sleep_timer_custom_minus" },
    ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }
    Text(
      "$customMinutes min",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .semantics { testTag = "sleep_timer_custom_value" },
    )
    IconButton(
      onClick = { customMinutes = (customMinutes + 5).coerceAtMost(720) },
      modifier = Modifier.semantics { testTag = "sleep_timer_custom_plus" },
    ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
    OutlinedButton(
      onClick = { onStart(customMinutes * 60_000L, waitForEndOfTrack) },
      modifier = Modifier
        .padding(start = 8.dp)
        .semantics { testTag = "sleep_timer_custom_start" },
    ) { Text("Start") }
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = waitForEndOfTrack,
      onCheckedChange = { waitForEndOfTrack = it },
      modifier = Modifier.semantics { testTag = "sleep_timer_wait_song" },
    )
    Text("Wait for end of song", modifier = Modifier.padding(start = 4.dp))
  }
}

@Composable
private fun RunningBody(running: SleepTimerState.Running) {
  val totalSeconds = (running.remainingMs / 1000L).coerceAtLeast(0)
  val mins = totalSeconds / 60
  val secs = totalSeconds % 60
  Text(
    text = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}",
    style = MaterialTheme.typography.displaySmall,
    modifier = Modifier.semantics { testTag = "sleep_timer_remaining" },
  )
  Text(
    "remaining",
    style = MaterialTheme.typography.bodyMedium,
  )
  if (running.waitForEndOfTrack) {
    Text(
      "Will wait until the current song finishes.",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}
