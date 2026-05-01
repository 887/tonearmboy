package com.eight87.tonearm.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the per-tab back stack. The class is only
 * dependent on the Compose snapshot system for state observation; the
 * arithmetic of "where does pop land?" / "is the flat stack a
 * concatenation of the tab stacks?" is independent of the runtime, so
 * we exercise it directly on the JVM.
 */
class TonearmBackStackTest {

  @Test
  fun startsOnInitialTab() {
    val s = TonearmBackStack(Home)
    assertEquals(Home, s.topLevelKey)
    assertEquals(listOf(Home), s.backStack.toList())
    assertTrue(s.isSelected(Home))
  }

  @Test
  fun switchTo_addsTabAndPreservesPriorOrdering() {
    val s = TonearmBackStack(Home)
    s.switchTo(Library)
    assertEquals(Library, s.topLevelKey)
    // Flat stack is the concatenation of [Home, Library].
    assertEquals(listOf(Home, Library), s.backStack.toList())
  }

  @Test
  fun switchTo_existingTab_movesItToTheEnd() {
    val s = TonearmBackStack(Home)
    s.switchTo(Library)
    s.switchTo(Search)
    s.switchTo(Library) // already known, becomes the new "last"
    assertEquals(Library, s.topLevelKey)
    // Order: oldest-first, with Library bumped to the end.
    assertEquals(listOf(Home, Search, Library), s.backStack.toList())
  }

  @Test
  fun push_addsToCurrentTabsStack() {
    val s = TonearmBackStack(Home)
    s.switchTo(Library)
    val detail = PlaylistDetail(playlistId = 7L)
    s.push(detail)
    assertEquals(listOf(Home, Library, detail), s.backStack.toList())
  }

  @Test
  fun pop_unwindsCurrentTabFirst() {
    val s = TonearmBackStack(Home)
    s.switchTo(Library)
    s.push(PlaylistDetail(7L))
    s.pop()
    assertEquals(Library, s.topLevelKey)
    assertEquals(listOf(Home, Library), s.backStack.toList())
  }

  @Test
  fun pop_offTabRoot_dropsThatTab() {
    val s = TonearmBackStack(Home)
    s.switchTo(Library)
    s.pop() // pops Library root → Home becomes active
    assertEquals(Home, s.topLevelKey)
    assertEquals(listOf(Home), s.backStack.toList())
  }
}
