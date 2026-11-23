package com.eight87.tonearmboy.ui.playing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearmboy.playback.SleepTimer
import com.eight87.tonearmboy.playback.SleepTimerState

/**
 * Sleep-timer bottom sheet — shape lifted from whisperboy's
 * `SleepTimerSheet`. Quick-pick FilterChips for the standard
 * durations, a "Wait for end of song" toggle, a Custom… entry that
 * opens a duration AlertDialog, and a Cancel-timer footer when a
 * timer is active. Reads the live `SleepTimerState` so any remote
 * arm (settings auto-arm in the future) reflects immediately.
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun NowPlayingSleepTimerSheet(
  sleepTimer: SleepTimer,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  /** User's default duration (minutes) — seeds the Custom… dialog. */
  defaultMinutes: Int = 30,
  /** User's fade-out window in seconds. 0 = no fade. */
  fadeOutSeconds: Int = 0,
) {
  val sheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
  )
  val state by sleepTimer.state.collectAsStateWithLifecycle()
  var customOpen by remember { mutableStateOf(false) }
  var waitForEndOfTrack by remember { mutableStateOf(false) }

  // Force fully-opaque container even in glass-mode (the theme only
  // strips alpha from `surface`, but ModalBottomSheet's default tonal
  // elevation + dark-scheme `surfaceContainer` value still reads as
  // glassy against the cover wallpaper behind the queue). Explicit
  // `.copy(alpha = 1f)` + tonalElevation = 0.dp keeps the sheet a
  // hard surface.
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f),
    tonalElevation = 0.dp,
    dragHandle = { BottomSheetDefaults.DragHandle() },
    modifier = modifier,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
      Text(
        text = "Sleep timer",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.height(8.dp))

      when (val s = state) {
        is SleepTimerState.Running -> {
          val total = (s.remainingMs / 1000L).coerceAtLeast(0)
          val mins = total / 60
          val secs = total % 60
          Text(
            text = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')} remaining",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.height(8.dp))
        }
        SleepTimerState.WaitingForTrackEnd -> {
          Text(
            text = "Waiting for end of song…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.height(8.dp))
        }
        SleepTimerState.Idle -> Unit
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(QUICK_PICKS, key = { it }) { minutes ->
          val running = state as? SleepTimerState.Running
          val isActive = running != null &&
            running.remainingMs / 60_000L + 1 == minutes.toLong() &&
            running.waitForEndOfTrack == waitForEndOfTrack
          FilterChip(
            selected = isActive,
            onClick = {
              sleepTimer.start(minutes * 60_000L, waitForEndOfTrack, fadeOutSeconds * 1000L)
              onDismiss()
            },
            label = { Text("$minutes min") },
          )
        }
      }

      Spacer(Modifier.height(12.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
          onClick = { customOpen = true },
          modifier = Modifier.wrapContentWidth(),
        ) {
          Text("Custom…")
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = waitForEndOfTrack,
            onCheckedChange = { waitForEndOfTrack = it },
          )
          Text("Wait for end of song")
        }
      }

      Spacer(Modifier.height(16.dp))

      if (state !is SleepTimerState.Idle) {
        Button(
          onClick = {
            sleepTimer.cancel()
            onDismiss()
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Cancel timer")
        }
        Spacer(Modifier.height(8.dp))
      }
    }
  }

  if (customOpen) {
    CustomDurationDialog(
      initialMinutes = defaultMinutes,
      onPick = { minutes ->
        sleepTimer.start(minutes * 60_000L, waitForEndOfTrack, fadeOutSeconds * 1000L)
        customOpen = false
        onDismiss()
      },
      onDismiss = { customOpen = false },
    )
  }
}

@Composable
private fun CustomDurationDialog(
  initialMinutes: Int,
  onPick: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var raw by remember { mutableStateOf(initialMinutes.toString()) }
  val parsed = raw.toIntOrNull()
  val valid = parsed != null && parsed in 1..720

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Custom duration") },
    text = {
      OutlinedTextField(
        value = raw,
        onValueChange = { raw = it.filter { ch -> ch.isDigit() }.take(3) },
        label = { Text("Minutes (1–720)") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
          keyboardType = KeyboardType.Number,
        ),
      )
    },
    confirmButton = {
      TextButton(
        onClick = { if (parsed != null && valid) onPick(parsed) },
        enabled = valid,
      ) { Text("OK") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

private val QUICK_PICKS = listOf(5, 10, 15, 30, 60)
