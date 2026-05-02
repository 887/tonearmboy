package com.eight87.tonearm.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.ui.settings.catalog.SettingsCard
import com.eight87.tonearm.ui.settings.catalog.SettingsDimens
import kotlinx.coroutines.launch

/**
 * D.9d.1 — Music sources sub-page.
 *
 * Lists every persisted SAF tree URI (one [Row] per source). The "Add
 * source" button launches `Intent.ACTION_OPEN_DOCUMENT_TREE`; the
 * picker returns a tree URI which we persist via
 * `ContentResolver.takePersistableUriPermission` so the URI survives
 * process death — without that call the URI is stale on next launch.
 *
 * Default behaviour, preserved for backward compatibility: when no
 * sources are configured the library scanner uses the legacy
 * MediaStore default ("everything MediaStore knows about, including
 * `/sdcard/Music`"). Configuring even one source switches the scan
 * to that explicit set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMusicSourcesScreen(
  repository: SettingsRepository,
  onBack: () -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val sources by repository.musicSourceUris.collectAsState(initial = emptySet())
  val scope = rememberCoroutineScope()

  // SAF directory picker. The contract returns the tree URI; we then
  // call takePersistableUriPermission so the URI survives a process
  // restart — without this the URI is stale.
  val pickDirectory = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    runCatching {
      context.contentResolver.takePersistableUriPermission(uri, flags)
    }.onFailure {
      scope.launch {
        snackbarHostState.showSnackbar("Could not retain access to ${uri.lastPathSegment ?: uri}")
      }
      return@rememberLauncherForActivityResult
    }
    scope.launch {
      repository.addMusicSourceUri(uri.toString())
      snackbarHostState.showSnackbar(
        "Added music source. Run Settings > Library > Rescan music to apply.",
      )
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Music sources") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = SettingsDimens.PagePadding)
        .semantics { testTag = "settings_music_sources" },
      verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
    ) {
      // Header card.
      SettingsCard(title = "Sources") {
        if (sources.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp)
              .semantics { testTag = "music_sources_empty" },
          ) {
            Text(
              text = "No sources configured. The library scans the device-default music " +
                "directory (/sdcard/Music). Add a source to scan a different location " +
                "such as an SD card or a custom directory.",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        } else {
          sources.toList().forEachIndexed { index, uriStr ->
            SourceRow(
              uri = uriStr,
              onRemove = {
                scope.launch {
                  repository.removeMusicSourceUri(uriStr)
                  // Best-effort release of the persisted permission so
                  // we don't leak SAF grants when the user removes a
                  // source. A failure here is non-fatal — Android will
                  // tidy up when the source URI vanishes from any app.
                  runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                      Uri.parse(uriStr),
                      Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                  }
                  snackbarHostState.showSnackbar("Removed source")
                }
              },
            )
            if (index < sources.size - 1) HorizontalDivider()
          }
        }
      }

      // Add source button.
      Button(
        onClick = {
          // The contract takes an optional initial-folder URI; null
          // lets the system pick the most-recently-used location.
          pickDirectory.launch(null)
        },
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp)
          .semantics { testTag = "music_sources_add" },
      ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("Add source", modifier = Modifier.padding(start = 8.dp))
      }
    }
  }
}

@Composable
private fun SourceRow(
  uri: String,
  onRemove: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .semantics { testTag = "music_source_row_$uri" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = displayNameForUri(uri),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = uri,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = onRemove) {
      Icon(Icons.Filled.Delete, contentDescription = "Remove source")
    }
  }
}

/**
 * Pull a human-readable display name out of a SAF tree URI. The
 * convention is `content://.../tree/<volume>%3A<relative-path>`; we
 * decode the document id and surface its trailing path segment, or
 * fall back to the raw URI's last segment when the format isn't
 * recognised.
 */
internal fun displayNameForUri(uriStr: String): String {
  val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return uriStr
  val last = uri.lastPathSegment ?: return uriStr
  val decoded = runCatching { Uri.decode(last) }.getOrNull() ?: last
  // `primary:Music` → "Music"; `1A2B-3C4D:Music/Albums" → "Music/Albums"
  val sep = decoded.indexOf(':')
  val tail = if (sep < 0) decoded else decoded.substring(sep + 1)
  return when {
    tail.isBlank() && sep >= 0 -> decoded.substring(0, sep)
    tail.isBlank() -> decoded
    else -> tail
  }
}
