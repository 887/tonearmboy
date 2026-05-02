package com.eight87.tonearm.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for the single-rooted back stack used after the
 * Auxio top-tabs refactor. Library is the only root; everything else
 * (search, settings tree, now playing, playlist detail) is pushed on
 * top.
 */
class TonearmBackStackTest {

  @Test
  fun startsAtRoot() {
    val s = TonearmBackStack()
    assertEquals(LibraryRoot, s.backStack.first())
    assertEquals(1, s.backStack.size)
  }

  @Test
  fun pushAndPop_unwindsToRoot() {
    val s = TonearmBackStack()
    s.push(Search)
    s.push(NowPlaying)
    assertEquals(listOf(LibraryRoot, Search, NowPlaying), s.backStack.toList())
    s.pop()
    assertEquals(listOf(LibraryRoot, Search), s.backStack.toList())
    s.pop()
    assertEquals(listOf(LibraryRoot), s.backStack.toList())
  }

  @Test
  fun pop_atRoot_isNoOp() {
    val s = TonearmBackStack()
    s.pop()
    assertEquals(listOf(LibraryRoot), s.backStack.toList())
  }

  @Test
  fun playlistDetail_pushesAndPops() {
    val s = TonearmBackStack()
    val detail = PlaylistDetail(playlistId = 7L)
    s.push(detail)
    assertEquals(listOf(LibraryRoot, detail), s.backStack.toList())
    s.pop()
    assertEquals(listOf(LibraryRoot), s.backStack.toList())
  }

  @Test
  fun popToOrPush_collapsesToExistingEntry() {
    val s = TonearmBackStack()
    s.push(SettingsRootDest)
    s.push(SettingsLookAndFeel)
    s.popToOrPush(SettingsRootDest)
    assertEquals(listOf(LibraryRoot, SettingsRootDest), s.backStack.toList())
  }

  @Test
  fun popToOrPush_pushesIfMissing() {
    val s = TonearmBackStack()
    s.popToOrPush(Search)
    assertEquals(listOf(LibraryRoot, Search), s.backStack.toList())
  }
}
