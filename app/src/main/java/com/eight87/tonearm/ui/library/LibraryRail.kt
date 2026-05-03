package com.eight87.tonearm.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.db.CustomTabEntity
import com.eight87.tonearm.ui.settings.LibraryTab

/**
 * D.18.5 — sealed wrapper covering both built-in and user-defined tabs
 * so the rail and the content host can switch on a single sum type.
 */
sealed class RailItem {
  abstract val key: String
  abstract val label: String

  data class BuiltIn(val tab: LibraryTab) : RailItem() {
    override val key: String get() = "builtin_${tab.name}"
    override val label: String get() = tab.name
  }

  data class Custom(val tab: CustomTabEntity) : RailItem() {
    override val key: String get() = "custom_${tab.id}"
    override val label: String get() = tab.name
  }
}

/**
 * Vertical tab rail used as the library's primary navigation chrome.
 * Replaces the previous horizontal `PrimaryTabRow`.
 *
 * Layout:
 *   - fixed 52 dp wide column on the left edge
 *   - scrollable region containing one rotated text label per visible
 *     tab, top-to-bottom — overflow scrolls vertically rather than
 *     clipping
 *   - settings gear pinned at the bottom, always on screen
 *
 * The active tab gets bold text + a 2 dp accent stripe on the right
 * edge of the rail (touching the content area). Inactive labels render
 * dim. Vertical text uses `Modifier.rotate(-90f)` on a `Text`; the
 * rotation does not change the laid-out size, so we wrap each label in
 * a small Box that reserves enough height for the longest expected
 * label and lets the rotated Text overflow into it.
 */
@Composable
fun LibraryRail(
  tabs: List<LibraryTab>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  customTabs: List<CustomTabEntity> = emptyList(),
) {
  val railWidth = 52.dp

  Box(
    modifier = modifier
      .fillMaxHeight()
      .requiredWidth(railWidth)
      .background(MaterialTheme.colorScheme.surface)
      .semantics { testTag = "library_rail" },
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Tabs occupy whatever vertical space remains after the gear is
      // laid out at the bottom. Wrapping them in a weighted scroll
      // container guarantees the gear is never pushed off-screen and
      // the tabs scroll when their natural height exceeds the rail.
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(Modifier.size(8.dp))
        tabs.forEachIndexed { index, tab ->
          RailTabItem(
            label = tabLabel(tab),
            tabName = tab.name,
            selected = index == selectedIndex,
            onClick = { onSelect(index) },
          )
        }
        // D.18.5 — render any user-defined custom tabs after the
        // built-ins. Selection indices `tabs.size + i` route to the
        // i-th custom tab; the content host knows the same shape.
        customTabs.forEachIndexed { i, custom ->
          val railIndex = tabs.size + i
          RailTabItem(
            label = custom.name,
            tabName = "custom_${custom.id}",
            selected = railIndex == selectedIndex,
            onClick = { onSelect(railIndex) },
          )
        }
      }
      IconButton(
        onClick = onOpenSettings,
        modifier = Modifier
          .padding(bottom = 8.dp)
          .semantics { testTag = "rail_settings" },
      ) {
        Icon(
          imageVector = Icons.Filled.Settings,
          contentDescription = "Settings",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun RailTabItem(
  label: String,
  tabName: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  val labelColor =
    if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

  // Each item is a fixed-size Box. A fixed height is important because
  // the active item draws a 2 dp accent stripe with `fillMaxHeight()`,
  // and we don't want that to compete with the parent Column for
  // vertical space (which would let the selected tab swallow all the
  // remaining height and hide its siblings).
  Box(
    modifier = Modifier
      .size(width = 52.dp, height = 108.dp)
      .clickable(onClick = onClick)
      .semantics { testTag = "rail_tab_$tabName" },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = labelColor,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      maxLines = 1,
      modifier = Modifier
        .wrapContentSize(unbounded = true)
        .rotate(-90f),
    )
    if (selected) {
      // 2 dp accent stripe pinned to the right edge of the rail.
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .fillMaxHeight()
          .width(2.dp)
          .background(accent)
          .semantics { testTag = "rail_accent" },
      )
    }
  }
}

private fun tabLabel(tab: LibraryTab): String = when (tab) {
  LibraryTab.Songs -> "Songs"
  LibraryTab.Albums -> "Albums"
  LibraryTab.Artists -> "Artists"
  LibraryTab.Genres -> "Genres"
  LibraryTab.Playlists -> "Playlists"
}
