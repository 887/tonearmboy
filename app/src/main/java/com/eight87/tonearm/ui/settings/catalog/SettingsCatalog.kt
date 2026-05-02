package com.eight87.tonearm.ui.settings.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.eight87.tonearm.ui.nav.SettingsAbout
import com.eight87.tonearm.ui.nav.SettingsAudio
import com.eight87.tonearm.ui.nav.SettingsContent
import com.eight87.tonearm.ui.nav.SettingsLookAndFeel
import com.eight87.tonearm.ui.nav.SettingsMusicSources
import com.eight87.tonearm.ui.nav.SettingsPersonalize

/**
 * Where this entry sits in the settings hierarchy. The root settings
 * screen is rendered from entries whose [SettingsCatalogEntry.section]
 * is [Section.Root]; each sub-page is rendered from entries with the
 * matching section value.
 */
enum class Section { Root, LookAndFeel, Personalize, Content, Audio }

/**
 * A grouping bucket inside a section. All entries with the same
 * (section, group) pair render inside one `SettingsCard`. Order of
 * entries inside the card follows the order they appear in
 * [SettingsCatalog.entries].
 */
enum class Group {
  // Root.
  Appearance,
  Behaviour,
  Library,
  About,
  // Look and Feel.
  Theme,
  // Personalize.
  Display,
  PersonalizeBehaviour,
  // Content.
  Music,
  Images,
  // Audio.
  Playback,
  VolumeNormalization,
}

/**
 * What kind of UI affordance the row exposes. The page renderer
 * switches on this to decide whether the row needs a Switch, a picker
 * dialog, a snackbar stub, or a plain navigation tap.
 *
 * The stable [SettingsCatalogEntry.id] is what the page binds to a
 * concrete handler; this enum just describes the trailing affordance.
 */
enum class RowKind {
  /** Tapping navigates to another destination. */
  Navigate,
  /** Boolean toggle wired to the repository. */
  Toggle,
  /** Picker (radio dialog) wired to the repository. */
  Picker,
  /** Leaf action with a confirmation or immediate effect. */
  Action,
  /**
   * D.17.3 — tapping opens a modal `Dialog` rendered above the current
   * page; no navigation push. Used for Music sources so the user
   * can adjust the source set + mode without leaving the Settings
   * root surface.
   */
  OpenDialog,
  /** Not yet wired — taps trigger a "Coming in v1.1" snackbar. */
  Stub,
}

/**
 * One settings entry. The catalog is the single source of truth: every
 * row visible in any settings sub-page comes from this list, and the
 * global search filters this list. There is no parallel "screen
 * definition" — the screens render by filtering the catalog.
 *
 * @param id Stable, unique identifier. Used as the bind key (the page
 *   matches on this to look up a wired handler) and as the highlight
 *   target when navigating from search.
 * @param breadcrumb Path shown in search results, e.g.
 *   `["Audio", "Volume normalization", "ReplayGain pre-amp"]`. The
 *   first element is the sub-page name; the last is the row label.
 * @param keywords Extra match terms for search — picker option names,
 *   alternate phrasings. The "Custom playback bar action" entry has
 *   keywords like `["skip", "shuffle"]` so typing "shuffle" finds it.
 * @param destination The sub-page to navigate to when a search result
 *   is tapped. For action / stub rows on the root page, this is the
 *   root page itself.
 */
data class SettingsCatalogEntry(
  val id: String,
  val label: String,
  val subtitle: String? = null,
  val keywords: List<String> = emptyList(),
  val icon: ImageVector,
  val section: Section,
  val group: Group,
  val kind: RowKind,
  val destination: NavKey,
  val breadcrumb: List<String>,
)

/**
 * Single source of truth for every settings row. Adding a new setting
 * is one entry here plus a binding in the corresponding sub-page
 * renderer. The Robolectric `SettingsCatalogTest` proves no orphans
 * (every wired UI row has a catalog entry) and no unreachable entries
 * (every catalog entry is rendered by some sub-page).
 */
object SettingsCatalog {

