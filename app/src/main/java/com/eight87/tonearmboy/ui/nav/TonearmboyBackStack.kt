package com.eight87.tonearmboy.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Single-rooted back stack for tonearmboy.
 *
 * The Auxio reference structure has one root destination ([LibraryRoot])
 * and pushes everything else (search, settings tree, now playing,
 * playlist detail) on top. There are no parallel per-tab stacks because
 * the library tabs are not navigation destinations — they live inside
 * the root composable itself.
 *
 * Each entry is `@Serializable`; the activity-scoped instance lives in a
 * `remember { }` block. Save/restore across process death will be added
 * in a follow-up via `rememberSaveable` + a custom `Saver`.
 */
class TonearmboyBackStack(rootKey: Destination = LibraryRoot) {

  /** Flat back stack — what `NavDisplay` consumes. */
  val backStack: SnapshotStateList<NavKey> = mutableStateListOf<NavKey>(rootKey)

  /** Currently visible destination. */
  val current: NavKey
    get() = backStack.last()

  /** Push any destination onto the stack. */
  fun push(key: NavKey) {
    backStack.add(key)
  }

  /**
   * Pop the current entry. Used as `NavDisplay.onBack`. The root entry
   * cannot be popped — pressing back at the root is a no-op (the system
   * back-press handler will let the activity finish).
   */
  fun pop() {
    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
  }

  /**
   * Pop everything down to (and including) the first occurrence of [key]
   * found from the top, then push it again — i.e. ensure [key] is on
   * top exactly once. If [key] isn't already in the stack, just push.
   */
  fun popToOrPush(key: NavKey) {
    val idx = backStack.indexOfFirst { it == key }
    if (idx >= 0) {
      while (backStack.size > idx + 1) backStack.removeAt(backStack.lastIndex)
    } else {
      push(key)
    }
  }
}
