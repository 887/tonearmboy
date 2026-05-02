package com.eight87.tonearm.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import com.eight87.tonearm.data.db.CustomTabContentType
import com.eight87.tonearm.data.db.CustomTabEntity
import kotlin.math.roundToInt

/**
 * D.18.3 + D.18.4 — Library tabs configuration dialog.
 *
 * Renders the five built-in tabs at the top (toggle for visibility,
 * drag handle for reorder), then any custom tabs (toggle, drag
 * handle, pencil, trash), and finally a "+ Add custom tab" row.
 *
 * Drag-and-drop is hand-rolled via `detectDragGesturesAfterLongPress`
 * on the drag-handle leading icon — long-press lifts the row, the
 * pointer's vertical translate maps to a "live" target index, and on
 * release we fire either [onReorderBuiltIns] or [onReorderCustomTabs]
 * depending on which list the dragged item belongs to. Built-ins and
 * custom tabs are reordered as separate lists; mixing the two would
 * break the rail's "built-ins first, customs after" contract.
 *
 * The implementation keeps drag state local and computes the visual
 * offset from a single anchor row index — this is the pattern Auxio
 * uses and matches the canonical compose drag-reorder snippet
 * documented in the Android knowledge base for LazyColumn-free lists
 * (the row count here is small enough that we don't need lazy
 * virtualization).
 */
data class LibraryTabsDialogModel(
  val builtIns: List<LibraryTab>,
  val visibleSet: Set<LibraryTab>,
  val customTabs: List<CustomTabEntity>,
)

@Composable
fun LibraryTabsDialog(
  model: LibraryTabsDialogModel,
  onDismiss: () -> Unit,
  onSetBuiltInVisibility: (LibraryTab, Boolean) -> Unit,
  onReorderBuiltIns: (List<LibraryTab>) -> Unit,
  onReorderCustomTabs: (List<Long>) -> Unit,
  onAddCustomTab: () -> Unit,
  onEditCustomTab: (CustomTabEntity) -> Unit,
  onDeleteCustomTab: (CustomTabEntity) -> Unit,
) {
  // Build the canonical built-in ordering: anything currently in the
  // user's order first, then any missing built-ins appended at the
  // end. Same shape the previous editor used.
  val orderedBuiltIns: List<LibraryTab> = remember(model.builtIns) {
    val seen = model.builtIns.toMutableSet()
    val ordered = model.builtIns.toMutableList()
    LibraryTab.entries.forEach { if (it !in seen) { ordered += it; seen += it } }
    ordered.toList()
  }
  // Local mutable copies so drags can rearrange optimistically without
  // ping-ponging through the parent state.
  var builtInOrder by remember(orderedBuiltIns) { mutableStateOf(orderedBuiltIns) }
  var customOrder by remember(model.customTabs) { mutableStateOf(model.customTabs) }
  var pendingDeletion by remember { mutableStateOf<CustomTabEntity?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Library tabs") },
    text = {
      Column(modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 520.dp)
        .semantics { testTag = "library_tabs_editor" }) {
        Text("Built-in tabs", style = MaterialTheme.typography.titleSmall)
        DragReorderColumn(
          items = builtInOrder,
          itemKey = { it.name },
          rowHeightDp = ROW_HEIGHT_DP,
          testTagPrefix = "builtin",
          onReordered = { newOrder ->
            builtInOrder = newOrder
            // Persistence: the contract is that `libraryTabs`
            // captures the visible-and-ordered set. We pass the
            // visible subset to the parent in the *new* order.
            onReorderBuiltIns(newOrder.filter { it in model.visibleSet })
          },
        ) { tab, dragHandleModifier ->
          BuiltInRow(
            tab = tab,
            visible = tab in model.visibleSet,
            onToggle = { v -> onSetBuiltInVisibility(tab, v) },
            dragHandleModifier = dragHandleModifier,
          )
        }

        Spacer(Modifier.height(12.dp))
        Text("Custom tabs", style = MaterialTheme.typography.titleSmall)
        if (customOrder.isEmpty()) {
          Text(
            "No custom tabs yet.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
          )
        } else {
          DragReorderColumn(
            items = customOrder,
            itemKey = { it.id.toString() },
            rowHeightDp = ROW_HEIGHT_DP,
            testTagPrefix = "custom",
            onReordered = { newOrder ->
              customOrder = newOrder
              onReorderCustomTabs(newOrder.map { it.id })
            },
          ) { tab, dragHandleModifier ->
            CustomTabRow(
              tab = tab,
              onEdit = { onEditCustomTab(tab) },
              onDelete = { pendingDeletion = tab },
              dragHandleModifier = dragHandleModifier,
            )
          }
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_DP.dp)
            .semantics { testTag = "add_custom_tab" },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onAddCustomTab) {
            Icon(Icons.Filled.Add, contentDescription = "Add custom tab")
          }
          Text(
            "Add custom tab",
            modifier = Modifier
              .padding(start = 4.dp)
              .weight(1f),
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )

  pendingDeletion?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingDeletion = null },
      title = { Text("Delete custom tab") },
      text = { Text("Delete \"${target.name}\"? This cannot be undone.") },
      confirmButton = {
        TextButton(onClick = {
          onDeleteCustomTab(target)
          pendingDeletion = null
        }) { Text("Delete") }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeletion = null }) { Text("Cancel") }
      },
    )
  }
}