  // Stable IDs. Tests reference these; do not rename without updating
  // both the bindings in `SettingsPagesRender.kt` and the test file.
  const val ID_APPEARANCE_LOOK_AND_FEEL = "appearance.look_and_feel"
  const val ID_APPEARANCE_PERSONALIZE = "appearance.personalize"
  const val ID_BEHAVIOUR_CONTENT = "behaviour.content"
  const val ID_BEHAVIOUR_AUDIO = "behaviour.audio"
  const val ID_LIBRARY_MUSIC_SOURCES = "library.music_sources"
  const val ID_LIBRARY_REFRESH = "library.refresh"
  const val ID_LIBRARY_RESCAN = "library.rescan"
  const val ID_ABOUT = "about"

  const val ID_THEME = "look_and_feel.theme"
  const val ID_COLOR_SCHEME = "look_and_feel.color_scheme"
  const val ID_BLACK_THEME = "look_and_feel.black_theme"
  const val ID_BASE_THEME = "look_and_feel.base_theme"
  const val ID_ALBUM_ART_TINT = "look_and_feel.album_art_tint"

  const val ID_LIBRARY_TABS = "personalize.library_tabs"
  const val ID_CUSTOM_PLAYBACK_BAR_ACTION = "personalize.custom_playback_bar_action"
  const val ID_CUSTOM_NOTIFICATION_ACTION = "personalize.custom_notification_action"
  const val ID_PLAY_FROM_LIBRARY = "personalize.play_from_library"
  const val ID_PLAY_FROM_ITEM_DETAILS = "personalize.play_from_item_details"
  const val ID_REMEMBER_SHUFFLE = "personalize.remember_shuffle"

  const val ID_AUTOMATIC_RELOADING = "content.automatic_reloading"
  const val ID_MULTI_VALUE_SEPARATORS = "content.multi_value_separators"
  const val ID_INTELLIGENT_SORTING = "content.intelligent_sorting"
  const val ID_HIDE_COLLABORATORS = "content.hide_collaborators"
  const val ID_AUTO_DISCOVER_ALBUM_ART = "content.auto_discover_album_art"
  const val ID_ALBUM_COVERS = "content.album_covers"
  const val ID_FORCE_SQUARE_COVERS = "content.force_square_covers"

  const val ID_HEADSET_AUTOPLAY = "audio.headset_autoplay"
  const val ID_REWIND_BEFORE_SKIP = "audio.rewind_before_skip"
  const val ID_PAUSE_ON_REPEAT = "audio.pause_on_repeat"
  const val ID_REMEMBER_PAUSE = "audio.remember_pause"
  const val ID_REPLAYGAIN_STRATEGY = "audio.replaygain_strategy"
  const val ID_REPLAYGAIN_PREAMP = "audio.replaygain_preamp"
  // Phase H.3 — sleep timer.
  const val ID_SLEEP_TIMER = "audio.sleep_timer"
  // Phase H.4 — system equalizer hand-off.
  const val ID_SYSTEM_EQUALIZER = "audio.system_equalizer"

  // Phase H.5 — playlist backup actions on the Settings root, in the
  // Library group alongside Music sources / Refresh / Rescan.
  const val ID_LIBRARY_EXPORT_PLAYLISTS = "library.export_playlists"
  const val ID_LIBRARY_IMPORT_PLAYLISTS = "library.import_playlists"

  // Section labels used in breadcrumbs and group headers.
  private const val SECTION_LOOK_AND_FEEL = "Look and Feel"
  private const val SECTION_PERSONALIZE = "Personalize"
  private const val SECTION_CONTENT = "Content"
  private const val SECTION_AUDIO = "Audio"
  private const val SECTION_SETTINGS = "Settings"

