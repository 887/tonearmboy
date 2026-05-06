package com.eight87.tonearmboy.ui.settings.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Dimensions used by every settings card / row. Keeping these as
 * top-level constants makes the page-level layout consistent and the
 * "sitting in the middle" inset (16 dp horizontal page padding) easy
 * to read at the call site.
 */
object SettingsDimens {
  val PagePadding = 16.dp
  val CardCornerRadius = 16.dp
  val RowVerticalPadding = 14.dp
  val RowHorizontalPadding = 16.dp
  val IconSize = 24.dp
  val IconLabelGap = 16.dp
  val GroupTitleTopPadding = 20.dp
  val GroupTitleBottomPadding = 8.dp
  val CardSpacing = 16.dp
}

/**
 * The id of a settings row that should briefly highlight on entry, set
 * by the search overlay before it navigates. Sub-pages observe this and
 * compare against each row's id; on match the row flashes its background
 * for ~300 ms and the value is cleared.
 *
 * Nullable (no row to highlight) is the default.
 */
val LocalHighlightedSettingId = compositionLocalOf<MutableState<String?>> {
  mutableStateOf(null)
}

/**
 * Material 3 card that hosts a group of related rows. Renders with
 * `RoundedCornerShape(16.dp)` corners and a tonal `surfaceContainer`
 * background so it visually separates from the page background — the
 * "sitting in the middle" pattern from Android system Settings.
 *
 * @param title Optional small header rendered above the card. When set,
 *   it takes the same horizontal inset as the card itself so the labels
 *   line up with each row's icon column.
 */
@Composable
fun SettingsCard(
  modifier: Modifier = Modifier,
  title: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier = modifier) {
    if (title != null) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
          start = SettingsDimens.RowHorizontalPadding,
          top = SettingsDimens.GroupTitleTopPadding,
          bottom = SettingsDimens.GroupTitleBottomPadding,
        ),
      )
    }
    // m3-expressive B.3 — `surfaceContainerHigh` lifts the card a
    // measurable step above the page (`surface`), matching the
    // Android 16 Settings two-tone look. Default `cardElevation` (0
    // dp tonal, 0 dp shadow) keeps the lift purely tonal — same as
    // the system Settings does it.
    Card(
      shape = RoundedCornerShape(SettingsDimens.CardCornerRadius),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .semantics { testTag = "settings_card" },
    ) {
      Column { content() }
    }
  }
}

/**
 * One row inside a [SettingsCard]. Always has a leading icon (the
 * Android Settings convention is "no orphan rows without icons") plus
 * a label and optional subtitle. The trailing slot hosts the
 * affordance — `Switch`, navigation chevron, picker indicator.
 *
 * If [id] matches [LocalHighlightedSettingId], the row briefly flashes
 * its background to draw the user's eye when the search overlay
 * navigated here.
 */
@Composable
fun SettingsRow(
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  id: String? = null,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  // m3-expressive Phase C — when non-null, render the leading icon
  // as a 40-dp coloured circle avatar holding a 24-dp filled glyph
  // tinted with `accent.onContainer`. When null AND [id] is non-null,
  // we auto-derive the accent via `accentFor(id)` so every settings
  // row across the app gets the avatar without each call site having
  // to pass the parameter explicitly. Pass `accent = null` AND a
  // null `id` to opt out (e.g. a row with no stable id).
  accent: com.eight87.tonearmboy.theme.CategoryAccent? = null,
) {
  // Auto-derive accent when caller didn't pass one but the row has
  // a stable id. Hash-based, so every row gets a stable colour.
  val resolvedAccent = accent ?: id?.let { com.eight87.tonearmboy.theme.accentFor(it) }
  val highlightState = LocalHighlightedSettingId.current
  val highlighted = id != null && highlightState.value == id
  val target =
    if (highlighted) MaterialTheme.colorScheme.primaryContainer
    else Color.Transparent
  val animatedBg by animateColorAsState(
    targetValue = target,
    animationSpec = tween(durationMillis = 300),
    label = "settings_row_highlight",
  )

  // Auto-clear the highlight after ~600 ms so a follow-up navigation to
  // the same id re-fires.
  if (highlighted) {
    LaunchedEffect(id) {
      delay(600)
      if (highlightState.value == id) highlightState.value = null
    }
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(animatedBg)
      .let { if (onClick != null) it.clickable(onClick = onClick) else it }
      .padding(
        horizontal = SettingsDimens.RowHorizontalPadding,
        vertical = SettingsDimens.RowVerticalPadding,
      )
      .heightIn(min = 48.dp)
      .semantics { testTag = "settings_row_${id ?: label}" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (resolvedAccent != null) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(resolvedAccent.container),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = resolvedAccent.onContainer,
          modifier = Modifier.size(SettingsDimens.IconSize),
        )
      }
    } else {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(SettingsDimens.IconSize),
      )
    }
    Spacer(Modifier.size(SettingsDimens.IconLabelGap))
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (trailing != null) {
      Spacer(Modifier.size(8.dp))
      Box { trailing() }
    }
  }
}

/**
 * Toggle row variant. Tapping anywhere on the row flips the state, and
 * the trailing `Switch` follows.
 */
@Composable
fun SettingsToggleRow(
  icon: ImageVector,
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  id: String? = null,
  subtitle: String? = null,
  accent: com.eight87.tonearmboy.theme.CategoryAccent? = null,
) {
  SettingsRow(
    id = id,
    icon = icon,
    label = label,
    subtitle = subtitle,
    onClick = { onCheckedChange(!checked) },
    trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    modifier = modifier,
    accent = accent,
  )
}

/**
 * Subtle divider used between rows inside a [SettingsCard]. Indented to
 * align with the row text (past the icon column) so the icons act as a
 * vertical guideline.
 */
@Composable
fun SettingsRowDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(
      start = SettingsDimens.RowHorizontalPadding +
        SettingsDimens.IconSize +
        SettingsDimens.IconLabelGap,
    ),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}
