package com.eight87.tonearmboy.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.tonearmboy.data.LibraryScanner

/**
 * Compact "library scanning…" bar surfaced at the top of every library
 * screen while a scan is in progress. Hidden when no scan is running
 * (the StateFlow emits `null` outside scan windows).
 *
 * Renders a `LinearProgressIndicator` driven by `progress.fraction`
 * plus a one-line "<scanned>/<total> · <currentTitle>" caption. The
 * bar is purely a UI consumer of [LibraryScanner.scanProgress];
 * the scanner runs entirely on `Dispatchers.IO` and bounds parallelism
 * at 4, so this component never blocks the main thread.
 */
@Composable
fun ScanProgressBar(
  scanner: LibraryScanner,
  modifier: Modifier = Modifier,
) {
  val progress by scanner.scanProgress.collectAsStateWithLifecycle()
  AnimatedVisibility(
    visible = progress != null,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier,
  ) {
    val p = progress ?: return@AnimatedVisibility
    Surface(
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (p.total > 0) "Scanning ${p.scanned} of ${p.total}" else "Scanning…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (p.total > 0) {
            Text(
              text = "${(p.fraction * 100).toInt()}%",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (p.total > 0) {
          LinearProgressIndicator(
            progress = { p.fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (!p.currentTitle.isNullOrBlank()) {
          Text(
            text = p.currentTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
