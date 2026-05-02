package com.eight87.tonearm.ui.search

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.model.Track
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Search screen — full-text search over titles / artists / albums via
 * the FTS-backed [LibraryRepository.search]. The query string is
 * debounced inside [SearchInputReducer] (unit-tested) so we do not
 * thrash the FTS index on every keystroke.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
  repository: LibraryRepository,
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
        title = { Text("Search") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding).semantics { testTag = "search_screen" }) {
      OutlinedTextField(
        value = rawQuery,
        onValueChange = { rawQuery = it },
        label = { Text("Search title, artist, album") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { testTag = "search_input" },
      )
      if (rawQuery.isBlank()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
          Text("Start typing to search.", style = MaterialTheme.typography.bodyMedium)
        }
      } else if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
          Text("No matches for \"$rawQuery\".", style = MaterialTheme.typography.bodyMedium)
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize().semantics { testTag = "search_results" }) {
          items(results, key = { it.id }) { t ->
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onTrackClick(t) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
              Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
              Text(
                listOfNotNull(t.artist, t.album).joinToString(" · ").ifEmpty { "Unknown" },
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