  val entries: List<SettingsCatalogEntry> = listOf(
    // ---------------------------------------------------------------
    // Settings root — top-level navigation cards + library actions.
    // ---------------------------------------------------------------
    SettingsCatalogEntry(
      id = ID_APPEARANCE_LOOK_AND_FEEL,
      label = SECTION_LOOK_AND_FEEL,
      subtitle = "Theme, color scheme, layout.",
      keywords = listOf("appearance", "theme", "dark", "light"),
      icon = Icons.Outlined.Palette,
      section = Section.Root,
      group = Group.Appearance,
      kind = RowKind.Navigate,
      destination = SettingsLookAndFeel,
      breadcrumb = listOf(SECTION_SETTINGS, SECTION_LOOK_AND_FEEL),
    ),
    SettingsCatalogEntry(
      id = ID_APPEARANCE_PERSONALIZE,
      label = SECTION_PERSONALIZE,
      subtitle = "Library tabs, behaviour, custom actions.",
      keywords = listOf("appearance", "tabs", "actions"),
      icon = Icons.Outlined.TouchApp,
      section = Section.Root,
      group = Group.Appearance,
      kind = RowKind.Navigate,
      destination = SettingsPersonalize,
      breadcrumb = listOf(SECTION_SETTINGS, SECTION_PERSONALIZE),
    ),
    SettingsCatalogEntry(
      id = ID_BEHAVIOUR_CONTENT,
      label = SECTION_CONTENT,
      subtitle = "Sorting, separators, album covers.",
      keywords = listOf("sort", "separator", "covers", "tags"),
      icon = Icons.Outlined.LibraryMusic,
      section = Section.Root,
      group = Group.Behaviour,
      kind = RowKind.Navigate,
      destination = SettingsContent,
      breadcrumb = listOf(SECTION_SETTINGS, SECTION_CONTENT),
    ),
    SettingsCatalogEntry(
      id = ID_BEHAVIOUR_AUDIO,
      label = SECTION_AUDIO,
      subtitle = "Playback, volume normalization.",
      keywords = listOf("playback", "replaygain", "volume", "headset"),
      icon = Icons.Outlined.GraphicEq,
      section = Section.Root,
      group = Group.Behaviour,
      kind = RowKind.Navigate,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_SETTINGS, SECTION_AUDIO),
    ),
    // D.17.3 — Music sources opens a modal dialog (Auxio pattern)
    // instead of a sub-page. Keep the [SettingsMusicSources] NavKey
    // around as the destination for search results so a search hit
    // routes back to the Settings root, where the OpenDialog binding
    // surfaces the dialog.
    SettingsCatalogEntry(
      id = ID_LIBRARY_MUSIC_SOURCES,
      label = "Music sources",
      subtitle = "Choose how the library finds your music.",
      keywords = listOf("folder", "directory", "saf", "storage", "system", "file picker"),
      icon = Icons.Outlined.Folder,
      section = Section.Root,
      group = Group.Library,
      kind = RowKind.OpenDialog,
      destination = com.eight87.tonearm.ui.nav.SettingsRootDest,
      breadcrumb = listOf(SECTION_SETTINGS, "Library", "Music sources"),
    ),
    SettingsCatalogEntry(
      id = ID_LIBRARY_REFRESH,
      label = "Refresh music",
      subtitle = "Reload the library, using cached tags when possible.",
      keywords = listOf("reload", "rescan", "scan"),
      icon = Icons.Outlined.Refresh,
      section = Section.Root,
      group = Group.Library,
      kind = RowKind.Action,
      destination = com.eight87.tonearm.ui.nav.SettingsRootDest,
      breadcrumb = listOf(SECTION_SETTINGS, "Library", "Refresh music"),
    ),
    SettingsCatalogEntry(
      id = ID_LIBRARY_RESCAN,
      label = "Rescan music",
      subtitle = "Clear the cache and re-read everything. Slower but more complete.",
      keywords = listOf("reload", "refresh", "scan"),
      icon = Icons.Outlined.RestartAlt,
      section = Section.Root,
      group = Group.Library,
      kind = RowKind.Action,
      destination = com.eight87.tonearm.ui.nav.SettingsRootDest,
      breadcrumb = listOf(SECTION_SETTINGS, "Library", "Rescan music"),
    ),
    // Phase H.5 — playlist backup actions. JSON envelope written via
    // SAF; tracks identified by (title, artist, album) so the file
    // round-trips across devices and re-scans.
    SettingsCatalogEntry(
      id = ID_LIBRARY_EXPORT_PLAYLISTS,
      label = "Export playlists",
      subtitle = "Save all playlists to a JSON file.",
      keywords = listOf("backup", "json", "save", "playlist"),
      icon = Icons.Outlined.FileDownload,
      section = Section.Root,
      group = Group.Library,
      kind = RowKind.Action,
      destination = com.eight87.tonearm.ui.nav.SettingsRootDest,
      breadcrumb = listOf(SECTION_SETTINGS, "Library", "Export playlists"),
    ),
    SettingsCatalogEntry(
      id = ID_LIBRARY_IMPORT_PLAYLISTS,
      label = "Import playlists",
      subtitle = "Load playlists from a JSON file.",
      keywords = listOf("restore", "json", "load", "playlist"),
      icon = Icons.Outlined.FileUpload,
      section = Section.Root,
      group = Group.Library,
      kind = RowKind.Action,
      destination = com.eight87.tonearm.ui.nav.SettingsRootDest,
      breadcrumb = listOf(SECTION_SETTINGS, "Library", "Import playlists"),
    ),
    // D.16.4 — About sub-page entry. Lives in its own About category at
    // the very bottom of Settings root (NOT under Library — that was a
    // categorisation error in the initial D.16 commit; user flagged it).
    SettingsCatalogEntry(
      id = ID_ABOUT,
      label = "About",
      subtitle = "Version, build, license, and credits.",
      keywords = listOf("about", "version", "build", "license", "credits", "github"),
      icon = Icons.Outlined.Info,
      section = Section.Root,
      group = Group.About,
      kind = RowKind.Navigate,
      destination = SettingsAbout,
      breadcrumb = listOf(SECTION_SETTINGS, "About"),
    ),

