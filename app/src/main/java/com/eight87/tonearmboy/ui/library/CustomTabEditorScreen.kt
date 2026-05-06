package com.eight87.tonearmboy.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.FilterCondition
import com.eight87.tonearmboy.data.FilterCriteria
import com.eight87.tonearmboy.data.db.CustomTabContentType
import com.eight87.tonearmboy.data.db.CustomTabEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * D.30 — full-screen editor for a custom library tab.
 *
 * The form is composed of three sections: a name field, a content-type
 * segmented row (Songs / Albums / Artists / Genres), and a stack of
 * [FilterCondition]s the user assembles via the "Add filter" button.
 * Save composes the chosen conditions into a [FilterCriteria], drops
 * empty conditions, and dispatches upstream. Back discards.
 *
 * The conditions stack is the load-bearing change vs pre-D.30: instead
 * of a fixed seven-section accordion, the user adds the filters they
 * actually want and edits each one in place. AND-only across stacked
 * conditions (confirmed in the D.30 design pass).
 *
 * The library-derived option pools — known genres, artists, albums,
 * year bounds — come in via the [universe] argument so the screen
 * stays a pure UI surface (no Flow plumbing here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTabEditorScreen(
  existing: CustomTabEntity?,
  universe: FilterUniverse,
  onBack: () -> Unit,
  onSave: (name: String, contentType: CustomTabContentType, criteria: FilterCriteria) -> Unit,
) {
  val initial: FilterCriteria = remember(existing) {
    existing?.let { FilterCriteria.fromJson(it.criteriaJson) } ?: FilterCriteria()
  }
  var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
  var contentType by remember(existing) {
    mutableStateOf(existing?.contentType ?: CustomTabContentType.SONGS)
  }
  var conditions by remember(existing) { mutableStateOf(initial.conditions) }
  var showChooser by remember { mutableStateOf(false) }

  val canSave = name.trim().isNotEmpty()

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .semantics { testTag = "custom_tab_editor" },
    topBar = {
      TopAppBar(
        expandedHeight = 48.dp,
        title = {
          Text(
            stringResource(
              if (existing == null) R.string.library_custom_tab_editor_new_title
              else R.string.library_custom_tab_editor_edit_title,
            ),
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_cd_back))
          }
        },
        actions = {
          TextButton(
            onClick = {
              val cleaned = conditions.filterNot { it.isEmpty() }
              val trimmed = name.trim()
              if (trimmed.isNotEmpty()) {
                onSave(trimmed, contentType, FilterCriteria(cleaned))
              }
            },
            enabled = canSave,
            modifier = Modifier.semantics { testTag = "editor_save" },
          ) {
            Text(
              stringResource(
                if (existing == null) R.string.library_custom_tab_editor_create
                else R.string.library_custom_tab_editor_save,
              ),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
    ) {
      item {
        OutlinedTextField(
          value = name,
          onValueChange = { if (it.length <= 32) name = it },
          label = { Text(stringResource(R.string.library_custom_tab_editor_name_label)) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .semantics { testTag = "editor_name" },
        )
      }
      item {
        Text(
          stringResource(R.string.library_custom_tab_editor_content_section),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        val all = CustomTabContentType.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          all.forEachIndexed { index, ct ->
            SegmentedButton(
              selected = contentType == ct,
              onClick = { contentType = ct },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = all.size),
              modifier = Modifier.semantics { testTag = "editor_ct_${ct.name}" },
            ) { Text(stringResource(contentTypeLabelRes(ct))) }
          }
        }
      }
      item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
      item {
        Text(
          stringResource(R.string.library_custom_tab_editor_filters_section),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
      if (conditions.isEmpty()) {
        item {
          Text(
            stringResource(R.string.library_custom_tab_editor_no_filters),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
          )
        }
      } else {
        itemsIndexed(conditions, key = { i, _ -> i }) { i, cond ->
          FilterConditionRow(
            condition = cond,
            universe = universe,
            onChange = { updated ->
              conditions = conditions.toMutableList().also { it[i] = updated }
            },
            onDelete = {
              conditions = conditions.toMutableList().also { it.removeAt(i) }
            },
            tagPrefix = "cond_$i",
          )
        }
      }
      item {
        OutlinedButton(
          onClick = { showChooser = true },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 24.dp)
            .semantics { testTag = "editor_add_filter" },
        ) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Text(stringResource(R.string.library_custom_tab_editor_add_filter), modifier = Modifier.padding(start = 8.dp))
        }
      }
    }
  }

  if (showChooser) {
    ConditionTypeChooser(
      onPick = { newCondition ->
        conditions = conditions + newCondition
        showChooser = false
      },
      onDismiss = { showChooser = false },
    )
  }
}

/** D.18.2 — universe of options the editor offers, derived from the live library. */
data class FilterUniverse(
  val genres: List<String>,
  val artists: List<String>,
  val albums: List<String>,
  val minYear: Int?,
  val maxYear: Int?,
)

@StringRes
internal fun contentTypeLabelRes(ct: CustomTabContentType): Int = when (ct) {
  CustomTabContentType.SONGS -> R.string.library_custom_tab_content_type_songs
  CustomTabContentType.ALBUMS -> R.string.library_custom_tab_content_type_albums
  CustomTabContentType.ARTISTS -> R.string.library_custom_tab_content_type_artists
  CustomTabContentType.GENRES -> R.string.library_custom_tab_content_type_genres
}

// R.F.2 — conditionTypeLabel + conditionSummary collapsed into the
// per-variant ConditionUi registry; see `ConditionUi.kt`.
@StringRes
internal fun conditionTypeLabelRes(condition: FilterCondition): Int =
  ConditionUiRegistry.uiFor(condition).labelRes

internal fun conditionSummary(condition: FilterCondition, context: android.content.Context): String =
  ConditionUiRegistry.uiFor(condition).summary(condition, context)

internal fun formatEpochDay(epochSeconds: Long): String {
  val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }
  return fmt.format(java.util.Date(epochSeconds * 1000))
}

@Composable
internal fun SubstringEditor(
  value: String,
  label: String,
  tag: String,
  onChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = tag },
  )
}

