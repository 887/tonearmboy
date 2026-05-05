package com.eight87.tonearmboy.data.delete

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.eight87.tonearmboy.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase F.1 — file-deletion plumbing.
 *
 * Routes a list of MediaStore content URIs through whichever delete
 * path the running Android version permits:
 *
 * - **API 30+ (R / Android 11):**
 *   [MediaStore.createDeleteRequest] is the canonical entry point. It
 *   returns a `PendingIntent` whose `IntentSender` the caller hands to
 *   `ActivityResultContracts.StartIntentSenderForResult`. On
 *   `RESULT_OK` the system has already removed the files. We surface
 *   that intent sender as [DeleteRequest.Consent] for the Compose
 *   layer to launch.
 *
 * - **API 29 (Q / Android 10):**
 *   Calling `contentResolver.delete` directly throws
 *   [RecoverableSecurityException] when the app does not own the file.
 *   The exception's `userAction.actionIntent.intentSender` is the
 *   consent intent we forward. Same [DeleteRequest.Consent] shape so
 *   the UI doesn't care about the API split.
 *
 * - **API 26-28 (O / O_MR1 / P):**
 *   `minSdk = 26`. Pre-Q has no scoped storage, so a plain
 *   `delete(uri)` succeeds for any file the app holds
 *   `READ_EXTERNAL_STORAGE` for. Returns [DeleteRequest.Immediate] with
 *   the URIs the system actually removed.
 *
 * The Compose layer owns the `rememberLauncherForActivityResult` +
 * relays the result back to the repository so the database row gets
 * dropped (see [com.eight87.tonearmboy.data.LibraryRepository.onTracksDeleted]).
 */
class TrackDeleter(private val context: Context) {

  /**
   * Request deletion of [uris]. Empty list → [DeleteRequest.Immediate]
   * with no entries (caller treats as a no-op).
   */
  suspend fun requestDelete(uris: List<Uri>): DeleteRequest = withContext(Dispatchers.IO) {
    if (uris.isEmpty()) return@withContext DeleteRequest.Immediate(emptyList())
    when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> deleteViaCreateRequest(uris)
      Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> deleteViaRecoverableException(uris)
      else -> deleteDirect(uris)
    }
  }

  private fun deleteViaCreateRequest(uris: List<Uri>): DeleteRequest = try {
    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
    DeleteRequest.Consent(pendingIntent.intentSender)
  } catch (t: Throwable) {
    Log.w(TAG, "createDeleteRequest failed", t)
    DeleteRequest.Failure(t.message ?: context.getString(R.string.library_delete_failure_create_request))
  }

  /**
   * On Q the documented pattern is: try a plain `delete()`, catch
   * `RecoverableSecurityException`, hand its IntentSender back to the
   * UI to launch. After the user grants, the UI re-invokes
   * `requestDelete` (or this method) — by that point the resolver call
   * succeeds. We expose the consent flow uniformly via
   * [DeleteRequest.Consent] so the UI never has to special-case Q.
   */
  private fun deleteViaRecoverableException(uris: List<Uri>): DeleteRequest {
    // Q does not have a "delete many" entry point; we surface the first
    // URI's consent intent and let the UI loop the remainder. Most
    // users land on Q ~5% of installs at this point — keep it simple.
    val first = uris.first()
    return try {
      val deleted = context.contentResolver.delete(first, null, null)
      if (deleted > 0) DeleteRequest.Immediate(listOf(first))
      else DeleteRequest.Failure(context.getString(R.string.library_delete_failure_already_gone))
    } catch (e: RecoverableSecurityException) {
      val sender = e.userAction.actionIntent.intentSender
      DeleteRequest.Consent(sender)
    } catch (e: SecurityException) {
      Log.w(TAG, "delete() denied without recoverable action", e)
      DeleteRequest.Failure(context.getString(R.string.library_delete_failure_denied))
    } catch (t: Throwable) {
      Log.w(TAG, "delete() failed", t)
      DeleteRequest.Failure(t.message ?: context.getString(R.string.library_delete_failure_generic))
    }
  }

  private fun deleteDirect(uris: List<Uri>): DeleteRequest {
    val ok = ArrayList<Uri>(uris.size)
    for (uri in uris) {
      try {
        val n = context.contentResolver.delete(uri, null, null)
        if (n > 0) ok += uri
      } catch (e: SecurityException) {
        Log.w(TAG, "direct delete denied for $uri", e)
      } catch (t: Throwable) {
        Log.w(TAG, "direct delete failed for $uri", t)
      }
    }
    return DeleteRequest.Immediate(ok)
  }

  companion object {
    private const val TAG = "tonearmboy-delete"
  }
}

/**
 * Result of [TrackDeleter.requestDelete].
 *
 * The caller decides what to do with each branch:
 * - [Immediate] → invalidate the cache for [deletedUris] and surface a
 *   success snackbar.
 * - [Consent] → launch [intentSender] via the Compose
 *   `StartIntentSenderForResult` launcher; on `RESULT_OK` invalidate
 *   the cache for the URIs originally requested.
 * - [Failure] → surface [reason] in a snackbar; cache untouched.
 */
sealed class DeleteRequest {
  data class Immediate(val deletedUris: List<Uri>) : DeleteRequest()
  data class Consent(val intentSender: IntentSender) : DeleteRequest()
  data class Failure(val reason: String) : DeleteRequest()
}