    // ---------------------------------------------------------------
    // Look and Feel.
    // ---------------------------------------------------------------
    SettingsCatalogEntry(
      id = ID_THEME,
      label = "Theme",
      subtitle = null,
      keywords = listOf("dark", "light", "automatic", "system"),
      icon = Icons.Outlined.Palette,
      section = Section.LookAndFeel,
      group = Group.Theme,
      kind = RowKind.Picker,
      destination = SettingsLookAndFeel,
      breadcrumb = listOf(SECTION_LOOK_AND_FEEL, "Theme", "Theme"),
    ),
    // D.20.4 — base-theme picker. Subsumes the legacy "Color scheme"
    // + "Black theme" pair into a single three-way decision; the
    // album-art tint toggle below sits on top.
    SettingsCatalogEntry(
      id = ID_BASE_THEME,
      label = "Base theme",
      subtitle = "Foundation colors. Album art tint sits on top.",
      keywords = listOf(
        "dynamic", "material you", "brand", "palette",
        "amoled", "oled", "pure black", "static",
      ),
      icon = Icons.Outlined.ColorLens,
      section = Section.LookAndFeel,
      group = Group.Theme,
      kind = RowKind.Picker,
      destination = SettingsLookAndFeel,
      breadcrumb = listOf(SECTION_LOOK_AND_FEEL, "Theme", "Base theme"),
    ),
    SettingsCatalogEntry(
      id = ID_ALBUM_ART_TINT,
      label = "Tint chrome by album art",
      subtitle = "Bias surfaces toward the playing track's dominant color.",
      keywords = listOf("palette", "tint", "album", "cover", "art", "color"),
      icon = Icons.Outlined.Palette,
      section = Section.LookAndFeel,
      group = Group.Theme,
      kind = RowKind.Toggle,
      destination = SettingsLookAndFeel,
      breadcrumb = listOf(SECTION_LOOK_AND_FEEL, "Theme", "Tint chrome by album art"),
    ),

