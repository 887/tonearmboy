package com.eight87.tonearm.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  store: ThemePreferenceStore,
  onRescan: () -> Unit,
) {
  val current by store.flow.collectAsState(initial = ThemePreference.Default)
  val scope = rememberCoroutineScope()

  var confirmRescan by remember { mutableStateOf(false) }
  var confirmClearCache by remember { mutableStateOf(false) }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Settings") }) },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding).semantics { testTag = "settings_screen" },
    ) {
      item {
        SectionHeader("Theme")
      }
      items(items = ThemePreference.entries.toList(), key = { it.name }) { option ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = current == option, onClick = {
              scope.launch { store.set(option) }
            })
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = current == option, onClick = null)
          Text(
            text = themeLabel(option),
            modifier = Modifier.padding(start = 12.dp),
          )
        }
        HorizontalDivider()
      }

      item { SectionHeader("Library") }
      item {
        SettingRow(title = "Rescan now", subtitle = "Re-read MediaStore and refresh the library cache.") {
          confirmRescan = true
        }
      }
      item {
        SettingRow(title = "Clear cache", subtitle = "Delete the cached library. The next launch will rescan.") {
          confirmClearCache = true
        }
      }

      item { SectionHeader("About") }
      item {
        SettingRow(
          title = "tonearm",
          subtitle = "Version 1.0 · MIT License · github.com/887/tonearm",
        )
      }
    }
  }

  if (confirmRescan) {
    AlertDialog(
      onDismissRequest = { confirmRescan = false },
      title = { Text("Rescan library?") },
      text = { Text("This re-reads MediaStore and updates cached metadata. Playback is not interrupted.") },
      confirmButton = {
        TextButton(onClick = { onRescan(); confirmRescan = false }) { Text("Rescan") }
      },
      dismissButton = {
        TextButton(onClick = { confirmRescan = false }) { Text("Cancel") }
      },
    )
  }

  if (confirmClearCache) {
    AlertDialog(
      onDismissRequest = { confirmClearCache = false },
      title = { Text("Clear cached library?") },
      text = {
        Text(
          "This drops the local library cache. Your audio files are not affected. " +
            "The library will be rebuilt the next time you open the Library tab.",
        )
      },
      confirmButton = {
        TextButton(onClick = {
          // Phase D scope: clearing cache is a rescan today; the
          // backing Room db is replaced wholesale by `runScan(initial = true)`.
          onRescan()
          confirmClearCache = false
        }) { Text("Clear") }
      },
      dismissButton = {
        TextButton(onClick = { confirmClearCache = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun SectionHeader(label: String) {
  Text(
    text = label,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
  )
}

@Composable
private fun SettingRow(
  title: String,
  subtitle: String,
  onClick: (() -> Unit)? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .let { if (onClick != null) it.clickable(onClick = onClick) else it }
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
  HorizontalDivider()
}

private fun themeLabel(p: ThemePreference): String = when (p) {
  ThemePreference.System -> "Follow system"
  ThemePreference.Light -> "Light"
  ThemePreference.Dark -> "Dark"
}
