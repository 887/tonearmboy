package com.eight87.tonearmboy.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.eight87.tonearmboy.ui.common.FastScrollbar
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.PlaylistStore
import com.eight87.tonearmboy.data.model.Playlist
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * D.27.6 — playlists rendered as a grid of square tiles.
 *
 * Tile cover resolution (in order):
 *  1. The user's chosen `coverUri` on the playlist row.
 *  2. The first track's `mediaStoreAlbumId` album-art (if non-null and
 *     the user has covers turned on / Balanced).
 *  3. A letter avatar with the playlist's first letter (existing
 *     fallback used elsewhere in the app via `initialKey`).
 *
 * Long-press a tile → context menu: Rename / Choose cover / Delete.
 *
 * The "New playlist" entry replaces the old `AlertDialog` with a
 * Material 3 `ModalBottomSheet` carrying a name field + an optional
 * "Pick a cover" chooser + Create button.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsTilesScreen(
  repository: PlaylistStore,
  onPlaylistClick: (Long) -> Unit,
  onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
  onDeletePlaylist: (Long) -> Unit = {},
  onSetPlaylistCover: (Long, String?) -> Unit = { _, _ -> },
  twoColumn: Boolean = false,
) {
  val playlists by repository.observePlaylists().collectAsState(initial = emptyList())
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // R.F.19 — modal dialog state collapsed into a single sealed slot.
  var dialog by remember { mutableStateOf<PlaylistDialogState>(PlaylistDialogState.None) }
  // Per-tile context menu remains anchored per-tile (not modal).
  var contextMenuFor by remember { mutableStateOf<Playlist?>(null) }

  Box(modifier = Modifier.fillMaxSize().semantics { testTag = "playlists_screen" }) {
    if (playlists.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.playlist_tiles_empty),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.semantics { testTag = "empty_state" },
        )
      }
    } else {
      val gridState = rememberLazyGridState()
      LazyVerticalGrid(
        columns = if (twoColumn) GridCells.Fixed(2) else GridCells.Adaptive(minSize = 140.dp),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp)
          .semantics { testTag = "playlists_grid" },
      ) {
        items(playlists, key = { it.id }) { p ->
          PlaylistTile(
            playlist = p,
            repository = repository,
            onClick = { onPlaylistClick(p.id) },
            onLongPress = { contextMenuFor = p },
            contextMenuAnchorOpen = contextMenuFor?.id == p.id,
            onContextDismiss = { contextMenuFor = null },
            onRenameRequest = { contextMenuFor = null; dialog = PlaylistDialogState.Rename(p) },
            onDeleteRequest = { contextMenuFor = null; dialog = PlaylistDialogState.Delete(p) },
            onChooseCoverRequest = { contextMenuFor = null; dialog = PlaylistDialogState.CoverChooser(p) },
          )
        }
      }
      FastScrollbar(state = gridState, modifier = Modifier.align(Alignment.CenterEnd))
    }
    ExtendedFloatingActionButton(
      onClick = { dialog = PlaylistDialogState.Create },
      icon = { Icon(Icons.Filled.Add, contentDescription = null) },
      text = { Text(stringResource(R.string.playlist_tiles_new_fab)) },
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).semantics { testTag = "new_playlist_fab" },
    )
  }

  PlaylistDialogHost(
    state = dialog,
    repository = repository,
    context = context,
    onDismiss = { dialog = PlaylistDialogState.None },
    onCreate = { name -> scope.launch { repository.createPlaylist(name) } },
    onRename = onRenamePlaylist,
    onDelete = onDeletePlaylist,
    onSetCover = onSetPlaylistCover,
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTile(
  playlist: Playlist,
  repository: PlaylistStore,
  onClick: () -> Unit,
  onLongPress: () -> Unit,
  contextMenuAnchorOpen: Boolean,
  onContextDismiss: () -> Unit,
  onRenameRequest: () -> Unit,
  onDeleteRequest: () -> Unit,
  onChooseCoverRequest: () -> Unit,
) {
  val firstAlbumId by repository.observePlaylistFirstAlbumArt(playlist.id)
    .collectAsState(initial = null)
  val resolvedCover = remember(playlist.coverUri, firstAlbumId) {
    resolvePlaylistCover(playlist.coverUri, firstAlbumId)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onClick, onLongClick = onLongPress)
      .padding(4.dp)
      .semantics { testTag = "playlist_tile" },
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      when (resolvedCover) {
        is PlaylistCoverSource.Custom -> AsyncImage(
          model = resolvedCover.uri,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
        is PlaylistCoverSource.AlbumArt -> CoverArt(
          albumId = resolvedCover.albumId,
          size = 140.dp,
          mode = AlbumCoversMode.Balanced,
          modifier = Modifier.fillMaxSize(),
        )
        is PlaylistCoverSource.Letter -> Text(
          text = letterFor(playlist.name),
          style = MaterialTheme.typography.headlineLarge,
          modifier = Modifier.semantics { testTag = "playlist_letter_avatar" },
        )
      }
      DropdownMenu(
        expanded = contextMenuAnchorOpen,
        onDismissRequest = onContextDismiss,
        modifier = Modifier.semantics { testTag = "playlist_context_menu" },
      ) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.playlist_context_rename)) },
          onClick = onRenameRequest,
          modifier = Modifier.semantics { testTag = "playlist_context_rename" },
        )
        DropdownMenuItem(
          text = { Text(stringResource(R.string.playlist_context_choose_cover)) },
          onClick = onChooseCoverRequest,
          modifier = Modifier.semantics { testTag = "playlist_context_cover" },
        )
        DropdownMenuItem(
          text = { Text(stringResource(R.string.playlist_context_delete)) },
          onClick = onDeleteRequest,
          modifier = Modifier.semantics { testTag = "playlist_context_delete" },
        )
      }
    }
    Text(
      text = playlist.name,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      modifier = Modifier
        .padding(top = 6.dp, start = 4.dp, end = 4.dp)
        .semantics { testTag = "playlist_tile_name" },
    )
    Text(
      text = pluralStringResource(R.plurals.playlist_track_count, playlist.trackCount, playlist.trackCount),
      style = MaterialTheme.typography.bodySmall,
      maxLines = 1,
      modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPlaylistSheet(
  onDismiss: () -> Unit,
  onCreate: (String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var name by remember { mutableStateOf("") }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "new_playlist_sheet" },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(R.string.playlist_create_dialog_title), style = MaterialTheme.typography.titleMedium)
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.playlist_name_label)) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "new_playlist_field" },
      )
      Text(
        text = stringResource(R.string.playlist_new_sheet_hint),
        style = MaterialTheme.typography.bodySmall,
      )
      Button(
        onClick = {
          val trimmed = name.trim()
          if (trimmed.isNotEmpty()) onCreate(trimmed)
        },
        enabled = name.trim().isNotEmpty(),
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "new_playlist_confirm" },
      ) { Text(stringResource(R.string.playlist_create_confirm)) }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistCoverChooserSheet(
  playlist: Playlist,
  repository: PlaylistStore,
  onDismiss: () -> Unit,
  onChoose: (String?) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri != null) onChoose(uri.toString())
  }

  var showFromTracksSubsheet by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "playlist_cover_chooser" },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        stringResource(R.string.playlist_cover_chooser_title, playlist.name),
        style = MaterialTheme.typography.titleMedium,
      )
      Button(
        onClick = { showFromTracksSubsheet = true },
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "cover_from_track" },
      ) { Text(stringResource(R.string.playlist_cover_pick_from_track)) }
      OutlinedButton(
        onClick = {
          // D.27.6 — `OpenDocument` instead of `GetContent` so we can
          // call `takePersistableUriPermission` on the resulting URI.
          launcher.launch(arrayOf("image/*"))
        },
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "cover_from_device" },
      ) { Text(stringResource(R.string.playlist_cover_pick_from_device)) }
      OutlinedButton(
        onClick = { onChoose(null) },
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "cover_use_letter" },
      ) { Text(stringResource(R.string.playlist_cover_use_letter)) }
    }
  }

  if (showFromTracksSubsheet) {
    PickFromTrackSheet(
      playlist = playlist,
      repository = repository,
      onDismiss = { showFromTracksSubsheet = false },
      onPick = { albumId ->
        showFromTracksSubsheet = false
        // Encode track album-art as our internal scheme so the
        // resolver branches correctly on read.
        onChoose(albumArtSchemeUri(albumId))
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickFromTrackSheet(
  playlist: Playlist,
  repository: PlaylistStore,
  onDismiss: () -> Unit,
  onPick: (Long) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
  LaunchedEffect(playlist.id) {
    tracks = repository.observePlaylistTracks(playlist.id).first()
  }
  val candidates = tracks.filter { it.mediaStoreAlbumId != null }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "cover_pick_from_track_sheet" },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(stringResource(R.string.playlist_cover_pick_track_title), style = MaterialTheme.typography.titleMedium)
      if (candidates.isEmpty()) {
        Text(
          text = stringResource(R.string.playlist_cover_no_art),
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          items(candidates, key = { it.id }) { t ->
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                  onClick = { onPick(t.mediaStoreAlbumId!!) },
                  onLongClick = {},
                )
                .padding(vertical = 8.dp)
                .semantics { testTag = "cover_track_row" },
            ) {
              val unknown = stringResource(R.string.playlist_track_unknown_subtitle)
              Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
              Text(
                text = listOfNotNull(t.artist, t.album).joinToString(" · ").ifEmpty { unknown },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * D.27.6 — pure cover-resolution chain. Public so unit tests can pin
 * the precedence (chosen URI > first-track album art > letter).
 */
sealed interface PlaylistCoverSource {
  data class Custom(val uri: String) : PlaylistCoverSource
  data class AlbumArt(val albumId: Long) : PlaylistCoverSource
  object Letter : PlaylistCoverSource
}

private const val ALBUM_ART_SCHEME = "tonearmboy-albumart://"

/**
 * Encode a MediaStore album id as an opaque tonearmboy URI for the
 * playlist `coverUri` column. Round-trips through
 * [resolvePlaylistCover].
 */
internal fun albumArtSchemeUri(albumId: Long): String = "$ALBUM_ART_SCHEME$albumId"

internal fun resolvePlaylistCover(
  coverUri: String?,
  firstTrackAlbumId: Long?,
): PlaylistCoverSource {
  if (!coverUri.isNullOrBlank()) {
    if (coverUri.startsWith(ALBUM_ART_SCHEME)) {
      val id = coverUri.removePrefix(ALBUM_ART_SCHEME).toLongOrNull()
      if (id != null) return PlaylistCoverSource.AlbumArt(id)
      // Unparseable id → fall through to the next layer.
    } else {
      return PlaylistCoverSource.Custom(coverUri)
    }
  }
  if (firstTrackAlbumId != null) return PlaylistCoverSource.AlbumArt(firstTrackAlbumId)
  return PlaylistCoverSource.Letter
}

internal fun letterFor(name: String): String {
  val ch = name.firstOrNull()?.uppercaseChar() ?: '#'
  return if (ch.isLetter()) ch.toString() else "#"
}