    // ---------------------------------------------------------------
    // Personalize.
    // ---------------------------------------------------------------
    SettingsCatalogEntry(
      id = ID_LIBRARY_TABS,
      label = "Library tabs",
      subtitle = null,
      keywords = listOf("songs", "albums", "artists", "genres", "playlists", "tabs"),
      icon = Icons.AutoMirrored.Outlined.ViewList,
      section = Section.Personalize,
      group = Group.Display,
      kind = RowKind.Picker,
      destination = SettingsPersonalize,
      breadcrumb = listOf(SECTION_PERSONALIZE, "Display", "Library tabs"),
    ),
    SettingsCatalogEntry(
      id = ID_CUSTOM_PLAYBACK_BAR_ACTION,
      label = "Custom playback bar action",
      subtitle = null,
      keywords = listOf("skip", "shuffle", "repeat", "next", "long press"),
      icon = Icons.Outlined.TouchApp,
      section = Section.Personalize,
      group = Group.Display,
      kind = RowKind.Picker,
      destination = SettingsPersonalize,
      breadcrumb = listOf(SECTION_PERSONALIZE, "Display", "Custom playback bar action"),
    ),
    SettingsCatalogEntry(
      id = ID_CUSTOM_NOTIFICATION_ACTION,
      label = "Custom notification action",
      subtitle = null,
      keywords = listOf("shuffle", "repeat", "notification"),
      icon = Icons.Outlined.Notifications,
      section = Section.Personalize,
      group = Group.Display,
      kind = RowKind.Picker,
      destination = SettingsPersonalize,
      breadcrumb = listOf(SECTION_PERSONALIZE, "Display", "Custom notification action"),
    ),
    SettingsCatalogEntry(
      id = ID_PLAY_FROM_LIBRARY,
      label = "When playing from the library",
      subtitle = null,
      keywords = listOf("queue", "play from", "all songs", "filter"),
      icon = Icons.Outlined.LibraryMusic,
      section = Section.Personalize,
      group = Group.PersonalizeBehaviour,
      kind = RowKind.Picker,
      destination = SettingsPersonalize,
      breadcrumb = listOf(SECTION_PERSONALIZE, "Behaviour", "When playing from the library"),
    ),
    SettingsCatalogEntry(
      id = ID_PLAY_FROM_ITEM_DETAILS,
      label = "When playing from item details",
      subtitle = null,
      keywords = listOf("queue", "album", "artist", "play from"),
      icon = Icons.AutoMirrored.Outlined.ListAlt,
      section = Section.Personalize,
      group = Group.PersonalizeBehaviour,
      kind = RowKind.Picker,
      destination = SettingsPersonalize,
      breadcrumb = listOf(SECTION_PERSONALIZE, "Behaviour", "When playing from item details"),
    ),
    SettingsCatalogEntry(
      id = ID_REMEMBER_SHUFFLE,
      label = "Remember shuffle",
      subtitle = "Restore the previous shuffle state on relaunch.",
      keywords = listOf("shuffle", "random"),
      icon = Icons.Outlined.Shuffle,
      section = Section.Personalize,
      group = Group.PersonalizeBehaviour,
      kind = RowKind.Toggle,
      destination = SettingsPersonalize,
      breadcrumb = listOf(SECTION_PERSONALIZE, "Behaviour", "Remember shuffle"),
    ),

