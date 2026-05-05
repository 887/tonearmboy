package com.eight87.tonearmboy.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Stable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R

/**
 * R.F.17 — single-radio settings picker bundled with its show/hide
 * state. Replaces the per-picker `var xPicker by remember { mutableStateOf(false) }`
 * + `if (xPicker) RadioPicker(...)` cluster scattered across every
 * Settings sub-page. (Settings-F5.)
 *
 * Usage:
 * ```
 * val picker = rememberSettingPickerState()
 * SettingsRowBinding.Picker(id = ID, ..., onClick = picker::show)
 * picker.Render(
 *   title = "Theme",
 *   options = Theme.entries,
 *   label = { it.displayLabel },
 *   current = currentTheme,
 *   onPick = { theme.theme.set(it) },
 * )
 * ```
 */
@Stable
class SettingPickerState {
  internal var visible by mutableStateOf(false)
  fun show() { visible = true }
  fun hide() { visible = false }
}

@Composable
fun rememberSettingPickerState(): SettingPickerState = remember { SettingPickerState() }

@Composable
fun <T> SettingPickerState.Render(
  title: String,
  options: Iterable<T>,
  label: (T) -> String,
  current: T,
  onPick: (T) -> Unit,
) {
  if (!visible) return
  AlertDialog(
    onDismissRequest = ::hide,
    title = { Text(title) },
    text = {
      Column {
        options.forEach { option ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .selectable(selected = option == current, onClick = {
                onPick(option)
                hide()
              })
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = option == current, onClick = null)
            Text(label(option), modifier = Modifier.padding(start = 12.dp))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = ::hide) {
        Text(stringResource(R.string.settings_dialog_close))
      }
    },
  )
}
