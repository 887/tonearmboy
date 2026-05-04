package com.eight87.tonearmboy.ui.settings.catalog.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Sync
import com.eight87.tonearmboy.ui.nav.SettingsContent
import com.eight87.tonearmboy.ui.settings.catalog.Groups
import com.eight87.tonearmboy.ui.settings.catalog.RowKind
import com.eight87.tonearmboy.ui.settings.catalog.Section
import com.eight87.tonearmboy.ui.settings.catalog.SettingsCatalog
import com.eight87.tonearmboy.ui.settings.catalog.SettingsCatalogEntry

/** R.F.14 — entries on the Content sub-page. */
internal val ContentEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_AUTOMATIC_RELOADING,
    label = "Automatic reloading",
    subtitle = "Watch for library changes and rescan automatically. Runs a foreground service.",
    keywords = listOf("watch", "reload", "background", "observer", "rescan"),
    icon = Icons.Outlined.Sync,
    section = Section.Content,
    group = Groups.Music,
    kind = RowKind.Toggle,
    destination = SettingsContent,
    breadcrumb = listOf(SECTION_CONTENT, "Music", "Automatic reloading"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_MULTI_VALUE_SEPARATORS,
    label = "Multi-value separators",
    subtitle = null,
    keywords = listOf("artist", "split", "feat", "comma", "semicolon", "ampersand", "slash"),
    icon = Icons.Outlined.MoreHoriz,
    section = Section.Content,
    group = Groups.Music,
    kind = RowKind.Picker,
    destination = SettingsContent,
    breadcrumb = listOf(SECTION_CONTENT, "Music", "Multi-value separators"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_INTELLIGENT_SORTING,
    label = "Intelligent sorting",
    subtitle = "Ignore leading articles (English, French, German, Spanish, Italian, Dutch) when sorting.",
    keywords = listOf(
      "sort", "alphabetical", "the", "articles",
      "le", "la", "der", "die", "el", "il", "de",
    ),
    icon = Icons.Outlined.SortByAlpha,
    section = Section.Content,
    group = Groups.Music,
    kind = RowKind.Toggle,
    destination = SettingsContent,
    breadcrumb = listOf(SECTION_CONTENT, "Music", "Intelligent sorting"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_HIDE_COLLABORATORS,
    label = "Hide collaborators",
    subtitle = "Show only the primary album artist; collapse featured-artist credits.",
    keywords = listOf("artist", "album artist", "feat"),
    icon = Icons.Outlined.PersonOff,
    section = Section.Content,
    group = Groups.Music,
    kind = RowKind.Toggle,
    destination = SettingsContent,
    breadcrumb = listOf(SECTION_CONTENT, "Music", "Hide collaborators"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_AUTO_DISCOVER_ALBUM_ART,
    label = "Auto-discover missing album art",
    subtitle = "Fetch covers from MusicBrainz Cover Art Archive for albums missing local art (Phase H).",
    keywords = listOf("cover", "art", "musicbrainz", "fetch", "download"),
    icon = Icons.Outlined.ImageSearch,
    section = Section.Content,
    group = Groups.Music,
    kind = RowKind.Toggle,
    destination = SettingsContent,
    breadcrumb = listOf(SECTION_CONTENT, "Music", "Auto-discover missing album art"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_ALBUM_COVERS,
    label = "Album covers",
    subtitle = null,
    keywords = listOf("art", "image", "loading", "balanced", "coil"),
    icon = Icons.Outlined.Photo,
    section = Section.Content,
    group = Groups.Images,
    kind = RowKind.Picker,
    destination = SettingsContent,
    breadcrumb = listOf(SECTION_CONTENT, "Images", "Album covers"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_FORCE_SQUARE_COVERS,
    label = "Force square album covers",
    subtitle = "Render covers as squares instead of rounded rectangles.",
    keywords = listOf("rounded", "square", "art"),
    icon = Icons.Outlined.CropSquare,
    section = Section.Content,
    group = Groups.Images,
    kind = RowKind.Toggle,
    destination = SettingsContent,
    breadcrumb = listOf(SECTION_CONTENT, "Images", "Force square album covers"),
  ),
)
