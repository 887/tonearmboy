package com.eight87.tonearm

import android.app.Application
import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.eight87.tonearm.data.LibraryRepository
import com.eight87.tonearm.playback.PlaybackUiController
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

  val libraryRepository: LibraryRepository by lazy {
    LibraryRepository(applicationContext, externalScope = applicationScope)
  }

  val playbackUiController: PlaybackUiController by lazy {
    PlaybackUiController(applicationContext)
  }

  val themePreferenceStore: ThemePreferenceStore by lazy {
    ThemePreferenceStore(applicationContext)
  }

  val settingsRepository: SettingsRepository by lazy {
    SettingsRepository(applicationContext)
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
