package com.eight87.tonearmboy.ui.library.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * R.D.4 — multi-select state hoisted out of `TracksListContent`.
 *
 * Pure transition methods make selection independently testable —
 * the per-tab body just calls into the state and renders, no
 * `var selectedIds by remember { mutableStateOf(emptySet()) }`
 * scattered through the composable.
 *
 * Use via [rememberSelectionState]:
 * ```
 * val selection = rememberSelectionState<Long>()
 * if (selection.inSelectionMode) MultiSelectBar(count = selection.size, ...)
 * row(selected = selection.contains(id), onLongClick = { selection.add(id) })
 * ```
 */
class SelectionState<T>(initial: Set<T> = emptySet()) {
  private var ids: Set<T> by mutableStateOf(initial)

  val selected: Set<T> get() = ids
  val size: Int get() = ids.size
  val inSelectionMode: Boolean get() = ids.isNotEmpty()

  fun contains(id: T): Boolean = id in ids
  fun toggle(id: T) { ids = if (id in ids) ids - id else ids + id }
  fun add(id: T) { ids = ids + id }
  fun clear() { ids = emptySet() }
  fun snapshot(): List<T> = ids.toList()
}

/**
 * Compose factory: hold a [SelectionState] across recompositions.
 * Type parameter is the id type (`Long` for library entities).
 */
@Composable
fun <T> rememberSelectionState(): SelectionState<T> = remember { SelectionState() }