/**
 * One row in the conditions list. Renders the type + summary + delete
 * always; tapping the row expands an inline editor for that condition.
 */
@Composable
private fun FilterConditionRow(
  condition: FilterCondition,
  universe: FilterUniverse,
  onChange: (FilterCondition) -> Unit,
  onDelete: () -> Unit,
  tagPrefix: String,
) {
  var expanded by remember { mutableStateOf(false) }
  val context = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = tagPrefix },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          stringResource(conditionTypeLabelRes(condition)),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Medium,
        )
        Text(conditionSummary(condition, context), style = MaterialTheme.typography.bodySmall)
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.semantics { testTag = "${tagPrefix}_delete" },
      ) {
        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.library_custom_tab_editor_remove_filter_cd))
      }
      Icon(
        imageVector = Icons.Filled.ExpandMore,
        contentDescription = stringResource(
          if (expanded) R.string.library_custom_tab_editor_collapse_cd
          else R.string.library_custom_tab_editor_expand_cd,
        ),
        modifier = Modifier.rotate(if (expanded) 180f else 0f),
      )
    }
    if (expanded) {
      Box(modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) {
        ConditionEditor(condition, universe, onChange)
      }
    }
    HorizontalDivider()
  }
}

// R.F.2 — ConditionEditor collapsed into per-variant ConditionUi.Editor()
// dispatch via the registry. See `ConditionUi.kt`.
@Composable
private fun ConditionEditor(
  condition: FilterCondition,
  universe: FilterUniverse,
  onChange: (FilterCondition) -> Unit,
) {
  ConditionUiRegistry.uiFor(condition).Editor(condition, universe, onChange)
}

@Composable
internal fun YearRangeEditor(
  condition: FilterCondition.YearBetween,
  universe: FilterUniverse,
  onChange: (FilterCondition.YearBetween) -> Unit,
) {
  val low = universe.minYear ?: 1900
  val high = (universe.maxYear ?: 2030).coerceAtLeast(low + 1)
  val curMin = (condition.min ?: low).coerceIn(low, high).toFloat()
  val curMax = (condition.max ?: high).coerceIn(low, high).toFloat()
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      "${curMin.toInt()} – ${curMax.toInt()}",
      style = MaterialTheme.typography.bodySmall,
    )
    RangeSlider(
      value = curMin..curMax,
      onValueChange = { range ->
        onChange(condition.copy(min = range.start.toInt(), max = range.endInclusive.toInt()))
      },
      valueRange = low.toFloat()..high.toFloat(),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { testTag = "year_range" },
    )
  }
}

