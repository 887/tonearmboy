package com.eight87.tonearm.ui.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.17.3.1 / D.17.3.6 — verify the [MusicSourceMode] persistence path
 * end-to-end: default value, round-trip, and the
 * [SettingsRepository.firstLaunchInitialise] hook (the load-bearing
 * one — it's what stops a fresh install from showing an empty
 * library).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicSourceModeTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  @After
  fun tearDown() {
    context.filesDir.resolve("datastore").deleteRecursively()
  }

  @Test
  fun default_is_system_when_no_value_is_persisted() = runTest {
    val repo = SettingsRepository(context)
    assertEquals(MusicSourceMode.System, repo.musicSourceMode.flow.first())
    assertEquals(MusicSourceMode.System, repo.snapshot.first().musicSourceMode)
  }

  @Test
  fun set_round_trips_through_snapshot_and_flow() = runTest {
    val repo = SettingsRepository(context)
    repo.setMusicSourceMode(MusicSourceMode.FilePicker)
    assertEquals(MusicSourceMode.FilePicker, repo.musicSourceMode.flow.first())
    assertEquals(MusicSourceMode.FilePicker, repo.snapshot.first().musicSourceMode)
    repo.setMusicSourceMode(MusicSourceMode.System)
    assertEquals(MusicSourceMode.System, repo.musicSourceMode.flow.first())
  }

  @Test
  fun firstLaunchInitialise_writes_default_when_unset() = runTest {
    val repo = SettingsRepository(context)
    repo.firstLaunchInitialise()
    assertEquals(MusicSourceMode.System, repo.musicSourceMode.flow.first())
  }

  @Test
  fun firstLaunchInitialise_does_not_overwrite_existing_choice() = runTest {
    val repo = SettingsRepository(context)
    repo.setMusicSourceMode(MusicSourceMode.FilePicker)
    repo.firstLaunchInitialise()
    assertEquals(
      "firstLaunchInitialise must not clobber a previously-set mode",
      MusicSourceMode.FilePicker,
      repo.musicSourceMode.flow.first(),
    )
  }

  @Test
  fun fromStored_unknown_value_falls_back_to_default() {
    assertEquals(MusicSourceMode.System, MusicSourceMode.fromStored(null))
    assertEquals(MusicSourceMode.System, MusicSourceMode.fromStored(""))
    assertEquals(MusicSourceMode.System, MusicSourceMode.fromStored("garbage"))
    assertEquals(MusicSourceMode.System, MusicSourceMode.fromStored("system"))
    assertEquals(MusicSourceMode.System, MusicSourceMode.fromStored("System"))
    assertEquals(MusicSourceMode.FilePicker, MusicSourceMode.fromStored("FilePicker"))
  }
}
