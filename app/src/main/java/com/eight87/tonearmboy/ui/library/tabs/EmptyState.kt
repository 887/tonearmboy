package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

/** Centered empty-state message used by every library tab. */
@Composable
internal fun EmptyState(@StringRes messageRes: Int) {
  EmptyState(stringResource(messageRes))
}

@Composable
internal fun EmptyState(message: String) {
  Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.semantics { testTag = "empty_state" },
    )
  }
}