    // ---------------------------------------------------------------
    // Content.
    // ---------------------------------------------------------------
    SettingsCatalogEntry(
      id = ID_AUTOMATIC_RELOADING,
      label = "Automatic reloading",
      subtitle = "Watch for library changes and rescan automatically. Runs a foreground service.",
      keywords = listOf("watch", "reload", "background", "observer", "rescan"),
      icon = Icons.Outlined.Sync,
      section = Section.Content,
      group = Group.Music,
      kind = RowKind.Toggle,
      destination = SettingsContent,
      breadcrumb = listOf(SECTION_CONTENT, "Music", "Automatic reloading"),
    ),
    SettingsCatalogEntry(
      id = ID_MULTI_VALUE_SEPARATORS,
      label = "Multi-value separators",
      subtitle = null,
      keywords = listOf("artist", "split", "feat", "comma", "semicolon", "ampersand", "slash"),
      icon = Icons.Outlined.MoreHoriz,
      section = Section.Content,
      group = Group.Music,
      kind = RowKind.Picker,
      destination = SettingsContent,
      breadcrumb = listOf(SECTION_CONTENT, "Music", "Multi-value separators"),
    ),
    SettingsCatalogEntry(
      id = ID_INTELLIGENT_SORTING,
      label = "Intelligent sorting",
      subtitle = "Ignore leading articles (English, French, German, Spanish, Italian, Dutch) when sorting.",
      keywords = listOf(
        "sort", "alphabetical", "the", "articles",
        "le", "la", "der", "die", "el", "il", "de",
      ),
      icon = Icons.Outlined.SortByAlpha,
      section = Section.Content,
      group = Group.Music,
      kind = RowKind.Toggle,
      destination = SettingsContent,
      breadcrumb = listOf(SECTION_CONTENT, "Music", "Intelligent sorting"),
    ),
    SettingsCatalogEntry(
      id = ID_HIDE_COLLABORATORS,
      label = "Hide collaborators",
      subtitle = "Show only the primary album artist; collapse featured-artist credits.",
      keywords = listOf("artist", "album artist", "feat"),
      icon = Icons.Outlined.PersonOff,
      section = Section.Content,
      group = Group.Music,
      kind = RowKind.Toggle,
      destination = SettingsContent,
      breadcrumb = listOf(SECTION_CONTENT, "Music", "Hide collaborators"),
    ),
    SettingsCatalogEntry(
      id = ID_AUTO_DISCOVER_ALBUM_ART,
      label = "Auto-discover missing album art",
      subtitle = "Fetch covers from MusicBrainz Cover Art Archive for albums missing local art (Phase H).",
      keywords = listOf("cover", "art", "musicbrainz", "fetch", "download"),
      icon = Icons.Outlined.ImageSearch,
      section = Section.Content,
      group = Group.Music,
      kind = RowKind.Toggle,
      destination = SettingsContent,
      breadcrumb = listOf(SECTION_CONTENT, "Music", "Auto-discover missing album art"),
    ),
    SettingsCatalogEntry(
      id = ID_ALBUM_COVERS,
      label = "Album covers",
      subtitle = null,
      keywords = listOf("art", "image", "loading", "balanced", "coil"),
      icon = Icons.Outlined.Photo,
      section = Section.Content,
      group = Group.Images,
      kind = RowKind.Picker,
      destination = SettingsContent,
      breadcrumb = listOf(SECTION_CONTENT, "Images", "Album covers"),
    ),
    SettingsCatalogEntry(
      id = ID_FORCE_SQUARE_COVERS,
      label = "Force square album covers",
      subtitle = "Render covers as squares instead of rounded rectangles.",
      keywords = listOf("rounded", "square", "art"),
      icon = Icons.Outlined.CropSquare,
      section = Section.Content,
      group = Group.Images,
      kind = RowKind.Toggle,
      destination = SettingsContent,
      breadcrumb = listOf(SECTION_CONTENT, "Images", "Force square album covers"),
    ),

