package com.eight87.tonearm.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Per-tab back stack manager for Navigation 3, lifted from the official
 * `navigation-3` skill's "Common UI" recipe and adapted to tonearm's
 * top-level destinations.
 *
 * Each [TopLevelDestination] gets its own private stack; switching tabs
 * preserves the back history of the previously visible tab. The single
 * flat [backStack] exposed to `NavDisplay` is the concatenation of the
 * known per-tab stacks in their order of recent use, so back-navigation
 * unwinds the active tab first and then falls back through previously
 * visited tabs (which is what the recipe documents as the expected
 * behavior).
 *
 * This class is intentionally NOT serializable on its own — the
 * activity-scoped instance lives in a `remember { }` block. Each
 * individual [NavKey] in the stacks is `@Serializable` (see
 * [Destination]), so when we move to a saveable variant in a future
 * pass we can lean on `rememberSaveable` + a custom `Saver`.
 */
class TonearmBackStack(startKey: TopLevelDestination) {

  private val topLevelStacks: LinkedHashMap<TopLevelDestination, SnapshotStateList<NavKey>> =
    linkedMapOf(startKey to mutableStateListOf<NavKey>(startKey))

  /** Currently visible top-level tab. */
  var topLevelKey: TopLevelDestination by mutableStateOf(startKey)
    private set

  /** Flat back stack — what `NavDisplay` consumes. */
  val backStack: SnapshotStateList<NavKey> = mutableStateListOf<NavKey>(startKey)

  private fun rebuildFlat() {
    backStack.apply {
      clear()
      topLevelStacks.values.forEach { addAll(it) }
    }
  }

  /**
   * Switch to a top-level tab. If the tab has no history yet it gets a
   * fresh stack rooted at itself; otherwise the existing stack is moved
   * to the end of the tab order so back-navigation lands on it last.
   */
  fun switchTo(tab: TopLevelDestination) {
    val existing = topLevelStacks.remove(tab)
    if (existing != null) {
      topLevelStacks[tab] = existing
    } else {
      topLevelStacks[tab] = mutableStateListOf<NavKey>(tab)
    }
    topLevelKey = tab
    rebuildFlat()
  }

  /** Push a detail key onto the active tab's stack. */
  fun push(key: NavKey) {
    topLevelStacks[topLevelKey]?.add(key)
    rebuildFlat()
  }

  /** Pop the current entry. Used as `NavDisplay.onBack`. */
  fun pop() {
    val stack = topLevelStacks[topLevelKey] ?: return
    val removed = stack.removeLastOrNull()
    // If we popped the tab root key itself, drop the whole stack so
    // the previous tab is now active.
    if (removed === topLevelKey) {
      topLevelStacks.remove(topLevelKey)
      topLevelKey = topLevelStacks.keys.lastOrNull() ?: topLevelKey
    }
    rebuildFlat()
  }

  /** Read-only view of "is this tab currently selected". */
  fun isSelected(tab: TopLevelDestination): Boolean = tab == topLevelKey
}
