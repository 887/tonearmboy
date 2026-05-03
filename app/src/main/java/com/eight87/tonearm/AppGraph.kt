package com.eight87.tonearm

import android.app.Application
import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.data.AlbumSource
import com.eight87.tonearm.data.ArtistSource
import com.eight87.tonearm.data.CustomTabStore
import com.eight87.tonearm.data.GenreSource
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.data.LibraryScanner
import com.eight87.tonearm.data.MediaChangeSource
import com.eight87.tonearm.data.PlaylistStore
import com.eight87.tonearm.data.TrackSource
import com.eight87.tonearm.playback.PlaybackUiController
import com.eight87.tonearm.theme.AlbumPaletteSource
import com.eight87.tonearm.ui.settings.SettingsRepository
import com.eight87.tonearm.ui.settings.ThemePreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph. Per the project convention there is **no DI
 * framework in v1** — dependencies are passed as constructor params, and
 * a single process-scoped [AppGraph] holds the long-lived singletons
 * the UI tree needs.
 *
 * Lazily initialised on first access from the activity.
 */
@UnstableApi
class AppGraph(private val applicationContext: Context) {

  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  /**
   * Phase F — UI surfaces that build a [com.eight87.tonearm.data.delete.TrackDeleter]
   * need the long-lived application context (the only context safe to
   * keep across launcher rebinds). Other places in the codebase still
   * pass concrete services into constructor params; this one accessor
   * keeps the pattern minimal.
   */
  val applicationContextForUi: Context get() = applicationContext

  /**
   * R.A.3 — concrete library repo. Exposed `@Deprecated` so remaining
   * direct uses surface as compile warnings — every UI consumer should
   * take one of the narrow interfaces below ([tracks], [albums],
   * [artists], [genres], [playlists], [customTabs], [scanner],
   * [mediaChanges]).
   */
  @Deprecated(
    "Take a narrow interface (TrackSource / AlbumSource / etc.) instead.",
    ReplaceWith("tracks / albums / artists / genres / playlists / customTabs / scanner"),
  )
  val libraryRepository: LibraryRepository by lazy {
    LibraryRepository(
      applicationContext,
      externalScope = applicationScope,
      scanConfig = settingsRepository,
    )
  }

  // R.A.3 — narrow interface aliases. UI screens take one of these
  // instead of the whole repository so a change to (say) playlist CRUD
  // can't ripple into the tab renderers (ISP).
  @Suppress("DEPRECATION")
  val tracks: TrackSource get() = libraryRepository
  @Suppress("DEPRECATION")
  val albums: AlbumSource get() = libraryRepository
  @Suppress("DEPRECATION")
  val artists: ArtistSource get() = libraryRepository
  @Suppress("DEPRECATION")
  val genres: GenreSource get() = libraryRepository
  @Suppress("DEPRECATION")
  val playlists: PlaylistStore get() = libraryRepository
  @Suppress("DEPRECATION")
  val customTabs: CustomTabStore get() = libraryRepository
  @Suppress("DEPRECATION")
  val scanner: LibraryScanner get() = libraryRepository
  @Suppress("DEPRECATION")
  val mediaChanges: MediaChangeSource get() = libraryRepository

  val playbackUiController: PlaybackUiController by lazy {
    PlaybackUiController(applicationContext)
  }

  val themePreferenceStore: ThemePreferenceStore by lazy {
    ThemePreferenceStore(applicationContext)
  }

  val settingsRepository: SettingsRepository by lazy {
    SettingsRepository(applicationContext)
  }

  /**
   * D.20.4 — process-scoped palette source. Activity feeds it the
   * playing track's MediaStore album id and the result drives
   * `LocalAlbumPalette` for the chrome.
   */
  val albumPaletteSource: AlbumPaletteSource by lazy {
    AlbumPaletteSource(applicationContext)
  }

  companion object {
    @Volatile
    private var instance: AppGraph? = null

    fun get(context: Context): AppGraph {
      val existing = instance
      if (existing != null) return existing
      synchronized(this) {
        val current = instance
        if (current != null) return current
        val created = AppGraph(context.applicationContext)
        instance = created
        return created
      }
    }
  }
}

/** Convenience for screens that already have an [Application] handle. */
val Application.appGraph: AppGraph
  @UnstableApi
  get() = AppGraph.get(this)