    // ---------------------------------------------------------------
    // Audio.
    // ---------------------------------------------------------------
    SettingsCatalogEntry(
      id = ID_HEADSET_AUTOPLAY,
      label = "Headset autoplay",
      subtitle = "Begin playback automatically when headphones connect.",
      keywords = listOf("headphones", "bluetooth", "autoplay", "connect"),
      icon = Icons.Outlined.Headphones,
      section = Section.Audio,
      group = Group.Playback,
      kind = RowKind.Toggle,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_AUDIO, "Playback", "Headset autoplay"),
    ),
    SettingsCatalogEntry(
      id = ID_REWIND_BEFORE_SKIP,
      label = "Rewind before skipping back",
      subtitle = "Tap previous within a few seconds rewinds; otherwise jumps to the previous track.",
      keywords = listOf("previous", "rewind", "skip"),
      icon = Icons.Outlined.FastRewind,
      section = Section.Audio,
      group = Group.Playback,
      kind = RowKind.Toggle,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_AUDIO, "Playback", "Rewind before skipping back"),
    ),
    SettingsCatalogEntry(
      id = ID_PAUSE_ON_REPEAT,
      label = "Pause on repeat",
      subtitle = "Pause at the end of the first play instead of looping the same track.",
      keywords = listOf("repeat", "loop", "pause"),
      icon = Icons.Outlined.PauseCircle,
      section = Section.Audio,
      group = Group.Playback,
      kind = RowKind.Toggle,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_AUDIO, "Playback", "Pause on repeat"),
    ),
    SettingsCatalogEntry(
      id = ID_REMEMBER_PAUSE,
      label = "Remember pause",
      subtitle = "Restore the paused position on relaunch.",
      keywords = listOf("resume", "position", "pause"),
      icon = Icons.Outlined.Pause,
      section = Section.Audio,
      group = Group.Playback,
      kind = RowKind.Toggle,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_AUDIO, "Playback", "Remember pause"),
    ),
    SettingsCatalogEntry(
      id = ID_REPLAYGAIN_STRATEGY,
      label = "ReplayGain strategy",
      subtitle = null,
      keywords = listOf("replaygain", "normalization", "track", "album", "smart"),
      icon = Icons.Outlined.GraphicEq,
      section = Section.Audio,
      group = Group.VolumeNormalization,
      kind = RowKind.Picker,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_AUDIO, "Volume normalization", "ReplayGain strategy"),
    ),
    SettingsCatalogEntry(
      id = ID_REPLAYGAIN_PREAMP,
      label = "ReplayGain pre-amp",
      subtitle = null,
      keywords = listOf("replaygain", "preamp", "volume", "boost", "gain", "slider"),
      icon = Icons.Outlined.Tune,
      section = Section.Audio,
      group = Group.VolumeNormalization,
      kind = RowKind.Picker,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_AUDIO, "Volume normalization", "ReplayGain pre-amp"),
    ),
    // Phase H.3 — sleep timer. Picker-style row that opens a presets
    // dialog (15 / 30 / 45 / 60 / 90 min + custom + "wait for end of
    // song"). Reuses RowKind.OpenDialog so the row hosts the dialog
    // inline instead of pushing a sub-page.
    SettingsCatalogEntry(
      id = ID_SLEEP_TIMER,
      label = "Sleep timer",
      subtitle = "Pause playback after a delay.",
      keywords = listOf("sleep", "timer", "pause", "bedtime", "off"),
      icon = Icons.Outlined.Bedtime,
      section = Section.Audio,
      group = Group.Playback,
      kind = RowKind.OpenDialog,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_AUDIO, "Playback", "Sleep timer"),
    ),
    // Phase H.4 — System equalizer hand-off. Tapping fires
    // ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL on the active audio
    // session; if no app handles it the snackbar falls back.
    SettingsCatalogEntry(
      id = ID_SYSTEM_EQUALIZER,
      label = "System equalizer",
      subtitle = "Open the system audio effect panel.",
      keywords = listOf("equalizer", "eq", "audio effects", "system"),
      icon = Icons.Outlined.Equalizer,
      section = Section.Audio,
      group = Group.VolumeNormalization,
      kind = RowKind.Action,
      destination = SettingsAudio,
      breadcrumb = listOf(SECTION_AUDIO, "Volume normalization", "System equalizer"),
    ),
  )

  /** Look up an entry by id. Throws if missing — IDs are compile-time stable. */
  fun byId(id: String): SettingsCatalogEntry =
    entries.first { it.id == id }

  /** Entries belonging to one section, grouped by [Group] and rendered in one card per group. */
  fun bySection(section: Section): List<SettingsCatalogEntry> =
    entries.filter { it.section == section }

  /**
   * Search the catalog. Case-insensitive substring match against
   * label, subtitle, and keywords. Empty query returns an empty list
   * (the search overlay shows an empty-state hint instead).
   */
  fun search(query: String): List<SettingsCatalogEntry> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return entries.filter { entry ->
      entry.label.lowercase().contains(q) ||
        (entry.subtitle?.lowercase()?.contains(q) ?: false) ||
        entry.keywords.any { it.lowercase().contains(q) }
    }
  }

  /** Human-readable breadcrumb path joined with `>` for search result subtitles. */
  fun breadcrumbPath(entry: SettingsCatalogEntry): String =
    entry.breadcrumb.joinToString(" > ")
}
