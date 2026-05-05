package com.eight87.tonearmboy.ui.library

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.ui.settings.SortDirection
import com.eight87.tonearmboy.ui.settings.SortKey
import com.eight87.tonearmboy.ui.settings.TabSort

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
      Text(stringResource(R.string.library_sort_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))

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
          Text(text = stringResource(sortKeyLabelRes(option)), modifier = Modifier.padding(start = 12.dp))
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
          ) {
            Text(
              stringResource(
                if (dir == SortDirection.Ascending) R.string.library_sort_direction_ascending
                else R.string.library_sort_direction_descending,
              ),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_sort_cancel)) }
        TextButton(onClick = { onConfirm(TabSort(pendingKey, pendingDir)) }) { Text(stringResource(R.string.library_sort_ok)) }
      }
    }
  }
}

@androidx.annotation.StringRes
private fun sortKeyLabelRes(k: SortKey): Int = when (k) {
  SortKey.Name -> R.string.library_sort_by_name
  SortKey.Artist -> R.string.library_sort_by_artist
  SortKey.Album -> R.string.library_sort_by_album
  SortKey.Date -> R.string.library_sort_by_date
  SortKey.Duration -> R.string.library_sort_by_duration
  SortKey.DateAdded -> R.string.library_sort_by_date_added
}
