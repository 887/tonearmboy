package com.eight87.tonearmboy.ui.settings.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.eight87.tonearmboy.R

/**
 * Pill-shaped search bar pinned at the top of the Settings root. Tapping
 * it opens [SettingsSearchScreen] via [onOpen]. The bar itself is
 * read-only; the real `TextField` lives on the search overlay so the
 * keyboard appears alongside the result list.
 */
@Composable
fun SettingsSearchBar(
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen)
      .background(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
      )
      .padding(horizontal = 20.dp, vertical = 14.dp)
      .semantics { testTag = "settings_search_bar" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Filled.Search,
      contentDescription = stringResource(R.string.settings_cd_search_settings),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(12.dp))
    Text(
      text = stringResource(R.string.settings_search_bar_label),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Full-screen search overlay. Owns a [TextField], a live-filtered
 * result list, and a "no matches" / empty state. Tapping a result fires
 * [onResult], which navigates to the entry's destination and seeds
 * [LocalHighlightedSettingId] so the destination row briefly flashes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSearchScreen(
  onBack: () -> Unit,
  onResult: (NavKey, String) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboard?.show()
  }

  val results by remember {
    derivedStateOf { SettingsCatalog.search(query) }
  }

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.settings_cd_back),
          )
        }
        TextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text(stringResource(R.string.settings_search_field_placeholder)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
          ),
          trailingIcon = {
            if (query.isNotEmpty()) {
              IconButton(onClick = { query = "" }) {
                Icon(
                  Icons.Filled.Clear,
                  contentDescription = stringResource(R.string.settings_cd_clear),
                )
              }
            }
          },
          modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester)
            .semantics { testTag = "settings_search_field" },
        )
      }
    },
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
      when {
        query.isBlank() -> SearchEmptyState(
          message = stringResource(R.string.settings_search_empty_initial),
          tag = "search_empty_initial",
        )
        results.isEmpty() -> SearchEmptyState(
          message = stringResource(R.string.settings_search_no_matches, query),
          tag = "search_empty_no_matches",
        )
        else -> ResultsList(results = results, onResult = onResult)
      }
    }
  }
}

@Composable
private fun ResultsList(
  results: List<SettingsCatalogEntry>,
  onResult: (NavKey, String) -> Unit,
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .semantics { testTag = "settings_search_results" },
    contentPadding = androidx.compose.foundation.layout.PaddingValues(
      horizontal = SettingsDimens.PagePadding,
      vertical = 8.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    items(results, key = { it.id }) { entry ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onResult(entry.destination, entry.id) }
          .padding(horizontal = 8.dp, vertical = 12.dp)
          .semantics { testTag = "search_result_${entry.id}" },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = entry.icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(SettingsDimens.IconSize),
        )
        Spacer(Modifier.size(SettingsDimens.IconLabelGap))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(entry.labelRes),
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = SettingsCatalog.breadcrumbPath(entry),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun SearchEmptyState(message: String, tag: String) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp)
      .semantics { testTag = tag },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
