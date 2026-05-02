package com.eight87.tonearm.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eight87.tonearm.BuildConfig
import com.eight87.tonearm.R
import com.eight87.tonearm.ui.settings.catalog.SettingsCard
import com.eight87.tonearm.ui.settings.catalog.SettingsDimens
import com.eight87.tonearm.ui.settings.catalog.SettingsRow
import com.eight87.tonearm.ui.settings.catalog.SettingsRowDivider
import kotlinx.coroutines.launch

/**
 * D.16.4 — About sub-page. Renders inside the same M3 Expressive grouped
 * cards (`SettingsCard` / `SettingsRow`) used elsewhere so the chrome
 * lines up with every other settings surface.
 *
 * Layout:
 *   - "Build" card: app name, version + SHA, build date.
 *   - "Source" card: GitHub repo link (browser intent) + MIT license.
 *   - "Credits" card: visual + chrome references and tech stack.
 *
 * D.16.5 — the build-version row hosts a tap counter (state machine in
 * [EasterEggController]). After three quick taps the fullscreen fox
 * dialog appears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  onBack: () -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // D.16.5 — counter lives across recompositions; reused across
  // multiple reveals (counter resets internally on reveal).
  val easterEgg = remember { EasterEggController() }
  var foxVisible by remember { mutableStateOf(false) }

  val versionName = "tonearm ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})"
  val buildDate = "Built ${BuildConfig.BUILD_DATE} (UTC)"

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("About") },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.semantics { testTag = "about_back" },
          ) {
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
        .semantics { testTag = "about_screen" },
      verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
    ) {
      // ---- Build card ----
      SettingsCard(
        title = "Build",
        modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
      ) {
        SettingsRow(
          id = "about.app_name",
          icon = Icons.Outlined.Info,
          label = "Application",
          subtitle = "tonearm — modern Android music player",
          onClick = null,
        )
        SettingsRowDivider()
        // D.16.5 — easter-egg target. The whole row is clickable; the
        // controller decides what to surface based on tap count.
        SettingsRow(
          id = "about.version",
          icon = Icons.Outlined.Numbers,
          label = "Version",
          subtitle = versionName,
          onClick = {
            when (easterEgg.tap(System.currentTimeMillis())) {
              EasterEggController.Outcome.FirstPromptSnackbar -> scope.launch {
                snackbarHostState.showSnackbar("Click 2 more times for a treat")
              }
              EasterEggController.Outcome.SecondPromptSnackbar -> scope.launch {
                snackbarHostState.showSnackbar("1 more time")
              }
              EasterEggController.Outcome.Reveal -> {
                foxVisible = true
              }
            }
          },
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.build_date",
          icon = Icons.Outlined.Schedule,
          label = "Build date",
          subtitle = buildDate,
          onClick = null,
        )
      }

      // ---- Source card ----
      SettingsCard(
        title = "Source",
        modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
      ) {
        SettingsRow(
          id = "about.github",
          icon = Icons.Outlined.Launch,
          label = "GitHub repository",
          subtitle = "github.com/887/tonearm",
          onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
          },
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.license",
          icon = Icons.Outlined.Article,
          label = "License",
          subtitle = "MIT — see LICENSE",
          onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LICENSE_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
          },
        )
      }

      // ---- Credits card ----
      // Visual references only. NO code from Auxio or Harmony Music
      // was copied into tonearm. This is a clean-room implementation —
      // we looked at their UIs to inform our own, but every line in
      // this app is original Kotlin. Critical because Auxio is GPL-3.0
      // and tonearm is MIT; if any Auxio code were here, our license
      // would be tainted. It isn't.
      SettingsCard(
        title = "Credits",
        modifier = Modifier.padding(horizontal = SettingsDimens.PagePadding),
      ) {
        SettingsRow(
          id = "about.credits.cleanroom",
          icon = Icons.Outlined.Info,
          label = "Visual references only",
          subtitle = "No code copied from the projects below. Clean-room implementation, MIT-licensed throughout.",
          onClick = null,
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.credits.auxio",
          icon = Icons.Outlined.Favorite,
          label = "Auxio (GPL-3.0)",
          subtitle = "Visual reference for the player surface and library chrome. github.com/OxygenCobalt/Auxio",
          onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AUXIO_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
          },
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.credits.harmony",
          icon = Icons.Outlined.Favorite,
          label = "Harmony Music (GPL-3.0)",
          subtitle = "Chrome reference (rail-and-content boundary, M3 Expressive cards). github.com/anandnet/Harmony-Music",
          onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HARMONY_URL))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
          },
        )
        SettingsRowDivider()
        SettingsRow(
          id = "about.credits.media3",
          icon = Icons.Outlined.Code,
          label = "Media3 + Compose + Room",
          subtitle = "AndroidX libraries: ExoPlayer/MediaSession, Jetpack Compose, Room.",
          onClick = null,
        )
      }
    }
  }

  if (foxVisible) {
    EasterEggFoxDialog(onDismiss = { foxVisible = false })
  }
}

/**
 * D.16.5.3 — fullscreen modal for the fox reveal. Tap-outside or
 * back-press dismisses; a 70 % black scrim sits behind the image so the
 * fox stands out regardless of theme.
 */
@Composable
private fun EasterEggFoxDialog(onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.7f))
        .clickable(onClick = onDismiss)
        .semantics { testTag = "easter_egg_fox_scrim" },
      contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
      Image(
        painter = painterResource(id = R.drawable.easter_egg_fox),
        contentDescription = "Stay pawsitive",
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp)
          .semantics { testTag = "easter_egg_fox_image" },
      )
    }
  }
}

private const val GITHUB_URL = "https://github.com/887/tonearm"
private const val LICENSE_URL = "https://github.com/887/tonearm/blob/main/LICENSE"
private const val AUXIO_URL = "https://github.com/OxygenCobalt/Auxio"
private const val HARMONY_URL = "https://github.com/anandnet/Harmony-Music"
