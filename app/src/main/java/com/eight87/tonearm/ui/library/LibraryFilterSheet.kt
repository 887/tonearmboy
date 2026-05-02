package com.eight87.tonearm.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.FilterCriteria
import com.eight87.tonearm.data.model.Track
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * D.27.5 — library filter sheet.
 *
 * The user wanted "filter by name, by year, by date added, between two
 * dates, all AND-combined". This sheet drives a [FilterCriteria]:
 *
 *  - Name → [FilterCriteria.nameSubstring] (case-insensitive substring
 *    over title / artist / album / album-artist)
 *  - Year range → [FilterCriteria.yearMin] / [yearMax] (range slider
 *    bounded by the library's actual year span)
 *  - Date-added range → [FilterCriteria.dateAddedAfter] /
 *    [dateAddedBefore] (two `DatePicker` dialogs, both optional)
 *
 * Apply commits, Reset wipes everything to a default `FilterCriteria()`.
 *
 * The current draft state lives inside this composable and only flows
 * back to the host on Apply / Reset — so the user can experiment with
 * range thumbs and discard via dismiss without polluting the active
 * filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
  current: FilterCriteria,
  tracks: List<Track>,
  onDismiss: () -> Unit,
  onApply: (FilterCriteria) -> Unit,
  onReset: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // Year span bounds. Default to a sane fallback when the library is
  // empty or has zero year metadata so the slider remains operable
  // (the user gets thumbs at a wide span instead of a degenerate range).
  val yearSpan = remember(tracks) {
    val years = tracks.mapNotNull { it.year }.filter { it in 1900..2200 }
    if (years.isEmpty()) 1970 to 2030 else (years.min() to years.max())
  }
  val yearLow = yearSpan.first
  val yearHigh = yearSpan.second
  // Guard against a degenerate single-point range (one-track library).
  val yearLowF = yearLow.toFloat()
  val yearHighF = (if (yearHigh > yearLow) yearHigh else yearLow + 1).toFloat()

  var nameDraft by remember(current) { mutableStateOf(current.nameSubstring.orEmpty()) }
  var yearLowDraft by remember(current) { mutableStateOf((current.yearMin ?: yearLow).toFloat()) }
  var yearHighDraft by remember(current) { mutableStateOf((current.yearMax ?: yearHigh).toFloat()) }
  var dateAfterDraft by remember(current) { mutableStateOf(current.dateAddedAfter) }
  var dateBeforeDraft by remember(current) { mutableStateOf(current.dateAddedBefore) }

  var showDateAfterPicker by remember { mutableStateOf(false) }
  var showDateBeforePicker by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "library_filter_sheet" },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Filter library", style = MaterialTheme.typography.titleMedium)

      OutlinedTextField(
        value = nameDraft,
        onValueChange = { nameDraft = it },
        label = { Text("Name (title / artist / album)") },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "filter_name_field" },
      )

      Text(
        "Year: ${yearLowDraft.toInt()}–${yearHighDraft.toInt()}",
        style = MaterialTheme.typography.bodyMedium,
      )
      RangeSlider(
        value = yearLowDraft..yearHighDraft,
        onValueChange = { range ->
          yearLowDraft = range.start
          yearHighDraft = range.endInclusive
        },
        valueRange = yearLowF..yearHighF,
        steps = (yearHighF - yearLowF).toInt().coerceAtLeast(1) - 1,
        modifier = Modifier
          .fillMaxWidth()
          .semantics { testTag = "filter_year_slider" },
      )

      Text("Date added", style = MaterialTheme.typography.bodyMedium)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = { showDateAfterPicker = true },
          modifier = Modifier
            .weight(1f)
            .semantics { testTag = "filter_date_after_button" },
        ) {
          Text(dateAfterDraft?.let { "From: ${formatDate(it)}" } ?: "From: any")
        }
        OutlinedButton(
          onClick = { showDateBeforePicker = true },
          modifier = Modifier
            .weight(1f)
            .semantics { testTag = "filter_date_before_button" },
        ) {
          Text(dateBeforeDraft?.let { "To: ${formatDate(it)}" } ?: "To: any")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = {
            // D.27.5 — Reset wipes the draft and dispatches an empty
            // FilterCriteria upstream. Equivalent to "no filter".
            nameDraft = ""
            yearLowDraft = yearLow.toFloat()
            yearHighDraft = yearHigh.toFloat()
            dateAfterDraft = null
            dateBeforeDraft = null
            onReset()
          },
          modifier = Modifier
            .weight(1f)
            .semantics { testTag = "filter_reset_button" },
        ) { Text("Reset") }
        Button(
          onClick = {
            // Build the criteria. We treat the slider thumbs as
            // bounding the full library span — when both thumbs are
            // pinned to the extremes we omit the year predicate so the
            // user isn't accidentally filtering at-rest. Same logic for
            // the date pickers (null = unconstrained).
            val criteria = FilterCriteria(
              nameSubstring = nameDraft.trim().ifBlank { null },
              yearMin = if (yearLowDraft.toInt() <= yearLow) null else yearLowDraft.toInt(),
              yearMax = if (yearHighDraft.toInt() >= yearHigh) null else yearHighDraft.toInt(),
              dateAddedAfter = dateAfterDraft,
              dateAddedBefore = dateBeforeDraft,
            )
            onApply(criteria)
          },
          modifier = Modifier
            .weight(1f)
            .semantics { testTag = "filter_apply_button" },
        ) { Text("Apply") }
      }
    }
  }

  if (showDateAfterPicker) {
    val state = rememberDatePickerState(
      initialSelectedDateMillis = dateAfterDraft?.let { it * 1000 },
    )
    DatePickerDialog(
      onDismissRequest = { showDateAfterPicker = false },
      confirmButton = {
        TextButton(onClick = {
          dateAfterDraft = state.selectedDateMillis?.let { it / 1000 }
          showDateAfterPicker = false
        }) { Text("OK") }
      },
      dismissButton = {
        TextButton(onClick = {
          dateAfterDraft = null
          showDateAfterPicker = false
        }) { Text("Clear") }
      },
    ) { DatePicker(state = state) }
  }

  if (showDateBeforePicker) {
    val state = rememberDatePickerState(
      initialSelectedDateMillis = dateBeforeDraft?.let { it * 1000 },
    )
    DatePickerDialog(
      onDismissRequest = { showDateBeforePicker = false },
      confirmButton = {
        TextButton(onClick = {
          dateBeforeDraft = state.selectedDateMillis?.let { it / 1000 }
          showDateBeforePicker = false
        }) { Text("OK") }
      },
      dismissButton = {
        TextButton(onClick = {
          dateBeforeDraft = null
          showDateBeforePicker = false
        }) { Text("Clear") }
      },
    ) { DatePicker(state = state) }
  }
}

private fun formatDate(epochSeconds: Long): String {
  // D.27.5 — UTC because the DatePicker emits day-aligned UTC millis;
  // converting through the device's local zone here would shift the
  // displayed date by up to a day.
  val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }
  return fmt.format(java.util.Date(epochSeconds * 1000))
}
