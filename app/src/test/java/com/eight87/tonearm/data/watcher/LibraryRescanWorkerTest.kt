package com.eight87.tonearm.data.watcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.9d.2 — confirm the worker runs to completion. The actual rescan
 * goes through `LibraryRepository.rescanNow`, which on a clean
 * Robolectric instance simply finds no audio (no permission, no
 * fixtures) and writes the empty cache — a `Result.success` outcome.
 *
 * The point of this test is not to assert specific track counts but
 * to pin: the worker class is wired correctly, the suspending
 * `doWork` resolves, and we get a `Result.success`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryRescanWorkerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun worker_completes_successfully() = runTest {
    val worker = TestListenableWorkerBuilder<LibraryRescanWorker>(context).build()
    val result = worker.doWork()
    assertEquals(ListenableWorker.Result.success(), result)
  }
}
