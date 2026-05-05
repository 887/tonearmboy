package com.eight87.tonearmboy.ui.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.mediastore.MediaStorePermissions

/**
 * Gate that ensures `READ_MEDIA_AUDIO` (or the legacy
 * `READ_EXTERNAL_STORAGE` on API ≤ 32) is granted before letting the
 * library composables render.
 *
 * On a fresh install on a real phone the manifest declaration alone
 * isn't enough — Android 13+ requires a runtime grant via
 * `ActivityResultContracts.RequestPermission`. Without this gate the
 * library scan silently returned zero tracks and the user saw "No
 * tracks yet" forever. This composable triggers the system dialog on
 * first launch, shows a rationale + retry card if denied, and offers
 * a deep link into the app's permission settings if the user picked
 * "don't ask again".
 *
 * On grant, [onGranted] fires once so the caller can kick off the
 * initial library scan.
 */
@Composable
fun RequireAudioPermission(
  onGranted: () -> Unit,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val permission = remember { MediaStorePermissions.requiredAudioPermission() }

  // Track current grant state. Compose-friendly so a recomposition
  // after the system dialog updates the UI immediately.
  var granted by remember {
    mutableStateOf(MediaStorePermissions.hasAudioPermission(context))
  }
  // True after we've asked at least once. Distinguishes "first launch,
  // we should auto-prompt" from "user denied, show the rationale".
  var asked by remember { mutableStateOf(false) }

  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { result ->
    granted = result
    asked = true
    if (result) onGranted()
  }

  // First composition: if not granted, fire the system dialog immediately.
  LaunchedEffect(Unit) {
    if (!granted && !asked) {
      launcher.launch(permission)
    } else if (granted) {
      onGranted()
    }
  }

  if (granted) {
    content()
    return
  }

  // Denied (or pending). Show the rationale card.
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 360.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Icon(
        Icons.Outlined.LibraryMusic,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        stringResource(R.string.permission_audio_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )
      Text(
        stringResource(R.string.permission_audio_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = { launcher.launch(permission) }) {
        Text(
          if (asked) stringResource(R.string.permission_audio_retry_button)
          else stringResource(R.string.permission_audio_grant_button),
        )
      }
      if (asked) {
        // After a denial, the system may suppress further prompts
        // ("don't ask again"). Provide a fallback to the app's
        // permission settings page so the user can flip it manually.
        OutlinedButton(onClick = {
          val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          runCatching { context.startActivity(intent) }
        }) {
          Text(stringResource(R.string.permission_audio_open_settings_button))
        }
      }
    }
  }
}
