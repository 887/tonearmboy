package com.eight87.tonearmboy.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.eight87.tonearmboy.data.watcher.LibraryWatcherService

/**
 * R.E.7 — narrow injectable interfaces over the side-effect launchers
 * the Settings sub-pages call directly today. The Settings composables
 * take these as parameters; production bindings ([DefaultAutoReloadController],
 * [DefaultEqualizerLauncher], [DefaultMusicSourceCommands]) live alongside.
 *
 * Settings-F6 in the audit. Lifts the toggle-to-watcher coupling, the
 * equalizer intent + snackbar fallback, and the SAF tree-URI permission
 * grant out of the composable bodies — each is now contract-tested
 * separately and stubbable for unit tests of the sub-page bindings.
 */

/**
 * Toggling automatic-reloading needs to start / stop the foreground
 * watcher service in the same gesture so the sticky notification
 * appears within ~1 frame of the toggle.
 */
fun interface AutoReloadController {
  fun setEnabled(context: Context, enabled: Boolean)
}

object DefaultAutoReloadController : AutoReloadController {
  override fun setEnabled(context: Context, enabled: Boolean) {
    if (enabled) LibraryWatcherService.start(context)
    else LibraryWatcherService.stop(context)
  }
}

/**
 * Hand-off to the system equalizer for the active audio session.
 * Returns true when the launch succeeded; the caller surfaces a
 * snackbar fallback when it returns false.
 */
fun interface EqualizerLauncher {
  fun launch(context: Context, audioSessionId: Int): Boolean
}

object DefaultEqualizerLauncher : EqualizerLauncher {
  override fun launch(context: Context, audioSessionId: Int): Boolean {
    val intent = SystemEqualizer.buildIntent(audioSessionId, context.packageName)
    if (!SystemEqualizer.resolves(context, intent)) return false
    return runCatching { context.startActivity(intent) }.isSuccess
  }
}

/**
 * Music-sources side effects that the SAF dialog runs after the
 * `OpenDocumentTree` launcher returns. The launcher itself stays
 * inline — `rememberLauncherForActivityResult` is necessarily
 * Composable-bound — but the post-pick permission grant is now
 * injectable.
 */
fun interface MusicSourceCommands {
  fun persistFolderPermission(context: Context, uri: Uri)
}

object DefaultMusicSourceCommands : MusicSourceCommands {
  override fun persistFolderPermission(context: Context, uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
  }
}
