package com.eight87.tonearmboy.ui.settings.catalog.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Tune
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.ui.nav.SettingsAudio
import com.eight87.tonearmboy.ui.settings.catalog.Groups
import com.eight87.tonearmboy.ui.settings.catalog.RowKind
import com.eight87.tonearmboy.ui.settings.catalog.Section
import com.eight87.tonearmboy.ui.settings.catalog.SettingsCatalog
import com.eight87.tonearmboy.ui.settings.catalog.SettingsCatalogEntry

/** R.F.14 — entries on the Audio sub-page. */
internal val AudioEntries: List<SettingsCatalogEntry> = listOf(
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_HEADSET_AUTOPLAY,
    label = "Headset autoplay",
    subtitle = "Begin playback automatically when headphones connect.",
    labelRes = R.string.settings_audio_headset_autoplay_label,
    subtitleRes = R.string.settings_audio_headset_autoplay_subtitle,
    keywords = listOf("headphones", "bluetooth", "autoplay", "connect"),
    icon = Icons.Outlined.Headphones,
    section = Section.Audio,
    group = Groups.Playback,
    kind = RowKind.Toggle,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_AUDIO, "Playback", "Headset autoplay"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_REWIND_BEFORE_SKIP,
    label = "Rewind before skipping back",
    subtitle = "Tap previous within a few seconds rewinds; otherwise jumps to the previous track.",
    labelRes = R.string.settings_audio_rewind_before_skip_label,
    subtitleRes = R.string.settings_audio_rewind_before_skip_subtitle,
    keywords = listOf("previous", "rewind", "skip"),
    icon = Icons.Outlined.FastRewind,
    section = Section.Audio,
    group = Groups.Playback,
    kind = RowKind.Toggle,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_AUDIO, "Playback", "Rewind before skipping back"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_PAUSE_ON_REPEAT,
    label = "Pause on repeat",
    subtitle = "Pause at the end of the first play instead of looping the same track.",
    labelRes = R.string.settings_audio_pause_on_repeat_label,
    subtitleRes = R.string.settings_audio_pause_on_repeat_subtitle,
    keywords = listOf("repeat", "loop", "pause"),
    icon = Icons.Outlined.PauseCircle,
    section = Section.Audio,
    group = Groups.Playback,
    kind = RowKind.Toggle,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_AUDIO, "Playback", "Pause on repeat"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_REMEMBER_PAUSE,
    label = "Remember pause",
    subtitle = "Restore the paused position on relaunch.",
    labelRes = R.string.settings_audio_remember_pause_label,
    subtitleRes = R.string.settings_audio_remember_pause_subtitle,
    keywords = listOf("resume", "position", "pause"),
    icon = Icons.Outlined.Pause,
    section = Section.Audio,
    group = Groups.Playback,
    kind = RowKind.Toggle,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_AUDIO, "Playback", "Remember pause"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_REPLAYGAIN_STRATEGY,
    label = "ReplayGain strategy",
    subtitle = null,
    labelRes = R.string.settings_audio_replaygain_strategy_label,
    subtitleRes = null,
    keywords = listOf("replaygain", "normalization", "track", "album", "smart"),
    icon = Icons.Outlined.GraphicEq,
    section = Section.Audio,
    group = Groups.VolumeNormalization,
    kind = RowKind.Picker,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_AUDIO, "Volume normalization", "ReplayGain strategy"),
  ),
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_REPLAYGAIN_PREAMP,
    label = "ReplayGain pre-amp",
    subtitle = null,
    labelRes = R.string.settings_audio_replaygain_preamp_label,
    subtitleRes = null,
    keywords = listOf("replaygain", "preamp", "volume", "boost", "gain", "slider"),
    icon = Icons.Outlined.Tune,
    section = Section.Audio,
    group = Groups.VolumeNormalization,
    kind = RowKind.Picker,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_AUDIO, "Volume normalization", "ReplayGain pre-amp"),
  ),
  // Phase H.3 — sleep timer. RowKind.OpenDialog so the row hosts the
  // presets dialog inline instead of pushing a sub-page.
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_SLEEP_TIMER,
    label = "Sleep timer",
    subtitle = "Pause playback after a delay.",
    labelRes = R.string.settings_audio_sleep_timer_label,
    subtitleRes = R.string.settings_audio_sleep_timer_subtitle,
    keywords = listOf("sleep", "timer", "pause", "bedtime", "off"),
    icon = Icons.Outlined.Bedtime,
    section = Section.Audio,
    group = Groups.Playback,
    kind = RowKind.OpenDialog,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_AUDIO, "Playback", "Sleep timer"),
  ),
  // Phase H.4 — System equalizer hand-off. Tapping fires
  // ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL on the active audio
  // session; if no app handles it the snackbar falls back.
  SettingsCatalogEntry(
    id = SettingsCatalog.ID_SYSTEM_EQUALIZER,
    label = "System equalizer",
    subtitle = "Open the system audio effect panel.",
    labelRes = R.string.settings_audio_system_equalizer_label,
    subtitleRes = R.string.settings_audio_system_equalizer_subtitle,
    keywords = listOf("equalizer", "eq", "audio effects", "system"),
    icon = Icons.Outlined.Equalizer,
    section = Section.Audio,
    group = Groups.VolumeNormalization,
    kind = RowKind.Action,
    destination = SettingsAudio,
    breadcrumb = listOf(SECTION_AUDIO, "Volume normalization", "System equalizer"),
  ),
)
