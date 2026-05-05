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
import androidx.compose.ui.res.stringResource
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
/**
 * R.F.13 — each binding variant carries its own `Render(entry)` method.
 * The render path is a single dispatch through [Render]; missing bindings
 * fall back to [Stub] at the lookup site, so the renderer's `when` is
 * sealed-exhaustive — adding a new variant without a `Render` becomes a
 * compile-time error rather than a render-time stub. (Settings-F9.)
 */
sealed class SettingsRowBinding {
  abstract val id: String

  @Composable
  abstract fun Render(entry: SettingsCatalogEntry)

  /** Plain navigation / action row. */
  data class Action(
    override val id: String,
    val onClick: () -> Unit,
    /** Optional override for the catalog subtitle (e.g. picker current value). */
    val subtitleOverride: String? = null,
  ) : SettingsRowBinding() {
    @Composable
    override fun Render(entry: SettingsCatalogEntry) {
      SettingsRow(
        id = entry.id,
        icon = entry.icon,
        label = stringResource(entry.labelRes),
        subtitle = subtitleOverride ?: entry.subtitleRes?.let { stringResource(it) },
        onClick = onClick,
      )
    }
  }

  /** Toggle row backed by a Boolean. */
  data class Toggle(
    override val id: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
  ) : SettingsRowBinding() {
    @Composable
    override fun Render(entry: SettingsCatalogEntry) {
      SettingsToggleRow(
        id = entry.id,
        icon = entry.icon,
        label = stringResource(entry.labelRes),
        subtitle = entry.subtitleRes?.let { stringResource(it) },
        checked = checked,
        onCheckedChange = onCheckedChange,
      )
    }
  }

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
  ) : SettingsRowBinding() {
    @Composable
    override fun Render(entry: SettingsCatalogEntry) {
      SettingsRow(
        id = entry.id,
        icon = entry.icon,
        label = stringResource(entry.labelRes),
        subtitle = currentLabel,
        onClick = onClick,
        trailing = trailing,
      )
    }
  }

  /**
   * R.F.13 — fallback variant for catalog entries that have no wired
   * handler on this sub-page. Renders the catalog's own subtitle
   * (typically "Coming in v1.1.") with no click action. Pages don't
   * normally instantiate this directly; it's the implicit fallback at
   * the lookup site in [SettingsCatalogPage].
   */
  data class Stub(override val id: String) : SettingsRowBinding() {
    @Composable
    override fun Render(entry: SettingsCatalogEntry) {
      SettingsRow(
        id = entry.id,
        icon = entry.icon,
        label = stringResource(entry.labelRes),
        subtitle = entry.subtitleRes?.let { stringResource(it) },
        onClick = null,
      )
    }
  }
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
          // R.F.13 — single dispatch through binding.Render; missing
          // bindings fall back to Stub. No null arm.
          val binding = bindingsById[entry.id] ?: SettingsRowBinding.Stub(entry.id)
          binding.Render(entry)
          if (index < items.size - 1) SettingsRowDivider()
        }
      }
    }
    // Bottom breathing room so the last card isn't pinned to the system gesture area.
    Spacer(Modifier.padding(bottom = 8.dp))
  }
}

// R.F.13 — RowFromBinding collapsed: `binding.Render(entry)` at the
// call site. Each variant owns its own composable; no when, no null arm.

/** R.F.15 — group titles now ride the GroupRef inline. */
fun groupTitleFor(group: GroupRef): String = group.label
