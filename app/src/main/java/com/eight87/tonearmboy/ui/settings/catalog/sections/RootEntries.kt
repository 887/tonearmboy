package com.eight87.tonearmboy.ui.settings.catalog.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TouchApp
import com.eight87.tonearmboy.ui.nav.SettingsAbout
import com.eight87.tonearmboy.ui.nav.SettingsAudio
import com.eight87.tonearmboy.ui.nav.SettingsContent
import com.eight87.tonearmboy.ui.nav.SettingsLookAndFeel
import com.eight87.tonearmboy.ui.nav.SettingsPersonalize
import com.eight87.tonearmboy.ui.nav.SettingsRootDest
import com.eight87.tonearmboy.ui.settings.catalog.Groups
import com.eight87.tonearmboy.ui.settings.catalog.RowKind
import com.eight87.tonearmboy.ui.settings.catalog.Section
import com.eight87.tonearmboy.ui.settings.catalog.SettingsCatalog
import com.eight87.tonearmboy.ui.settings.catalog.SettingsCatalogEntry

/**
 * R.F.14 — top-level Settings root entries (the cards visible on the
 * Settings landing surface). Aggregated into [SettingsCatalog.entries].
 */
internal val RootEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_APPEARANCE_LOOK_AND_FEEL,
    label = SECTION_LOOK_AND_FEEL,
    subtitle = "Theme, color scheme, layout.",
    keywords = listOf("appearance", "theme", "dark", "light"),
    icon = Icons.Outlined.Palette,
    section = Section.Root,
    group = Groups.Appearance,
    kind = RowKind.Navigate,
    destination = SettingsLookAndFeel,
    breadcrumb = listOf(SECTION_SETTINGS, SECTION_LOOK_AND_FEEL),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_APPEARANCE_PERSONALIZE,
    label = SECTION_PERSONALIZE,
    subtitle = "Library tabs, behaviour, custom actions.",
    keywords = listOf("appearance", "tabs", "actions"),
    icon = Icons.Outlined.TouchApp,
    section = Section.Root,
    group = Groups.Appearance,
    kind = RowKind.Navigate,
    destination = SettingsPersonalize,
    breadcrumb = listOf(SECTION_SETTINGS, SECTION_PERSONALIZE),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_BEHAVIOUR_CONTENT,
    label = SECTION_CONTENT,
    subtitle = "Sorting, separators, album covers.",
    keywords = listOf("sort", "separator", "covers", "tags"),
    icon = Icons.Outlined.LibraryMusic,
    section = Section.Root,
    group = Groups.Behaviour,
    kind = RowKind.Navigate,
    destination = SettingsContent,
    breadcrumb = listOf(SECTION_SETTINGS, SECTION_CONTENT),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_BEHAVIOUR_AUDIO,
    label = SECTION_AUDIO,
    subtitle = "Playback, volume normalization.",
    keywords = listOf("playback", "replaygain", "volume", "headset"),
    icon = Icons.Outlined.GraphicEq,
    section = Section.Root,
    group = Groups.Behaviour,
    kind = RowKind.Navigate,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_SETTINGS, SECTION_AUDIO),
  ),
  // D.17.3 — Music sources opens a modal dialog (Auxio pattern) instead
  // of a sub-page. Keep the SettingsMusicSources NavKey around as the
  // search-result destination so a hit routes back to the Settings root,
  // where the OpenDialog binding surfaces the dialog.
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_LIBRARY_MUSIC_SOURCES,
    label = "Music sources",
    subtitle = "Choose how the library finds your music.",
    keywords = listOf("folder", "directory", "saf", "storage", "system", "file picker"),
    icon = Icons.Outlined.Folder,
    section = Section.Root,
    group = Groups.Library,
    kind = RowKind.OpenDialog,
    destination = SettingsRootDest,
    breadcrumb = listOf(SECTION_SETTINGS, "Library", "Music sources"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_LIBRARY_REFRESH,
    label = "Refresh music",
    subtitle = "Reload the library, using cached tags when possible.",
    keywords = listOf("reload", "rescan", "scan"),
    icon = Icons.Outlined.Refresh,
    section = Section.Root,
    group = Groups.Library,
    kind = RowKind.Action,
    destination = SettingsRootDest,
    breadcrumb = listOf(SECTION_SETTINGS, "Library", "Refresh music"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_LIBRARY_RESCAN,
    label = "Rescan music",
    subtitle = "Clear the cache and re-read everything. Slower but more complete.",
    keywords = listOf("reload", "refresh", "scan"),
    icon = Icons.Outlined.RestartAlt,
    section = Section.Root,
    group = Groups.Library,
    kind = RowKind.Action,
    destination = SettingsRootDest,
    breadcrumb = listOf(SECTION_SETTINGS, "Library", "Rescan music"),
  ),
  // Phase H.5 — playlist backup actions. JSON envelope written via SAF;
  // tracks identified by (title, artist, album) so the file round-trips
  // across devices and re-scans.
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_LIBRARY_EXPORT_PLAYLISTS,
    label = "Export playlists",
    subtitle = "Save all playlists to a JSON file.",
    keywords = listOf("backup", "json", "save", "playlist"),
    icon = Icons.Outlined.FileDownload,
    section = Section.Root,
    group = Groups.Library,
    kind = RowKind.Action,
    destination = SettingsRootDest,
    breadcrumb = listOf(SECTION_SETTINGS, "Library", "Export playlists"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_LIBRARY_IMPORT_PLAYLISTS,
    label = "Import playlists",
    subtitle = "Load playlists from a JSON file.",
    keywords = listOf("restore", "json", "load", "playlist"),
    icon = Icons.Outlined.FileUpload,
    section = Section.Root,
    group = Groups.Library,
    kind = RowKind.Action,
    destination = SettingsRootDest,
    breadcrumb = listOf(SECTION_SETTINGS, "Library", "Import playlists"),
  ),
  // D.16.4 — About sub-page entry. Lives in its own About category at
  // the very bottom of Settings root.
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_ABOUT,
    label = "About",
    subtitle = "Version, build, license, and credits.",
    keywords = listOf("about", "version", "build", "license", "credits", "github"),
    icon = Icons.Outlined.Info,
    section = Section.Root,
    group = Groups.About,
    kind = RowKind.Navigate,
    destination = SettingsAbout,
    breadcrumb = listOf(SECTION_SETTINGS, "About"),
  ),
)
