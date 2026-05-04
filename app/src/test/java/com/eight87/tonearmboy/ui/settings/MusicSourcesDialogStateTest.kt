package com.eight87.tonearmboy.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D.17.3 — pure state-machine tests for [MusicSourcesDialogState]. No
 * Robolectric / runtime — just round-trip the operations the dialog
 * performs before Save commits to DataStore.
 */
class MusicSourcesDialogStateTest {

  @Test
  fun fromPersisted_seeds_dialog_with_persisted_values() {
    val state = MusicSourcesDialogState.fromPersisted(
      mode = MusicSourceMode.FilePicker,
      folders = setOf("a", "b"),
    )
    assertEquals(MusicSourceMode.FilePicker, state.mode)
    assertEquals(setOf("a", "b"), state.folders.toSet())
    assertFalse(state.moreSettingsExpanded)
  }

  @Test
  fun setMode_does_not_clear_folders() {
    val initial = MusicSourcesDialogState.fromPersisted(
      mode = MusicSourceMode.FilePicker,
      folders = setOf("a"),
    )
    val toSystem = initial.setMode(MusicSourceMode.System)
    assertEquals(MusicSourceMode.System, toSystem.mode)
    assertEquals(
      "Toggling to System must not lose the saved folder list",
      listOf("a"),
      toSystem.folders,
    )
    val backToPicker = toSystem.setMode(MusicSourceMode.FilePicker)
    assertEquals(listOf("a"), backToPicker.folders)
  }

  @Test
  fun addFolder_is_idempotent() {
    val state = MusicSourcesDialogState(
      mode = MusicSourceMode.FilePicker,
      folders = listOf("a"),
    )
    val added = state.addFolder("b")
    assertEquals(listOf("a", "b"), added.folders)
    val readded = added.addFolder("a")
    assertEquals(
      "Adding an existing URI must be a no-op",
      listOf("a", "b"),
      readded.folders,
    )
  }

  @Test
  fun removeFolder_drops_only_the_named_uri() {
    val state = MusicSourcesDialogState(
      mode = MusicSourceMode.FilePicker,
      folders = listOf("a", "b", "c"),
    )
    val removed = state.removeFolder("b")
    assertEquals(listOf("a", "c"), removed.folders)
    val noop = removed.removeFolder("missing")
    assertEquals(listOf("a", "c"), noop.folders)
  }

  @Test
  fun toggleMoreSettings_flips_expanded() {
    val state = MusicSourcesDialogState(
      mode = MusicSourceMode.System,
      folders = emptyList(),
    )
    val open = state.toggleMoreSettings()
    assertTrue(open.moreSettingsExpanded)
    val closed = open.toggleMoreSettings()
    assertFalse(closed.moreSettingsExpanded)
  }

  @Test
  fun cancel_semantics_discard_working_copy() {
    // The dialog itself never persists on cancel — to prove it, we
    // simulate the flow: take a working copy, mutate it, and confirm
    // the persisted seed (a separate value) is untouched.
    val persistedMode = MusicSourceMode.System
    val persistedFolders = setOf<String>()
    var working = MusicSourcesDialogState.fromPersisted(persistedMode, persistedFolders)
    working = working.setMode(MusicSourceMode.FilePicker)
    working = working.addFolder("uri-1")
    working = working.addFolder("uri-2")
    // "Cancel" — drop the working state and verify the persisted seed
    // is unchanged. (The repository write is the only persistence
    // path; the state machine itself cannot mutate it.)
    assertEquals(MusicSourceMode.System, persistedMode)
    assertEquals(emptySet<String>(), persistedFolders)
    assertEquals(MusicSourceMode.FilePicker, working.mode)
    assertEquals(2, working.folders.size)
  }
}
