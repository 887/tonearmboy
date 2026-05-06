package com.eight87.tonearmboy.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.TrackSource
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.library.libraryListCard
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Search screen — full-text search over titles / artists / albums via
 * the FTS-backed [TrackSource.search]. The query string is
 * debounced inside [SearchInputReducer] (unit-tested) so we do not
 * thrash the FTS index on every keystroke.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
  repository: TrackSource,
  onTrackClick: (Track) -> Unit,
  onBack: () -> Unit = {},
) {
  var rawQuery by rememberSaveable { mutableStateOf("") }
  val state = remember { SearchInputReducer() }
  val effectiveQuery = state.reduce(rawQuery)

  val results by produceState(initialValue = emptyList<Track>(), key1 = effectiveQuery) {
    val flow = if (effectiveQuery.isBlank()) flowOf(emptyList()) else repository.search(effectiveQuery)
    flow.collectLatest { value = it }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        expandedHeight = 48.dp,
        title = { Text(stringResource(R.string.search_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.search_cd_back))
          }
        },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding).semantics { testTag = "search_screen" }) {
      OutlinedTextField(
        value = rawQuery,
        onValueChange = { rawQuery = it },
        label = { Text(stringResource(R.string.search_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { testTag = "search_input" },
      )
      if (rawQuery.isBlank()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
          Text(stringResource(R.string.search_empty_hint), style = MaterialTheme.typography.bodyMedium)
        }
      } else if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
          Text(stringResource(R.string.search_no_matches, rawQuery), style = MaterialTheme.typography.bodyMedium)
        }
      } else {
        // D.16.1 — wrap search result rows in the M3 Expressive grouped
        // card so the chrome lines up with library + settings surfaces.
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
            .libraryListCard()
            .semantics { testTag = "search_results" },
        ) {
          items(results, key = { it.id }) { t ->
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onTrackClick(t) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
              Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
              val unknown = stringResource(R.string.search_unknown_track_subtitle)
              Text(
                listOfNotNull(t.artist, t.album).joinToString(" · ").ifEmpty { unknown },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
              )
            }
            HorizontalDivider()
          }
        }
      }
    }
  }
}
