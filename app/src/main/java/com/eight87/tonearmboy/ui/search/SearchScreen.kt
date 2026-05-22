package com.eight87.tonearmboy.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.TrackSource
import com.eight87.tonearmboy.data.model.Track
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.tonearmboy.ui.library.CoverArt
import com.eight87.tonearmboy.ui.library.LocalTrackCoverUris
import com.eight87.tonearmboy.ui.library.libraryListCard
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
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
  val focusRequester = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboard?.show()
  }

  val results by produceState(initialValue = emptyList<Track>(), key1 = effectiveQuery) {
    val flow = if (effectiveQuery.isBlank()) flowOf(emptyList()) else repository.search(effectiveQuery)
    flow.collectLatest { value = it }
  }

  // Same per-track cover URI map LibraryScreen provides — without
  // this, CoverArt falls back to MediaStore's albumart URI which
  // 404s for unknown-album tracks. Search is a top-level route so it
  // doesn't inherit the library's CompositionLocal; provide it here.
  val pinnedCoverUris: Map<Long, String> by repository.observeTrackCoverUris()
    .collectAsStateWithLifecycle(initialValue = emptyMap<Long, String>())

  CompositionLocalProvider(LocalTrackCoverUris provides pinnedCoverUris) {
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
            contentDescription = stringResource(R.string.search_cd_back),
          )
        }
        TextField(
          value = rawQuery,
          onValueChange = { rawQuery = it },
          placeholder = { Text(stringResource(R.string.search_placeholder)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
          ),
          trailingIcon = {
            if (rawQuery.isNotEmpty()) {
              IconButton(onClick = { rawQuery = "" }) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_cd_clear))
              }
            }
          },
          modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester)
            .semantics { testTag = "search_input" },
        )
      }
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding).semantics { testTag = "search_screen" }) {
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
        // Opaque backplate so search results don't bleed into the
        // album-art wallpaper under glass-mode chrome. surfaceContainer
        // stays opaque even when AlbumArtBackground is active.
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(bottom = 16.dp)
            .libraryListCard()
            .semantics { testTag = "search_results" },
        ) {
          items(results, key = { it.id }) { t ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onTrackClick(t) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CoverArt(
                albumId = t.mediaStoreAlbumId,
                size = 48.dp,
                mode = AlbumCoversMode.Default,
                trackId = t.id,
                trackSource = repository,
                modifier = Modifier
                  .size(48.dp)
                  .clip(RoundedCornerShape(4.dp)),
              )
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.fillMaxWidth()) {
                Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                val unknown = stringResource(R.string.search_unknown_track_subtitle)
                Text(
                  listOfNotNull(t.artist, t.album).joinToString(" · ").ifEmpty { unknown },
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 1,
                )
              }
            }
            HorizontalDivider()
          }
        }
      }
    }
  }
  }
}
