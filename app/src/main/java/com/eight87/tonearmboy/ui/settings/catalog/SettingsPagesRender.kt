package com.eight87.tonearmboy.ui.settings.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp

/**
 * Per-row binding the page renderer needs. The catalog says *what* a
 * row is (id, label, icon, kind); the binding supplies *how* it
 * behaves on this concrete page (current toggle state, on-click
 * action). Every row's `id` in [SettingsCatalogEntry.id] must have a
 * matching binding when its sub-page renders, or the row falls back to
 * a stub-like "Coming in v1.1" tap.
 */
sealed class SettingsRowBinding {
  abstract val id: String

  /** Plain navigation / action row. */
  data class Action(
    override val id: String,
    val onClick: () -> Unit,
    /** Optional override for the catalog subtitle (e.g. picker current value). */
    val subtitleOverride: String? = null,
  ) : SettingsRowBinding()

  /** Toggle row backed by a Boolean. */
  data class Toggle(
    override val id: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
  ) : SettingsRowBinding()

  /**
   * Picker row: tapping opens a dialog. The currently-selected value's
   * label is used as the row subtitle. Optional [trailing] composable
   * (e.g. a coloured swatch for the custom-base-theme picker) is shown
   * to the right of the row.
   */
  data class Picker(
    override val id: String,
    val currentLabel: String,
    val onClick: () -> Unit,
    val trailing: (@Composable () -> Unit)? = null,
  ) : SettingsRowBinding()
}

/**
 * Render a settings page from catalog entries belonging to [section],
 * grouped by [Group] into one [SettingsCard] per group, with row
 * bindings supplied by [bindings].
 *
 * Group titles use the human-readable labels in [groupTitleFor]. Inside
 * each card, rows are separated by a thin [SettingsRowDivider]. Page
 * padding is the canonical 16 dp horizontal inset.
 */
@Composable
fun SettingsCatalogPage(
  testTagName: String,
  section: com.eight87.tonearmboy.ui.settings.catalog.Section,
  bindings: List<SettingsRowBinding>,
  modifier: Modifier = Modifier,
) {
  val bindingsById = bindings.associateBy { it.id }
  val pageEntries = SettingsCatalog.bySection(section)
  // R.F.15 — render order follows the order entries appear in the
  // catalog (preserved by groupBy in Kotlin).
  val grouped: List<Pair<GroupRef, List<SettingsCatalogEntry>>> =
    pageEntries.groupBy { it.group }.map { (g, list) -> g to list }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = SettingsDimens.PagePadding)
      .semantics { testTag = testTagName },
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.padding(0.dp))
    grouped.forEach { (group, items) ->
      SettingsCard(title = groupTitleFor(group)) {
        items.forEachIndexed { index, entry ->
          val binding = bindingsById[entry.id]
          RowFromBinding(entry = entry, binding = binding)
          if (index < items.size - 1) SettingsRowDivider()
        }
      }
    }
    // Bottom breathing room so the last card isn't pinned to the system gesture area.
    Spacer(Modifier.padding(bottom = 8.dp))
  }
}

@Composable
private fun RowFromBinding(
  entry: SettingsCatalogEntry,
  binding: SettingsRowBinding?,
) {
  when (binding) {
    is SettingsRowBinding.Toggle -> SettingsToggleRow(
      id = entry.id,
      icon = entry.icon,
      label = entry.label,
      subtitle = entry.subtitle,
      checked = binding.checked,
      onCheckedChange = binding.onCheckedChange,
    )
    is SettingsRowBinding.Picker -> SettingsRow(
      id = entry.id,
      icon = entry.icon,
      label = entry.label,
      subtitle = binding.currentLabel,
      onClick = binding.onClick,
      trailing = binding.trailing,
    )
    is SettingsRowBinding.Action -> SettingsRow(
      id = entry.id,
      icon = entry.icon,
      label = entry.label,
      subtitle = binding.subtitleOverride ?: entry.subtitle,
      onClick = binding.onClick,
    )
    null -> {
      // Catalog entry without a binding — render its catalog subtitle
      // (typically "Coming in v1.1.") and have the tap do nothing
      // visible. This is the explicit stub render path.
      SettingsRow(
        id = entry.id,
        icon = entry.icon,
        label = entry.label,
        subtitle = entry.subtitle,
        onClick = null,
      )
    }
  }
}

/** R.F.15 — group titles now ride the GroupRef inline. */
fun groupTitleFor(group: GroupRef): String = group.label
