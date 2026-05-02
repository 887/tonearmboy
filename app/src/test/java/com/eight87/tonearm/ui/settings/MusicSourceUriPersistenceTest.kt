package com.eight87.tonearm.ui.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.9d.1 — exercise the SAF tree-URI store: add, remove, deduplicate,
 * survive restart. Robolectric runs against the same DataStore the
 * activity uses, so we know the keys + parsing line up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicSourceUriPersistenceTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val repo = SettingsRepository(context)

  @Before
  fun reset() = runTest {
    repo.setMusicSourceUris(emptySet())
    repo.setAutomaticReloading(false)
  }

  @After
  fun tearDown() {
    context.filesDir.resolve("datastore").deleteRecursively()
  }

  @Test
  fun default_is_empty_set() = runTest {
    assertEquals(emptySet<String>(), repo.musicSourceUris.first())
    assertEquals(emptySet<String>(), repo.snapshot.first().musicSourceUris)
  }

  @Test
  fun add_and_remove_round_trip() = runTest {
    val a = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    val b = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2Faudio"

    repo.addMusicSourceUri(a)
    repo.addMusicSourceUri(b)
    assertEquals(setOf(a, b), repo.musicSourceUris.first())

    repo.removeMusicSourceUri(a)
    assertEquals(setOf(b), repo.musicSourceUris.first())

    repo.removeMusicSourceUri(b)
    assertTrue(repo.musicSourceUris.first().isEmpty())
  }

  @Test
  fun add_is_idempotent_dedupes() = runTest {
    val a = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    repo.addMusicSourceUri(a)
    repo.addMusicSourceUri(a)
    repo.addMusicSourceUri(a)
    assertEquals(setOf(a), repo.musicSourceUris.first())
  }

  @Test
  fun snapshot_reflects_current_set() = runTest {
    val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    repo.addMusicSourceUri(uri)
    assertEquals(setOf(uri), repo.snapshot.first().musicSourceUris)
  }

  @Test
  fun setMusicSourceUris_replaces_full_set() = runTest {
    repo.addMusicSourceUri("a")
    repo.addMusicSourceUri("b")
    repo.setMusicSourceUris(setOf("c", "d", "e"))
    assertEquals(setOf("c", "d", "e"), repo.musicSourceUris.first())
  }

  @Test
  fun set_persists_across_repository_recreation() = runTest {
    val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
    repo.addMusicSourceUri(uri)
    // Mimic process restart: a fresh SettingsRepository instance pointed
    // at the same datastore should observe the persisted set.
    val freshRepo = SettingsRepository(context)
    assertEquals(setOf(uri), freshRepo.musicSourceUris.first())
  }

  @Test
  fun automatic_reloading_round_trips_independently() = runTest {
    assertFalse(repo.automaticReloading.first())
    repo.setAutomaticReloading(true)
    assertTrue(repo.automaticReloading.first())
    assertTrue(repo.snapshot.first().automaticReloading)
    repo.setAutomaticReloading(false)
    assertFalse(repo.automaticReloading.first())
  }
}
