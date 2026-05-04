package com.eight87.tonearmboy.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.material3.Scaffold
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.eight87.tonearmboy.AppGraph
import com.eight87.tonearmboy.playback.PlaybackService
import com.eight87.tonearmboy.ui.library.AlbumDetailScreen
import com.eight87.tonearmboy.ui.library.AlbumDetailTrackAction
import com.eight87.tonearmboy.ui.library.ArtistDetailScreen
import com.eight87.tonearmboy.ui.library.GenreDetailScreen
import com.eight87.tonearmboy.ui.library.LibraryScreen
import com.eight87.tonearmboy.ui.library.PlaylistDetailScreen
import com.eight87.tonearmboy.ui.library.PlaylistPickerSheet
import com.eight87.tonearmboy.ui.playing.MiniPlayer
import com.eight87.tonearmboy.ui.playing.NowPlayingScreen
import com.eight87.tonearmboy.data.model.Track
import com.eight87.tonearmboy.ui.search.SearchScreen
import com.eight87.tonearmboy.ui.settings.AlbumCoversMode
import com.eight87.tonearmboy.ui.settings.CustomBarAction
import com.eight87.tonearmboy.ui.settings.PlayFromItemDetails
import com.eight87.tonearmboy.ui.settings.PlayFromLibrary
import com.eight87.tonearmboy.ui.settings.SettingsAudioScreen
import com.eight87.tonearmboy.ui.settings.SettingsContentScreen
import com.eight87.tonearmboy.ui.settings.AboutScreen
import com.eight87.tonearmboy.ui.settings.SettingsLookAndFeelScreen
import com.eight87.tonearmboy.ui.settings.MusicSourcesDialog
import com.eight87.tonearmboy.ui.library.CustomTabEditorScreen
import com.eight87.tonearmboy.ui.library.FilterUniverse
import com.eight87.tonearmboy.ui.settings.SettingsPersonalizeScreen
import com.eight87.tonearmboy.ui.settings.SettingsScreen
import com.eight87.tonearmboy.ui.settings.catalog.LocalHighlightedSettingId
import com.eight87.tonearmboy.ui.settings.catalog.SettingsSearchScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Root composable: a [NavDisplay] over a single back stack rooted at
 * [LibraryRoot]. There is **no bottom navigation**. The library is the
 * landing surface (via its own top tabs); search and settings are
 * pushed on top of it from the top app bar.
 *
 * The mini-player floats at the bottom of every destination except
 * [NowPlaying] (where the full player is already visible).
 */
