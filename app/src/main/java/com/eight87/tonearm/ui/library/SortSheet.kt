package com.eight87.tonearm.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.ui.settings.SortDirection
import com.eight87.tonearm.ui.settings.SortKey
import com.eight87.tonearm.ui.settings.TabSort

/**
 * "Sort by" ModalBottomSheet, mirroring Auxio. Local pending state
 * commits to the caller only on OK — Cancel discards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
  current: TabSort,
  onDismiss: () -> Unit,
  onConfirm: (TabSort) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var pendingKey by rememberSaveable { mutableStateOf(current.key) }
  var pendingDir by rememberSaveable { mutableStateOf(current.direction) }

  // Reset pending if the externally-supplied current changed since we
  // were opened (e.g. another tab switched in mid-flight).
  remember(current) {
    pendingKey = current.key
    pendingDir = current.direction
    Unit
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "sort_sheet" },
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
      Text("Sort by", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))

      SortKey.entries.forEach { option ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .selectable(
              selected = pendingKey == option,
              onClick = { pendingKey = option },
            )
            .padding(vertical = 6.dp)
            .semantics { testTag = "sort_key_${option.name}" },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = pendingKey == option, onClick = null)
          Text(text = sortKeyLabel(option), modifier = Modifier.padding(start = 12.dp))
        }
      }

      val directions = SortDirection.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        directions.forEachIndexed { index, dir ->
          SegmentedButton(
            selected = pendingDir == dir,
            onClick = { pendingDir = dir },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = directions.size),
            modifier = Modifier.semantics { testTag = "sort_dir_${dir.name}" },
          ) { Text(if (dir == SortDirection.Ascending) "Ascending" else "Descending") }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        TextButton(onClick = { onConfirm(TabSort(pendingKey, pendingDir)) }) { Text("OK") }
      }
    }
  }
}

private fun sortKeyLabel(k: SortKey): String = when (k) {
  SortKey.Name -> "Name"
  SortKey.Artist -> "Artist"
  SortKey.Album -> "Album"
  SortKey.Date -> "Date"
  SortKey.Duration -> "Duration"
  SortKey.DateAdded -> "Date added"
}
