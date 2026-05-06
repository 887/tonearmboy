package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Two-line list row used by Artists / Genres / Playlists list views.
 * Optional onClick — null for non-tappable callers. Optional
 * [onLongClick] enters multi-select mode (long-press is the standard
 * gesture for selection start across every tab in the library).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TwoLineRow(
  primary: String,
  secondary: String,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .let {
        when {
          onClick != null && onLongClick != null ->
            it.combinedClickable(onClick = onClick, onLongClick = onLongClick)
          onClick != null -> it.clickable(onClick = onClick)
          else -> it
        }
      }
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Text(primary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    Text(secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
  }
  HorizontalDivider()
}