@OptIn(UnstableApi::class)
@Composable
fun TonearmboyApp(
  graph: AppGraph,
  /**
   * D.20.1 — bumped each time `MainActivity.handleIntent` receives a
   * notification deeplink. Triggers a `LaunchedEffect` that pushes
   * the matching destination onto the back stack.
   */
  deeplinkNonce: Int = 0,
  pendingDeeplink: String? = null,
  onDeeplinkConsumed: () -> Unit = {},
) {
  val backStack = remember { TonearmboyBackStack(LibraryRoot) }
  val current = backStack.backStack.lastOrNull() ?: LibraryRoot

  // D.20.1 — react to a notification deeplink. The activity hands us
  // a nonce so re-tapping the notification (or tapping it after a
  // back-press out of NowPlaying) re-triggers the push. We use
  // `popToOrPush` so the back stack stays sane: if NowPlaying is
  // already on top, do nothing; if it's deeper, pop to it.
  LaunchedEffect(deeplinkNonce, pendingDeeplink) {
    when (pendingDeeplink) {
      PlaybackService.DEEPLINK_NOW_PLAYING -> {
        if (current !is NowPlaying) {
          backStack.popToOrPush(NowPlaying)
        }
        onDeeplinkConsumed()
      }
      else -> Unit
    }
  }

  val playback = graph.playbackUiController
  val playbackState by playback.state.collectAsStateWithLifecycle()
  // R.E.6 — settings → playback mirrors live in PlaybackSettingsBridge,
  // not inline LaunchedEffects.
  PlaybackSettingsBridge(
    transport = playback,
    replayGain = playback,
    settings = graph.settingsRepository,
  )
  // R.B.5 — per-key Flow reads via the facets the repository implements.
  // Each Compose subscription is its own Flow; toggling a setting only
  // recomposes screens that read that specific key.
  val customBarAction by graph.settingsRepository.customBarAction.flow
    .collectAsStateWithLifecycle(initialValue = CustomBarAction.Default)
  val albumCoversMode by graph.settingsRepository.albumCoversMode.flow
    .collectAsStateWithLifecycle(initialValue = AlbumCoversMode.Default)
  val playFromLibrary by graph.settingsRepository.playFromLibrary.flow
    .collectAsStateWithLifecycle(initialValue = PlayFromLibrary.Default)
  val playFromItemDetails by graph.settingsRepository.playFromItemDetails.flow
    .collectAsStateWithLifecycle(initialValue = PlayFromItemDetails.Default)
  val snackbarHostState = remember { SnackbarHostState() }

  // Phase F — file-deletion entry. Hosting the launcher at the app
  // root means the consent dialog can return after a screen pop.
  val trackDeleter = remember { com.eight87.tonearmboy.data.delete.TrackDeleter(graph.applicationContextForUi) }
  val onDeleteTracks = com.eight87.tonearmboy.ui.library.rememberDeleteFlow(
    scanner = graph.scanner,
    playback = playback,
    snackbarHostState = snackbarHostState,
    applicationScope = graph.applicationScope,
    trackDeleter = trackDeleter,
  )

  // Single shared title cell driven by per-screen LaunchedEffects.
  val sectionTitle = remember { mutableStateOf("tonearmboy") }

  // Settings search seeds this state with the id of the row to flash
  // when navigating to its destination sub-page; the receiving row
  // animates a 300 ms background colour change and then clears it.
  val highlightedSettingId = remember { mutableStateOf<String?>(null) }

  // Keep the MediaController bound for the lifetime of the activity.
  // The full-screen NowPlaying re-uses this same connection.
  LaunchedEffect(Unit) {
    // D.9b.1 — give the controller a library handle for ReplayGain
    // album lookups before it sees the first track transition.
    playback.setLibrary(graph.tracks)
    playback.connect()
  }

  val showMiniPlayer = playbackState.hasMedia && current !is NowPlaying

  val onComingSoon: (String) -> Unit = { feature ->
    graph.applicationScope.launch {
      snackbarHostState.showSnackbar("$feature — coming in v1.1")
    }
  }

  // D.15.6.2 — track currently being added to a playlist; non-null
  // shows the picker sheet on top of whichever destination triggered it.
  var addingToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
  // D.27.2 — bulk version: selected ids from the multi-select bar
  // routed through the same picker sheet.
  var addingToPlaylistTrackIds by remember { mutableStateOf<List<Long>?>(null) }
  // D.27.5 — current library filter, owned at the app root so it
  // survives tab switches. Cleared on app close (no DataStore
  // persistence in v1, per the plan).
  var libraryFilter by remember {
    mutableStateOf(com.eight87.tonearmboy.data.FilterCriteria())
  }

  // D.17.3 — Music sources dialog visibility. Lifted here so the
  // dialog can be opened from the Settings root row (OpenDialog
  // catalog kind) without pushing a new destination onto the stack.
  var showMusicSourcesDialog by remember { mutableStateOf(false) }

  // R.E.4 — playlist export/import surface lifted into PlaylistBackupController.
  val playlistBackup = rememberPlaylistBackupController(
    playlists = graph.playlists,
    tracks = graph.tracks,
    snackbar = snackbarHostState,
    applicationScope = graph.applicationScope,
  )
  val onExportPlaylists = playlistBackup.onExport
  val onImportPlaylists = playlistBackup.onImport
  val playlists by graph.playlists.observePlaylists()
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val onRefreshMusic: () -> Unit = {
    graph.applicationScope.launch {
      graph.scanner.rescanNow()
      snackbarHostState.showSnackbar("Refreshed music library")
    }
  }
  val onRescanMusic: () -> Unit = {
    graph.applicationScope.launch {
      graph.scanner.rescanNow()
      snackbarHostState.showSnackbar("Rescanned music library")
    }
  }

  CompositionLocalProvider(
    LocalSectionTitle provides sectionTitle,
    LocalHighlightedSettingId provides highlightedSettingId,
  ) {
  Scaffold(
    bottomBar = {
      if (showMiniPlayer) {
        MiniPlayer(
          state = playbackState,
          onTogglePlayPause = playback::togglePlayPause,
          onClose = playback::stop,
          onExpand = { backStack.push(NowPlaying) },
          onSkipNext = playback::seekToNext,
          onSkipPrevious = playback::seekToPrevious,
          onPlayButtonLongPress = {
            playback.performCustomBarAction(customBarAction)
          },
          onToggleShuffle = playback::toggleShuffle,
          onCycleRepeat = playback::cycleRepeatMode,
          onSeekTo = playback::seekTo,
          albumCoversMode = albumCoversMode,
        )
      }
    },
  ) { innerPadding ->
    NavDisplay(
      backStack = backStack.backStack,
      onBack = { backStack.pop() },
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      entryProvider = entryProvider {
        entry<LibraryRoot> {
          LibraryScreen(
            tracks = graph.tracks,
            albums = graph.albums,
            artists = graph.artists,
            genres = graph.genres,
            playlists = graph.playlists,
            customTabs = graph.customTabs,
            scanner = graph.scanner,
            settingsRepository = graph.settingsRepository,
            onTrackClick = { tracks, index ->
              // D.9a.4: queue depends on the user's "When playing from
              // the library" choice. Surrounding list is whatever the
              // tab gave us; we treat that as both `surroundingList`
              // and `allSongs` since the library Songs tab IS all songs.
              playback.playFromLibrary(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = playFromLibrary,
              )
              backStack.push(NowPlaying)
            },
            onPlaylistClick = { id -> backStack.push(PlaylistDetail(id)) },
            onOpenSearch = { backStack.push(Search) },
            // D.16.3 — top-right wheel goes straight to the Settings root.
            onOpenSettings = { backStack.push(SettingsRootDest) },
            // D.16.2 — the rail's bottom-left gear is the "tab
            // customization" shortcut: it pushes the Personalize sub-page
            // (which holds Library tabs) directly. We push the Settings
            // root underneath so the user's back-stack reflects the
            // canonical Settings → Personalize hierarchy.
            onOpenLibraryTabsConfig = {
              backStack.push(SettingsRootDest)
              backStack.push(SettingsPersonalize)
            },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
            onOpenAlbum = { name, albumArtist -> backStack.push(AlbumDetail(name, albumArtist)) },
            onOpenArtist = { name -> backStack.push(ArtistDetail(name)) },
            onOpenGenre = { name -> backStack.push(GenreDetail(name)) },
            onAddToQueue = { track ->
              playback.addToQueue(track)
              graph.applicationScope.launch {
                snackbarHostState.showSnackbar("Added to queue: ${track.title}")
              }
            },
            onAddToPlaylist = { track -> addingToPlaylistTrack = track },
            onAddTracksToPlaylist = { ids -> addingToPlaylistTrackIds = ids },
            onRenamePlaylist = { id, name ->
              graph.applicationScope.launch {
                graph.playlists.renamePlaylist(id, name)
                snackbarHostState.showSnackbar("Renamed to \"$name\"")
              }
            },
            onDeletePlaylist = { id ->
              graph.applicationScope.launch {
                graph.playlists.deletePlaylist(id)
                snackbarHostState.showSnackbar("Playlist deleted")
              }
            },
            onSetPlaylistCover = { id, uri ->
              graph.applicationScope.launch {
                graph.playlists.setPlaylistCoverUri(id, uri)
                snackbarHostState.showSnackbar(
                  if (uri == null) "Playlist cover cleared" else "Playlist cover updated",
                )
              }
            },
            onOpenPlaylistDetail = { id -> backStack.push(PlaylistDetail(id)) },
            filter = libraryFilter,
            onFilterChange = { libraryFilter = it },
            onDeleteTracks = onDeleteTracks,
          )
        }
        entry<AlbumDetail> { key ->
          AlbumDetailScreen(
            trackSource = graph.tracks,
            albumSource = graph.albums,
            albumName = key.name,
            albumArtist = key.albumArtist,
            albumCoversMode = albumCoversMode,
            onTrackClick = { tracks, index ->
              playback.playFromDetail(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = playFromItemDetails,
              )
              backStack.push(NowPlaying)
            },
            onTrackAction = { track, action ->
              handleDetailTrackAction(
                track = track,
                action = action,
                playback = playback,
                backStack = backStack,
                snackbarHostState = snackbarHostState,
                applicationScope = graph.applicationScope,
                onAddToPlaylist = { addingToPlaylistTrack = it },
                onDeleteTracks = onDeleteTracks,
              )
            },
            onBack = { backStack.pop() },
          )
        }
        entry<ArtistDetail> { key ->
          ArtistDetailScreen(
            trackSource = graph.tracks,
            albumSource = graph.albums,
            artistName = key.name,
            albumCoversMode = albumCoversMode,
            onTrackClick = { tracks, index ->
              playback.playFromDetail(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = playFromItemDetails,
              )
              backStack.push(NowPlaying)
            },
            onTrackAction = { track, action ->
              handleDetailTrackAction(
                track = track,
                action = action,
                playback = playback,
                backStack = backStack,
                snackbarHostState = snackbarHostState,
                applicationScope = graph.applicationScope,
                onAddToPlaylist = { addingToPlaylistTrack = it },
                onDeleteTracks = onDeleteTracks,
              )
            },
            onAlbumClick = { album -> backStack.push(AlbumDetail(album.name, album.artist)) },
            onBack = { backStack.pop() },
          )
        }
        entry<GenreDetail> { key ->
          GenreDetailScreen(
            trackSource = graph.tracks,
            genreName = key.name,
            onTrackClick = { tracks, index ->
              playback.playFromDetail(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = playFromItemDetails,
              )
              backStack.push(NowPlaying)
            },
            onTrackAction = { track, action ->
              handleDetailTrackAction(
                track = track,
                action = action,
                playback = playback,
                backStack = backStack,
                snackbarHostState = snackbarHostState,
                applicationScope = graph.applicationScope,
                onAddToPlaylist = { addingToPlaylistTrack = it },
                onDeleteTracks = onDeleteTracks,
              )
            },
            onBack = { backStack.pop() },
          )
        }
        entry<Search> {
          LaunchedEffect(Unit) { sectionTitle.value = "Search" }
          SearchScreen(
            repository = graph.tracks,
            onTrackClick = { track ->
              playback.playTrack(track)
              backStack.push(NowPlaying)
            },
            onBack = { backStack.pop() },
          )
        }
        entry<NowPlaying> {
          LaunchedEffect(Unit) { sectionTitle.value = "Now Playing" }
          NowPlayingScreen(
            nowPlayingState = playback,
            transport = playback,
            queueCommands = playback,
            onBack = { backStack.pop() },
            albumCoversMode = albumCoversMode,
            onSaveQueueAsPlaylist = { mediaIds ->
              // D.29.1 — feed the queue's track ids into the same bulk
              // playlist-picker overlay multi-select uses, so the user
              // can append to an existing playlist or create a new one.
              val trackIds = mediaIds.mapNotNull { it.toLongOrNull() }
              if (trackIds.isNotEmpty()) {
                addingToPlaylistTrackIds = trackIds
              }
            },
          )
        }
        entry<PlaylistDetail> { key ->
          PlaylistDetailScreen(
            repository = graph.playlists,
            playlistId = key.playlistId,
            onTrackClick = { tracks, index ->
              // D.9a.5: tapped from a detail surface (playlist).
              playback.playFromDetail(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = playFromItemDetails,
              )
              backStack.push(NowPlaying)
            },
            onBack = { backStack.pop() },
            onAddTracks = { id -> backStack.push(PlaylistTrackPicker(id)) },
          )
        }
        entry<PlaylistTrackPicker> { key ->
          LaunchedEffect(Unit) { sectionTitle.value = "Add tracks" }
          com.eight87.tonearmboy.ui.library.TrackPickerScreen(
            trackSource = graph.tracks,
            playlists = graph.playlists,
            playlistId = key.playlistId,
            onBack = { backStack.pop() },
            onConfirm = { toAdd, toRemove ->
              graph.applicationScope.launch {
                toAdd.forEach { tid ->
                  graph.playlists.addTrackToPlaylist(key.playlistId, tid)
                }
                if (toRemove.isNotEmpty()) {
                  // Remove by re-writing the playlist's join rows
                  // without the unchecked ids; the repository helper
                  // doesn't take an id-set directly so we use the same
                  // pattern as the M3U importer.
                  val current = graph.playlists
                    .observePlaylistTracks(key.playlistId).first()
                    .map { it.id }
                  val remaining = current.filter { it !in toRemove }
                  graph.playlists.reorderPlaylist(key.playlistId, remaining)
                }
                snackbarHostState.showSnackbar(
                  "Updated playlist (added ${toAdd.size}, removed ${toRemove.size})",
                )
              }
              backStack.pop()
            },
          )
        }
        entry<SettingsRootDest> {
          LaunchedEffect(Unit) { sectionTitle.value = "Settings" }
          SettingsScreen(
            onBack = { backStack.pop() },
            onLookAndFeel = { backStack.push(SettingsLookAndFeel) },
            onPersonalize = { backStack.push(SettingsPersonalize) },
            onContent = { backStack.push(SettingsContent) },
            onAudio = { backStack.push(SettingsAudio) },
            // D.17.3 — open the modal Music sources dialog instead of
            // pushing a sub-page. The dialog state lives at the app
            // level so search results landing on Settings root can
            // surface it the same way.
            onMusicSources = { showMusicSourcesDialog = true },
            onRefreshMusic = onRefreshMusic,
            onRescanMusic = onRescanMusic,
            onExportPlaylists = onExportPlaylists,
            onImportPlaylists = onImportPlaylists,
            onAbout = { backStack.push(SettingsAbout) },
            onOpenSearch = { backStack.push(SettingsSearch) },
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsAbout> {
          LaunchedEffect(Unit) { sectionTitle.value = "About" }
          AboutScreen(
            onBack = { backStack.pop() },
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsSearch> {
          LaunchedEffect(Unit) { sectionTitle.value = "Search settings" }
          SettingsSearchScreen(
            onBack = { backStack.pop() },
            onResult = { destination, id ->
              // Pop the search overlay, then push (or stay on) the
              // destination sub-page. Seed the highlight so the
              // matched row briefly flashes when it composes.
              highlightedSettingId.value = id
              backStack.pop()
              if (destination !is SettingsRootDest) {
                backStack.push(destination)
              }
            },
          )
        }
        entry<SettingsLookAndFeel> {
          LaunchedEffect(Unit) { sectionTitle.value = "Look and Feel" }
          SettingsLookAndFeelScreen(
            theme = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsPersonalize> {
          LaunchedEffect(Unit) { sectionTitle.value = "Personalize" }
          SettingsPersonalizeScreen(
            playback = graph.settingsRepository,
            tabs = graph.settingsRepository,
            customTabStore = graph.customTabs,
            onBack = { backStack.pop() },
            onOpenCustomTabEditor = { id -> backStack.push(CustomTabEditor(id)) },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<CustomTabEditor> { key ->
          // D.30.3 — full-screen editor for a custom library tab. The
          // entity (when editing) is resolved from the live customTabs
          // Flow on entry; FilterUniverse is built from the same Flows
          // the dialog used pre-D.30.3.
          val customTabs by graph.customTabs.customTabs().collectAsStateWithLifecycle(initialValue = emptyList())
          val genres by graph.genres.observeGenres().collectAsStateWithLifecycle(initialValue = emptyList())
          val artists by graph.artists.observeArtists().collectAsStateWithLifecycle(initialValue = emptyList())
          val albums by graph.albums.observeAlbums().collectAsStateWithLifecycle(initialValue = emptyList())
          val tracks by graph.tracks.observeTracks().collectAsStateWithLifecycle(initialValue = emptyList())
          val existing = key.tabId?.let { id -> customTabs.firstOrNull { it.id == id } }
          val universe = remember(genres, artists, albums, tracks) {
            FilterUniverse(
              genres = genres.map { it.name }.distinct().sorted(),
              artists = artists.map { it.name }.distinct().sorted(),
              albums = albums.map { it.name }.distinct().sorted(),
              minYear = tracks.mapNotNull { it.year }.minOrNull(),
              maxYear = tracks.mapNotNull { it.year }.maxOrNull(),
            )
          }
          LaunchedEffect(existing) {
            sectionTitle.value = if (existing == null) "New custom tab" else "Edit custom tab"
          }
          val scope = rememberCoroutineScope()
          CustomTabEditorScreen(
            existing = existing,
            universe = universe,
            onBack = { backStack.pop() },
            onSave = { name, ct, criteria ->
              scope.launch {
                val entity = (existing ?: com.eight87.tonearmboy.data.db.CustomTabEntity(
                  name = name,
                  position = 0,
                  contentType = ct,
                  criteriaJson = "",
                )).copy(
                  name = name,
                  contentType = ct,
                  criteriaJson = com.eight87.tonearmboy.data.FilterCriteria.toJson(criteria),
                )
                graph.customTabs.upsertCustomTab(entity)
                backStack.pop()
              }
            },
          )
        }
        entry<SettingsContent> {
          LaunchedEffect(Unit) { sectionTitle.value = "Content" }
          SettingsContentScreen(
            library = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        // D.17.3 — SettingsMusicSources is no longer a navigable
        // destination; the row opens the MusicSourcesDialog at the
        // app-root level instead. The NavKey is kept (and registered
        // with a no-op entry) so back-stack save state and search
        // routes that still reference it stay valid; landing on this
        // entry simply pops to Settings root and surfaces the dialog.
        entry<SettingsMusicSources> {
          LaunchedEffect(Unit) {
            backStack.pop()
            showMusicSourcesDialog = true
          }
        }
        entry<SettingsAudio> {
          LaunchedEffect(Unit) { sectionTitle.value = "Audio" }
          SettingsAudioScreen(
            settings = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
            sleepTimer = graph.sleepTimer,
            nowPlayingState = playback,
          )
        }
      },
    )

    // D.15.6.2 — global "Add to playlist" picker overlay. Hosted at the
    // app level so any track-row action (library, detail screens) can
    // trigger the same sheet.
    addingToPlaylistTrack?.let { track ->
      PlaylistPickerSheet(
        playlists = playlists,
        onDismiss = { addingToPlaylistTrack = null },
        onPick = { p ->
          addingToPlaylistTrack = null
          graph.applicationScope.launch {
            graph.playlists.addTrackToPlaylist(p.id, track.id)
            snackbarHostState.showSnackbar("Added \"${track.title}\" to ${p.name}")
          }
        },
        onCreateAndPick = { name ->
          addingToPlaylistTrack = null
          graph.applicationScope.launch {
            val id = graph.playlists.createPlaylist(name)
            graph.playlists.addTrackToPlaylist(id, track.id)
            snackbarHostState.showSnackbar("Added \"${track.title}\" to $name")
          }
        },
      )
    }

    // D.27.2 — bulk Add-to-playlist overlay. Same picker UI as the
    // single-track flow above, but the pick target receives the whole
    // selected-id list and applies them in one launch block. Snackbar
    // copy reflects the count.
    addingToPlaylistTrackIds?.let { ids ->
      PlaylistPickerSheet(
        playlists = playlists,
        onDismiss = { addingToPlaylistTrackIds = null },
        onPick = { p ->
          addingToPlaylistTrackIds = null
          graph.applicationScope.launch {
            ids.forEach { tid -> graph.playlists.addTrackToPlaylist(p.id, tid) }
            snackbarHostState.showSnackbar("Added ${ids.size} tracks to ${p.name}")
          }
        },
        onCreateAndPick = { name ->
          addingToPlaylistTrackIds = null
          graph.applicationScope.launch {
            val id = graph.playlists.createPlaylist(name)
            ids.forEach { tid -> graph.playlists.addTrackToPlaylist(id, tid) }
            snackbarHostState.showSnackbar("Added ${ids.size} tracks to $name")
          }
        },
      )
    }

    // D.17.3 — Auxio-pattern Music sources dialog. Toggled from the
    // Settings root row (RowKind.OpenDialog) and from search results
    // routed to the SettingsMusicSources NavKey.
    if (showMusicSourcesDialog) {
      MusicSourcesDialog(
        settings = graph.settingsRepository,
        scanner = graph.scanner,
        onDismiss = { showMusicSourcesDialog = false },
      )
    }

    // R.E.4 — playlist-import collision dialog rendered from the
    // PlaylistBackupController; pending state lives inside the
    // controller, not here.
    playlistBackup.Overlay()
  }
  }
}

@OptIn(UnstableApi::class)
private fun handleDetailTrackAction(
  track: Track,
  action: AlbumDetailTrackAction,
  playback: com.eight87.tonearmboy.playback.PlaybackUiController,
  backStack: TonearmboyBackStack,
  snackbarHostState: SnackbarHostState,
  applicationScope: kotlinx.coroutines.CoroutineScope,
  onAddToPlaylist: (Track) -> Unit,
  onDeleteTracks: (List<Track>) -> Unit,
) {
  when (action) {
    AlbumDetailTrackAction.Play -> playback.playTrack(track)
    AlbumDetailTrackAction.AddToQueue -> {
      playback.addToQueue(track)
      applicationScope.launch {
        snackbarHostState.showSnackbar("Added to queue: ${track.title}")
      }
    }
    AlbumDetailTrackAction.AddToPlaylist -> onAddToPlaylist(track)
    AlbumDetailTrackAction.GoToAlbum -> {
      val name = track.album ?: return
      backStack.push(AlbumDetail(name, track.albumArtist ?: track.artist))
    }
    AlbumDetailTrackAction.GoToArtist -> {
      val name = track.albumArtist?.takeIf { it.isNotBlank() } ?: track.artist ?: return
      backStack.push(ArtistDetail(name))
    }
    // Phase F.2 — single-track delete from album/artist/genre detail.
    AlbumDetailTrackAction.Delete -> onDeleteTracks(listOf(track))
  }
}
