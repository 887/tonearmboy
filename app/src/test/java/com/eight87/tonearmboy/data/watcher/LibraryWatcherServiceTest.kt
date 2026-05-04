package com.eight87.tonearmboy.data.watcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.9d.2 — confirm the unique-work + KEEP debounce behaviour.
 *
 * The service registers a [android.database.ContentObserver] which on
 * every change calls [LibraryWatcherService.enqueueDebouncedRescan].
 * That helper enqueues a [LibraryRescanWorker] under the unique name
 * [LibraryRescanWorker.UNIQUE_WORK_NAME] with [ExistingWorkPolicy.KEEP].
 *
 * The contract this test pins: a flurry of "change" events between
 * the first enqueue and the worker's run window collapses into a
 * single queued worker. Without that, a busy directory paste would
 * spawn one rescan per file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryWatcherServiceTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
  }

  @Test
  fun multiple_change_events_coalesce_into_one_queued_worker() {
    val wm = WorkManager.getInstance(context)

    // Emit ten "change" events in quick succession — exactly what a
    // burst of MediaStore notifications during a `cp -r` looks like.
    repeat(10) {
      LibraryWatcherService.enqueueDebouncedRescan(context)
    }

    val infos = wm.getWorkInfosForUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME).get()
    val pending = infos.filter { it.state == WorkInfo.State.ENQUEUED }
    assertEquals(
      "KEEP policy should collapse a burst of enqueues into one worker",
      1,
      pending.size,
    )
  }

  @Test
  fun cancel_unique_work_clears_pending_worker() {
    val wm = WorkManager.getInstance(context)

    LibraryWatcherService.enqueueDebouncedRescan(context)
    assertEquals(
      1,
      wm.getWorkInfosForUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME).get()
        .count { it.state == WorkInfo.State.ENQUEUED },
    )

    // Toggling the setting OFF cancels the in-flight worker — the
    // service does this from `onDestroy`. We mirror that gesture here.
    wm.cancelUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME).result.get()

    val pending = wm.getWorkInfosForUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME).get()
      .filter { it.state == WorkInfo.State.ENQUEUED }
    assertTrue("pending worker should be cancelled", pending.isEmpty())
  }

  @Test
  fun debounce_window_is_30_seconds() {
    // Pin the debounce constant so a future tweak is intentional.
    assertEquals(30L, LibraryRescanWorker.DEBOUNCE_SECONDS)
  }

  @Test
  fun unique_work_name_is_stable() {
    // Pin the name — the manifest doesn't reference it but the service
    // and the cancellation path both do, and they must agree.
    assertEquals("tonearmboy.library.rescan", LibraryRescanWorker.UNIQUE_WORK_NAME)
  }

  @Test
  fun reenqueue_after_cancel_creates_a_new_worker() {
    val wm = WorkManager.getInstance(context)
    LibraryWatcherService.enqueueDebouncedRescan(context)
    wm.cancelUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME).result.get()
    LibraryWatcherService.enqueueDebouncedRescan(context)
    val pending = wm.getWorkInfosForUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME).get()
      .filter { it.state == WorkInfo.State.ENQUEUED }
    assertEquals(1, pending.size)
  }

  @Test
  fun direct_keep_policy_enqueue_matches_helper_behaviour() {
    val wm = WorkManager.getInstance(context)
    val a = OneTimeWorkRequestBuilder<LibraryRescanWorker>()
      .setInitialDelay(LibraryRescanWorker.DEBOUNCE_SECONDS, TimeUnit.SECONDS)
      .build()
    val b = OneTimeWorkRequestBuilder<LibraryRescanWorker>()
      .setInitialDelay(LibraryRescanWorker.DEBOUNCE_SECONDS, TimeUnit.SECONDS)
      .build()
    wm.enqueueUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, a).result.get()
    wm.enqueueUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, b).result.get()
    val pending = wm.getWorkInfosForUniqueWork(LibraryRescanWorker.UNIQUE_WORK_NAME).get()
      .filter { it.state == WorkInfo.State.ENQUEUED }
    assertEquals(1, pending.size)
  }
}
