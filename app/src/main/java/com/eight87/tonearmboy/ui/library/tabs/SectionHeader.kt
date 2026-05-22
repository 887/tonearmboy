package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Sticky-header section cell — M3 Expressive pill backplate.
 * Matches whisperboy's SectionHeader shape: `surfaceContainerHigh`
 * one notch above the page surface, clipped to a 12 dp rounded rect
 * so corners read as a pill rather than a hard band.
 */
@Composable
internal fun SectionHeader(letter: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Text(
      text = letter,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