private const val ROW_HEIGHT_DP = 56

@Composable
private fun BuiltInRow(
  tab: LibraryTab,
  visible: Boolean,
  onToggle: (Boolean) -> Unit,
  dragHandleModifier: Modifier,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(ROW_HEIGHT_DP.dp)
      .padding(horizontal = 4.dp)
      .semantics { testTag = "tab_row_${tab.name}" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Filled.DragHandle,
      contentDescription = "Drag to reorder",
      modifier = dragHandleModifier
        .padding(8.dp)
        .semantics { testTag = "drag_handle_${tab.name}" },
    )
    Text(tab.name, modifier = Modifier.weight(1f).padding(start = 4.dp))
    Switch(checked = visible, onCheckedChange = onToggle)
  }
}

@Composable
private fun CustomTabRow(
  tab: CustomTabEntity,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  dragHandleModifier: Modifier,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(ROW_HEIGHT_DP.dp)
      .padding(horizontal = 4.dp)
      .semantics { testTag = "custom_tab_row_${tab.id}" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Filled.DragHandle,
      contentDescription = "Drag to reorder",
      modifier = dragHandleModifier
        .padding(8.dp)
        .semantics { testTag = "drag_handle_custom_${tab.id}" },
    )
    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
      Text(tab.name, style = MaterialTheme.typography.bodyLarge)
      Text(
        contentTypeShortLabel(tab.contentType),
        style = MaterialTheme.typography.bodySmall,
      )
    }
    IconButton(
      onClick = onEdit,
      modifier = Modifier.semantics { testTag = "edit_custom_${tab.id}" },
    ) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
    IconButton(
      onClick = onDelete,
      modifier = Modifier.semantics { testTag = "delete_custom_${tab.id}" },
    ) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
  }
}

private fun contentTypeShortLabel(ct: CustomTabContentType): String = when (ct) {
  CustomTabContentType.SONGS -> "Songs"
  CustomTabContentType.ALBUMS -> "Albums"
  CustomTabContentType.ARTISTS -> "Artists"
  CustomTabContentType.GENRES -> "Genres"
}

/**
 * D.18.4 — small hand-rolled drag-and-drop column. Long-press on the
 * exposed `dragHandleModifier` lifts a row; subsequent vertical
 * translation re-orders the list in place; release commits via
 * [onReordered].
 *
 * Vertical-only drag, fixed row height. Computes the target index
 * by snapping the drag delta to whole row-heights. Items animate to
 * their new position via offset modifiers; we don't use a lazy list
 * here because the dialog's row count is bounded (5 built-ins + a
 * realistic upper bound of maybe a dozen custom tabs).
 *
 * The pattern is the same one documented in `android-skills` under
 * "compose-drag-and-drop" — the third-party library
 * `sh.calvin.reorderable` would be functionally equivalent but adds a
 * dependency for ~150 lines of code we can write inline.
 */
@Composable
private fun <T : Any> DragReorderColumn(
  items: List<T>,
  itemKey: (T) -> String,
  rowHeightDp: Int,
  testTagPrefix: String,
  onReordered: (List<T>) -> Unit,
  rowContent: @Composable (T, Modifier) -> Unit,
) {
  val density = LocalDensity.current
  val rowPx = with(density) { rowHeightDp.dp.toPx() }
  // Local working copy so drag updates animate before the parent
  // notifies. We sync to the supplied [items] when the parent
  // pushes a new list.
  var working by remember(items) { mutableStateOf(items) }
  LaunchedEffect(items) { working = items }

  var draggingIndex by remember { mutableStateOf(-1) }
  var dragOffsetPx by remember { mutableStateOf(0f) }

  Column(modifier = Modifier
    .fillMaxWidth()
    .semantics { testTag = "${testTagPrefix}_drag_column" }) {
    working.forEachIndexed { index, item ->
      val isDragging = index == draggingIndex
      val translateY = if (isDragging) dragOffsetPx else 0f
      val key = itemKey(item)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(rowHeightDp.dp)
          .zIndex(if (isDragging) 1f else 0f)
          .offset { IntOffset(0, translateY.roundToInt()) }
      ) {
        val handleModifier = Modifier.pointerInput(key, working.size) {
          detectDragGesturesAfterLongPress(
            onDragStart = {
              draggingIndex = index
              dragOffsetPx = 0f
            },
            onDrag = { _, drag ->
              dragOffsetPx += drag.y
              // Snap to a target index if the drag passes the
              // threshold of half a row.
              val current = draggingIndex
              if (current >= 0) {
                val targetDelta = (dragOffsetPx / rowPx).roundToInt()
                val target = (current + targetDelta).coerceIn(0, working.size - 1)
                if (target != current) {
                  val swapped = working.toMutableList()
                  val moved = swapped.removeAt(current)
                  swapped.add(target, moved)
                  working = swapped
                  draggingIndex = target
                  // Normalize the running offset so the visual
                  // doesn't jump after the swap.
                  dragOffsetPx -= (target - current) * rowPx
                }
              }
            },
            onDragEnd = {
              draggingIndex = -1
              dragOffsetPx = 0f
              if (working != items) onReordered(working)
            },
            onDragCancel = {
              draggingIndex = -1
              dragOffsetPx = 0f
              working = items
            },
          )
        }
        rowContent(item, handleModifier)
      }
    }
  }
}
