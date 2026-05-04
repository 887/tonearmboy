package com.eight87.tonearmboy.ui.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * D.23.3 — runtime grant gate for [Manifest.permission.POST_NOTIFICATIONS].
 *
 * The permission was added in Android 13 (API 33 / TIRAMISU). On
 * earlier API levels this composable is a pure pass-through and never
 * triggers a launcher. On API 33+:
 *
 *  - Granted → render [content].
 *  - First launch (not yet asked) → fire the system dialog once.
 *  - Denied → still render [content] (the rest of the app keeps
 *    working) but show a one-shot snackbar explaining the consequence
 *    in plain language: the foreground-service playback notification
 *    in the tray won't post. Quick Settings + lock screen still work
 *    because those surfaces are MediaSession-driven and don't depend
 *    on the runtime POST_NOTIFICATIONS grant.
 *
 * Mounted as a sibling of [RequireAudioPermission] in [com.eight87.tonearmboy.MainActivity]
 * — audio permission must be granted first so the user has actually
 * accepted the core "tonearmboy wants to read your music" consent before
 * we ask the secondary "and post a notification?" question.
 */
@Composable
fun RequirePostNotifications(
  content: @Composable () -> Unit,
) {
  // API 32 and below: no runtime grant needed, the manifest entry is
  // sufficient. Pass through immediately.
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    content()
    return
  }

  val context = LocalContext.current
  val initialGranted = remember {
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
  }

  var granted by remember { mutableStateOf(initialGranted) }
  // Persist the "we already prompted once" flag across configuration
  // changes so a screen rotate doesn't re-prompt. Process death is
  // fine to forget — the system itself rate-limits subsequent prompts.
  var asked by rememberSaveable { mutableStateOf(false) }
  var showDeniedSnackbar by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { result ->
    granted = result
    asked = true
    if (!result) showDeniedSnackbar = true
  }

  LaunchedEffect(Unit) {
    if (!granted && !asked) {
      launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  // Fire-and-forget snackbar after a denial.
  LaunchedEffect(showDeniedSnackbar) {
    if (!showDeniedSnackbar) return@LaunchedEffect
    scope.launch {
      val res = snackbarHostState.showSnackbar(
        message = DENIED_SNACKBAR_MESSAGE,
        actionLabel = "Dismiss",
        duration = SnackbarDuration.Long,
      )
      if (res == SnackbarResult.ActionPerformed || res == SnackbarResult.Dismissed) {
        showDeniedSnackbar = false
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    content()
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(16.dp),
    ) { data ->
      Snackbar(snackbarData = data)
    }
  }
}

internal const val DENIED_SNACKBAR_MESSAGE =
  "Notifications disabled — playback controls won't appear in your notification tray."
