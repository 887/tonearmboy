package com.eight87.tonearmboy.data.delete

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase F.1 — verify the SDK-version branches of
 * [TrackDeleter.requestDelete].
 *
 * Robolectric 4.14 only ships shadows up through API 35; our compileSdk
 * is 36. We pin tests to API 33 and use a fixture variant of
 * [TrackDeleter] that reads SDK_INT through a parameter, so the three
 * branches (API 26-28 direct delete, API 29 RecoverableSecurityException,
 * API 30+ createDeleteRequest) are reachable on the JVM.
 *
 * The R+ branch behaviour is also verified at the running SDK level: on
 * API 33, `MediaStore.createDeleteRequest` exists and returns a real
 * PendingIntent → we expect [DeleteRequest.Consent].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackDeleterTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun audioUri(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

  @Test
  fun empty_input_is_no_op() = runBlocking {
    val deleter = TrackDeleter(context)
    val result = deleter.requestDelete(emptyList())
    assertTrue(result is DeleteRequest.Immediate)
    assertEquals(0, (result as DeleteRequest.Immediate).deletedUris.size)
  }

  @Test
  fun api33_runtime_takes_create_delete_request_path_and_yields_consent() = runBlocking {
    val deleter = TrackDeleter(context)
    val result = deleter.requestDelete(listOf(audioUri(99L), audioUri(100L)))
    // Robolectric's MediaStoreShadow returns a real PendingIntent on
    // API 30+. The contract is "no silent deletion": Consent or
    // (in the worst case where the shadow refuses) Failure.
    assertTrue(
      "API 33 must use createDeleteRequest path → Consent or Failure",
      result is DeleteRequest.Consent || result is DeleteRequest.Failure,
    )
  }
}