@Composable
internal fun DateAddedEditor(
  condition: FilterCondition.DateAddedBetween,
  onChange: (FilterCondition.DateAddedBetween) -> Unit,
) {
  var showAfterPicker by remember { mutableStateOf(false) }
  var showBeforePicker by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedButton(
      onClick = { showAfterPicker = true },
      modifier = Modifier
        .weight(1f)
        .semantics { testTag = "date_after_button" },
    ) {
      Text(
        condition.afterEpochSeconds?.let { stringResource(R.string.library_filter_date_from, formatEpochDay(it)) }
          ?: stringResource(R.string.library_filter_date_from_any),
      )
    }
    OutlinedButton(
      onClick = { showBeforePicker = true },
      modifier = Modifier
        .weight(1f)
        .semantics { testTag = "date_before_button" },
    ) {
      Text(
        condition.beforeEpochSeconds?.let { stringResource(R.string.library_filter_date_to, formatEpochDay(it)) }
          ?: stringResource(R.string.library_filter_date_to_any),
      )
    }
  }

  if (showAfterPicker) {
    DatePickerSheet(
      initialEpochSeconds = condition.afterEpochSeconds,
      onConfirm = {
        onChange(condition.copy(afterEpochSeconds = it))
        showAfterPicker = false
      },
      onClear = {
        onChange(condition.copy(afterEpochSeconds = null))
        showAfterPicker = false
      },
      onDismiss = { showAfterPicker = false },
    )
  }
  if (showBeforePicker) {
    DatePickerSheet(
      initialEpochSeconds = condition.beforeEpochSeconds,
      onConfirm = {
        onChange(condition.copy(beforeEpochSeconds = it))
        showBeforePicker = false
      },
      onClear = {
        onChange(condition.copy(beforeEpochSeconds = null))
        showBeforePicker = false
      },
      onDismiss = { showBeforePicker = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
  initialEpochSeconds: Long?,
  onConfirm: (Long) -> Unit,
  onClear: () -> Unit,
  onDismiss: () -> Unit,
) {
  val state = rememberDatePickerState(
    initialSelectedDateMillis = initialEpochSeconds?.let { it * 1000 },
  )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = {
        state.selectedDateMillis?.let { onConfirm(it / 1000) } ?: onDismiss()
      }) { Text(stringResource(R.string.library_filter_date_picker_ok)) }
    },
    dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.library_filter_date_picker_clear)) } },
  ) { DatePicker(state = state) }
}

@Composable
internal fun HasAlbumArtEditor(
  condition: FilterCondition.HasAlbumArt,
  onChange: (FilterCondition.HasAlbumArt) -> Unit,
) {
  Column {
    listOf(
      true to stringResource(R.string.library_filter_album_art_only_with),
      false to stringResource(R.string.library_filter_album_art_only_without),
    ).forEach { (value, label) ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .selectable(selected = condition.value == value, onClick = { onChange(FilterCondition.HasAlbumArt(value)) })
          .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = condition.value == value, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
      }
    }
  }
}

@Composable
internal fun MultiCheckList(
  options: List<String>,
  selected: Set<String>,
  onToggle: (String) -> Unit,
  tagPrefix: String,
  initialVisible: Int = 20,
) {
  var showAll by remember { mutableStateOf(false) }
  if (options.isEmpty()) {
    Text(
      stringResource(R.string.library_filter_multicheck_empty),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(vertical = 8.dp),
    )
    return
  }
  val visible = if (showAll || options.size <= initialVisible) options else options.take(initialVisible)
  Column(modifier = Modifier.heightIn(max = 320.dp)) {
    visible.forEach { value ->
      val checked = value in selected
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .selectable(selected = checked, onClick = { onToggle(value) })
          .padding(vertical = 4.dp)
          .semantics { testTag = "${tagPrefix}_${value}" },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(value, modifier = Modifier.padding(start = 8.dp))
      }
    }
    if (!showAll && options.size > initialVisible) {
      TextButton(onClick = { showAll = true }) {
        Text(stringResource(R.string.library_filter_multicheck_show_all, options.size))
      }
    }
  }
}

/**
 * Bottom-sheet picker that lists every available [FilterCondition]
 * type. Each entry produces a default-empty instance of the chosen
 * variant; the user fills it in via the inline editor on the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionTypeChooser(
  onPick: (FilterCondition) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.semantics { testTag = "condition_chooser" },
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
      Text(
        stringResource(R.string.library_custom_tab_editor_chooser_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
      )
      // R.F.2 — iterate the ConditionUi registry; adding a new
      // FilterCondition variant + registry entry adds it here without
      // changing the chooser code.
      ConditionUiRegistry.all.forEach { ui ->
        ConditionPickRow(
          stringResource(ui.labelRes),
          stringResource(ui.addSubtitleRes),
        ) { onPick(ui.defaultInstance()) }
      }
    }
  }
}

@Composable
private fun ConditionPickRow(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .semantics { testTag = "pick_${title.lowercase().replace(' ', '_')}" },
  ) {
    ListItem(
      headlineContent = { Text(title) },
      supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
    )
  }
}
