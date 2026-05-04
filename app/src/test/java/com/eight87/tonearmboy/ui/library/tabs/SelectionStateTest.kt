package com.eight87.tonearmboy.ui.library.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R.D.4 — pure-logic tests for [SelectionState]'s transition methods.
 * The Compose state holder around `mutableStateOf` is exercised by
 * the Compose UI tests; these JVM-only tests pin the algebra.
 */
class SelectionStateTest {

  @Test
  fun fresh_state_is_empty_and_not_in_selection_mode() {
    val s = SelectionState<Long>()
    assertEquals(0, s.size)
    assertFalse(s.inSelectionMode)
    assertFalse(s.contains(1L))
  }

  @Test
  fun add_enters_selection_mode_and_is_idempotent() {
    val s = SelectionState<Long>()
    s.add(1L)
    assertTrue(s.inSelectionMode)
    assertTrue(s.contains(1L))
    assertEquals(1, s.size)
    s.add(1L)
    assertEquals("add is idempotent on the same id", 1, s.size)
  }

  @Test
  fun toggle_flips_membership() {
    val s = SelectionState<Long>()
    s.toggle(7L)
    assertTrue(s.contains(7L))
    s.toggle(7L)
    assertFalse(s.contains(7L))
    assertFalse(s.inSelectionMode)
  }

  @Test
  fun clear_drops_to_empty_and_exits_selection_mode() {
    val s = SelectionState<Long>()
    s.add(1L)
    s.add(2L)
    s.clear()
    assertEquals(0, s.size)
    assertFalse(s.inSelectionMode)
  }

  @Test
  fun snapshot_returns_current_ids_decoupled_from_state() {
    val s = SelectionState<Long>()
    s.add(1L)
    s.add(2L)
    val snap = s.snapshot()
    s.clear()
    assertEquals("snapshot is captured at call time", setOf(1L, 2L), snap.toSet())
  }

  @Test
  fun selection_works_with_string_id_type() {
    val s = SelectionState<String>()
    s.add("a")
    s.add("b")
    s.toggle("a")
    assertEquals(setOf("b"), s.selected)
  }
}
