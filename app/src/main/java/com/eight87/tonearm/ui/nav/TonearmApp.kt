package com.eight87.tonearm.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.material3.Scaffold
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.eight87.tonearm.AppGraph
import com.eight87.tonearm.playback.PlaybackService
import com.eight87.tonearm.ui.library.AlbumDetailScreen
import com.eight87.tonearm.ui.library.AlbumDetailTrackAction
import com.eight87.tonearm.ui.library.ArtistDetailScreen
import com.eight87.tonearm.ui.library.GenreDetailScreen
import com.eight87.tonearm.ui.library.LibraryScreen
import com.eight87.tonearm.ui.library.PlaylistDetailScreen
import com.eight87.tonearm.ui.library.PlaylistPickerSheet
import com.eight87.tonearm.ui.playing.MiniPlayer
import com.eight87.tonearm.ui.playing.NowPlayingScreen
import com.eight87.tonearm.data.model.Track
import com.eight87.tonearm.ui.search.SearchScreen
import com.eight87.tonearm.ui.settings.SettingsSnapshot
import com.eight87.tonearm.ui.settings.SettingsAudioScreen
import com.eight87.tonearm.ui.settings.SettingsContentScreen
import com.eight87.tonearm.ui.settings.AboutScreen
import com.eight87.tonearm.ui.settings.SettingsLookAndFeelScreen
import com.eight87.tonearm.ui.settings.MusicSourcesDialog
import com.eight87.tonearm.ui.settings.SettingsPersonalizeScreen
import com.eight87.tonearm.ui.settings.SettingsScreen
import com.eight87.tonearm.ui.settings.catalog.LocalHighlightedSettingId
import com.eight87.tonearm.ui.settings.catalog.SettingsSearchScreen
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
fun TonearmApp(
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
  val backStack = remember { TonearmBackStack(LibraryRoot) }
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
  val settingsSnapshot by graph.settingsRepository.snapshot
    .collectAsStateWithLifecycle(initialValue = SettingsSnapshot.Default)
  val snackbarHostState = remember { SnackbarHostState() }

  // Phase F — file-deletion entry. Hosting the launcher at the app
  // root means the consent dialog can return after a screen pop.
  val trackDeleter = remember { com.eight87.tonearm.data.delete.TrackDeleter(graph.applicationContextForUi) }
  val onDeleteTracks = com.eight87.tonearm.ui.library.rememberDeleteFlow(
    repository = graph.libraryRepository,
    playback = playback,
    snackbarHostState = snackbarHostState,
    applicationScope = graph.applicationScope,
    trackDeleter = trackDeleter,
  )

  // Single shared title cell driven by per-screen LaunchedEffects.
  val sectionTitle = remember { mutableStateOf("tonearm") }

  // Settings search seeds this state with the id of the row to flash
  // when navigating to its destination sub-page; the receiving row
  // animates a 300 ms background colour change and then clears it.
  val highlightedSettingId = remember { mutableStateOf<String?>(null) }

  // Keep the MediaController bound for the lifetime of the activity.
  // The full-screen NowPlaying re-uses this same connection.
  LaunchedEffect(Unit) {
    // D.9b.1 — give the controller a library handle for ReplayGain
    // album lookups before it sees the first track transition.
    playback.setLibrary(graph.libraryRepository)
    playback.connect()
  }

  // D.9a.3 — keep the playback controller's pause-on-repeat flag in
  // sync with the user's setting.
  LaunchedEffect(settingsSnapshot.pauseOnRepeat) {
    playback.setPauseOnRepeat(settingsSnapshot.pauseOnRepeat)
  }

  // D.9b.1 / D.9b.2 — push ReplayGain strategy + pre-amp into the
  // controller. The controller re-applies the volume immediately and
  // on every subsequent track transition.
  LaunchedEffect(settingsSnapshot.replayGainStrategy, settingsSnapshot.replayGainPreampDb) {
    playback.setReplayGain(
      settingsSnapshot.replayGainStrategy,
      settingsSnapshot.replayGainPreampDb,
    )
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

  // D.17.3 — Music sources dialog visibility. Lifted here so the
  // dialog can be opened from the Settings root row (OpenDialog
  // catalog kind) without pushing a new destination onto the stack.
  var showMusicSourcesDialog by remember { mutableStateOf(false) }

  // Phase H.5 — playlist export/import state.
  var pendingExportEnvelope by remember {
    mutableStateOf<com.eight87.tonearm.data.playlist.PlaylistBackupEnvelope?>(null)
  }
  var pendingImport by remember {
    mutableStateOf<PlaylistImportPending?>(null)
  }
  val context = androidx.compose.ui.platform.LocalContext.current
  val createDocLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
      "application/json",
    ),
  ) { uri: android.net.Uri? ->
    val envelope = pendingExportEnvelope
    pendingExportEnvelope = null
    if (uri == null || envelope == null) return@rememberLauncherForActivityResult
    graph.applicationScope.launch {
      runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(
              com.eight87.tonearm.data.playlist.PlaylistBackupCodec
                .encode(envelope).toByteArray(Charsets.UTF_8),
            )
          }
        }
      }.fold(
        onSuccess = {
          snackbarHostState.showSnackbar("Exported ${envelope.playlists.size} playlists")
        },
        onFailure = {
          snackbarHostState.showSnackbar("Export failed: ${it.message ?: "unknown error"}")
        },
      )
    }
  }
  val openDocLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
  ) { uri: android.net.Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    graph.applicationScope.launch {
      runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          val raw = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader(Charsets.UTF_8).readText()
          } ?: error("Could not read selected file")
          val envelope = com.eight87.tonearm.data.playlist.PlaylistBackupCodec.decode(raw)
          val tracks = graph.libraryRepository.observeTracks().first()
          val resolution = com.eight87.tonearm.data.playlist
            .resolvePlaylistImport(envelope, tracks)
          val existing = graph.libraryRepository.observePlaylists().first()
          val existingNames = existing.map { it.name }.toSet()
          val collisions = resolution.resolved.keys.filter { it in existingNames }
          envelope to resolution to collisions
        }
      }.fold(
        onSuccess = { (pair, collisions) ->
          val (env, resolution) = pair
          if (collisions.isEmpty()) {
            // No collisions, apply with Merge as the default policy
            // (irrelevant since none collide, but keeps the path simple).
            val summary = com.eight87.tonearm.data.playlist.applyPlaylistImport(
              repository = graph.libraryRepository,
              envelope = env,
              resolution = resolution,
              collisionPolicy = com.eight87.tonearm.data.playlist
                .PlaylistImportCollisionPolicy.Merge,
            )
            snackbarHostState.showSnackbar(
              importSummaryMessage(summary),
            )
          } else {
            pendingImport = PlaylistImportPending(env, resolution, collisions)
          }
        },
        onFailure = {
          snackbarHostState.showSnackbar("Import failed: ${it.message ?: "unknown error"}")
        },
      )
    }
  }
  val onExportPlaylists: () -> Unit = {
    graph.applicationScope.launch {
      val envelope = com.eight87.tonearm.data.playlist
        .buildPlaylistBackup(graph.libraryRepository)
      pendingExportEnvelope = envelope
      createDocLauncher.launch(
        com.eight87.tonearm.data.playlist.PlaylistBackupCodec.defaultFileName(),
      )
    }
  }
  val onImportPlaylists: () -> Unit = {
    openDocLauncher.launch(arrayOf("application/json"))
  }
  val playlists by graph.libraryRepository.observePlaylists()
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val onRefreshMusic: () -> Unit = {
    graph.applicationScope.launch {
      graph.libraryRepository.rescanNow()
      snackbarHostState.showSnackbar("Refreshed music library")
    }
  }
  val onRescanMusic: () -> Unit = {
    graph.applicationScope.launch {
      graph.libraryRepository.rescanNow()
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
            playback.performCustomBarAction(settingsSnapshot.customBarAction)
          },
          onToggleShuffle = playback::toggleShuffle,
          onCycleRepeat = playback::cycleRepeatMode,
          onSeekTo = playback::seekTo,
          albumCoversMode = settingsSnapshot.albumCoversMode,
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
            repository = graph.libraryRepository,
            settingsRepository = graph.settingsRepository,
            onTrackClick = { tracks, index ->
              // D.9a.4: queue depends on the user's "When playing from
              // the library" choice. Surrounding list is whatever the
              // tab gave us; we treat that as both `surroundingList`
              // and `allSongs` since the library Songs tab IS all songs.
              playback.playFromLibrary(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = settingsSnapshot.playFromLibrary,
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
            onRenamePlaylist = { id, name ->
              graph.applicationScope.launch {
                graph.libraryRepository.renamePlaylist(id, name)
                snackbarHostState.showSnackbar("Renamed to \"$name\"")
              }
            },
            onDeletePlaylist = { id ->
              graph.applicationScope.launch {
                graph.libraryRepository.deletePlaylist(id)
                snackbarHostState.showSnackbar("Playlist deleted")
              }
            },
            onDeleteTracks = onDeleteTracks,
          )
        }
        entry<AlbumDetail> { key ->
          AlbumDetailScreen(
            repository = graph.libraryRepository,
            albumName = key.name,
            albumArtist = key.albumArtist,
            albumCoversMode = settingsSnapshot.albumCoversMode,
            onTrackClick = { tracks, index ->
              playback.playFromDetail(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = settingsSnapshot.playFromItemDetails,
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
            repository = graph.libraryRepository,
            artistName = key.name,
            albumCoversMode = settingsSnapshot.albumCoversMode,
            onTrackClick = { tracks, index ->
              playback.playFromDetail(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = settingsSnapshot.playFromItemDetails,
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
            repository = graph.libraryRepository,
            genreName = key.name,
            onTrackClick = { tracks, index ->
              playback.playFromDetail(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = settingsSnapshot.playFromItemDetails,
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
            repository = graph.libraryRepository,
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
            playback = playback,
            onBack = { backStack.pop() },
            albumCoversMode = settingsSnapshot.albumCoversMode,
          )
        }
        entry<PlaylistDetail> { key ->
          PlaylistDetailScreen(
            repository = graph.libraryRepository,
            playlistId = key.playlistId,
            onTrackClick = { tracks, index ->
              // D.9a.5: tapped from a detail surface (playlist).
              playback.playFromDetail(
                surroundingList = tracks,
                tappedIndex = index,
                strategy = settingsSnapshot.playFromItemDetails,
              )
              backStack.push(NowPlaying)
            },
            onBack = { backStack.pop() },
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
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsPersonalize> {
          LaunchedEffect(Unit) { sectionTitle.value = "Personalize" }
          SettingsPersonalizeScreen(
            repository = graph.settingsRepository,
            libraryRepository = graph.libraryRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
          )
        }
        entry<SettingsContent> {
          LaunchedEffect(Unit) { sectionTitle.value = "Content" }
          SettingsContentScreen(
            repository = graph.settingsRepository,
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
            repository = graph.settingsRepository,
            onBack = { backStack.pop() },
            onComingSoon = onComingSoon,
            snackbarHostState = snackbarHostState,
            playback = playback,
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
            graph.libraryRepository.addTrackToPlaylist(p.id, track.id)
            snackbarHostState.showSnackbar("Added \"${track.title}\" to ${p.name}")
          }
        },
        onCreateAndPick = { name ->
          addingToPlaylistTrack = null
          graph.applicationScope.launch {
            val id = graph.libraryRepository.createPlaylist(name)
            graph.libraryRepository.addTrackToPlaylist(id, track.id)
            snackbarHostState.showSnackbar("Added \"${track.title}\" to $name")
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
        library = graph.libraryRepository,
        onDismiss = { showMusicSourcesDialog = false },
      )
    }

    // Phase H.5 — playlist-import name-collision resolver. Surfaces
    // when the imported envelope contains playlists whose names already
    // exist in the library. The user picks Overwrite / Merge / Cancel
    // and we apply the import on the chosen policy.
    pendingImport?.let { pending ->
      com.eight87.tonearm.ui.settings.PlaylistImportCollisionDialog(
        collidingNames = pending.collisions,
        onPick = { policy ->
          val captured = pending
          pendingImport = null
          graph.applicationScope.launch {
            val summary = com.eight87.tonearm.data.playlist.applyPlaylistImport(
              repository = graph.libraryRepository,
              envelope = captured.envelope,
              resolution = captured.resolution,
              collisionPolicy = policy,
            )
            snackbarHostState.showSnackbar(importSummaryMessage(summary))
          }
        },
        onDismiss = { pendingImport = null },
      )
    }
  }
  }
}

/**
 * Phase H.5 — pending state passed to the name-collision dialog.
 */
private data class PlaylistImportPending(
  val envelope: com.eight87.tonearm.data.playlist.PlaylistBackupEnvelope,
  val resolution: com.eight87.tonearm.data.playlist.PlaylistImportResolution,
  val collisions: List<String>,
)

/** Phase H.5 — render the import summary string for the snackbar. */
private fun importSummaryMessage(
  summary: com.eight87.tonearm.data.playlist.PlaylistImportSummary,
): String {
  val parts = mutableListOf<String>()
  parts += "Imported ${summary.importedCount} playlists"
  if (summary.skippedCount > 0) parts += "${summary.skippedCount} skipped"
  if (summary.unmatchedTrackCount > 0) {
    parts += "${summary.unmatchedTrackCount} tracks not found"
  }
  return parts.joinToString(", ")
}

@OptIn(UnstableApi::class)
private fun handleDetailTrackAction(
  track: Track,
  action: AlbumDetailTrackAction,
  playback: com.eight87.tonearm.playback.PlaybackUiController,
  backStack: TonearmBackStack,
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
