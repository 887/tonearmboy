package com.eight87.tonearmboy.ui.settings.catalog.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.TouchApp
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.ui.nav.SettingsPersonalize
import com.eight87.tonearmboy.ui.settings.catalog.Groups
import com.eight87.tonearmboy.ui.settings.catalog.RowKind
import com.eight87.tonearmboy.ui.settings.catalog.Section
import com.eight87.tonearmboy.ui.settings.catalog.SettingsCatalog
import com.eight87.tonearmboy.ui.settings.catalog.SettingsCatalogEntry

/** R.F.14 — entries on the Personalize sub-page. */
internal val PersonalizeEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_LIBRARY_TABS,
    label = "Library tabs",
    subtitle = null,
    labelRes = R.string.settings_personalize_library_tabs_label,
    subtitleRes = null,
    keywords = listOf("songs", "albums", "artists", "genres", "playlists", "tabs"),
    icon = Icons.AutoMirrored.Outlined.ViewList,
    section = Section.Personalize,
    group = Groups.Display,
    kind = RowKind.Picker,
    destination = SettingsPersonalize,
    breadcrumb = listOf(SECTION_PERSONALIZE, "Display", "Library tabs"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_CUSTOM_PLAYBACK_BAR_ACTION,
    label = "Custom playback bar action",
    subtitle = null,
    labelRes = R.string.settings_personalize_custom_bar_action_label,
    subtitleRes = null,
    keywords = listOf("skip", "shuffle", "repeat", "next", "long press"),
    icon = Icons.Outlined.TouchApp,
    section = Section.Personalize,
    group = Groups.Display,
    kind = RowKind.Picker,
    destination = SettingsPersonalize,
    breadcrumb = listOf(SECTION_PERSONALIZE, "Display", "Custom playback bar action"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_CUSTOM_NOTIFICATION_ACTION,
    label = "Custom notification action",
    subtitle = null,
    labelRes = R.string.settings_personalize_custom_notification_action_label,
    subtitleRes = null,
    keywords = listOf("shuffle", "repeat", "notification"),
    icon = Icons.Outlined.Notifications,
    section = Section.Personalize,
    group = Groups.Display,
    kind = RowKind.Picker,
    destination = SettingsPersonalize,
    breadcrumb = listOf(SECTION_PERSONALIZE, "Display", "Custom notification action"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_PLAY_FROM_LIBRARY,
    label = "When playing from the library",
    subtitle = null,
    labelRes = R.string.settings_personalize_play_from_library_label,
    subtitleRes = null,
    keywords = listOf("queue", "play from", "all songs", "filter"),
    icon = Icons.Outlined.LibraryMusic,
    section = Section.Personalize,
    group = Groups.PersonalizeBehaviour,
    kind = RowKind.Picker,
    destination = SettingsPersonalize,
    breadcrumb = listOf(SECTION_PERSONALIZE, "Behaviour", "When playing from the library"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_PLAY_FROM_ITEM_DETAILS,
    label = "When playing from item details",
    subtitle = null,
    labelRes = R.string.settings_personalize_play_from_item_details_label,
    subtitleRes = null,
    keywords = listOf("queue", "album", "artist", "play from"),
    icon = Icons.AutoMirrored.Outlined.ListAlt,
    section = Section.Personalize,
    group = Groups.PersonalizeBehaviour,
    kind = RowKind.Picker,
    destination = SettingsPersonalize,
    breadcrumb = listOf(SECTION_PERSONALIZE, "Behaviour", "When playing from item details"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_REMEMBER_SHUFFLE,
    label = "Remember shuffle",
    subtitle = "Restore the previous shuffle state on relaunch.",
    labelRes = R.string.settings_personalize_remember_shuffle_label,
    subtitleRes = R.string.settings_personalize_remember_shuffle_subtitle,
    keywords = listOf("shuffle", "random"),
    icon = Icons.Outlined.Shuffle,
    section = Section.Personalize,
    group = Groups.PersonalizeBehaviour,
    kind = RowKind.Toggle,
    destination = SettingsPersonalize,
    breadcrumb = listOf(SECTION_PERSONALIZE, "Behaviour", "Remember shuffle"),
  ),
)
