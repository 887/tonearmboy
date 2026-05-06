package com.eight87.tonearmboy.ui.settings

import com.eight87.tonearmboy.data.settings.Setting
import kotlinx.coroutines.flow.Flow

/**
 * R.B.3 — narrow facet interfaces over [SettingsRepository].
 *
 * Each settings sub-page takes only the facet it actually consumes,
 * not the wholesale repository. This is the I in SOLID applied to a
 * preferences store: the look-and-feel screen reads/writes theme
 * keys; the audio screen reads/writes playback keys; toggling theme
 * no longer pulls the audio screen's state through any shared
 * snapshot.
 *
 * `SettingsRepository` implements every facet. The composition root
 * (`AppGraph`) hands the appropriate facet to each consumer. The
 * facets compose with R.B.5's "every consumer reads its own narrow
 * `Flow<T>`" approach: each facet member is a [Setting] handle (the
 * common Flow-read + suspending-write pair) per key it owns.
 */

/** R.B.3 — Look-and-feel sub-page surface. */
interface ThemeSettings {
  val theme: Setting<ThemePreference>
  val baseTheme: Setting<BaseTheme>
  val albumArtTintEnabled: Setting<Boolean>
  /**
   * Custom chrome tint colour. Stored as a 24-bit RGB packed `Long`
   * (0..0xFFFFFF), or `0` for "unset" (fall back to album-art tint
   * when [albumArtTintEnabled], or no tint otherwise). When non-zero,
   * the value overrides the album-art-derived tint regardless of the
   * `albumArtTintEnabled` toggle — picking a colour is the explicit
   * "I want this colour" gesture.
   */
  val customChromeTint: Setting<Long>
}

/**
 * R.B.3 — Audio sub-page surface plus the playback-action shortcuts
 * the personalise screen surfaces (custom bar action, notification
 * action, play-from sources). All keys here can change behaviour of
 * the playback session or its UI affordances.
 */
interface PlaybackSettings {
  val pauseOnRepeat: Setting<Boolean>
  val customBarAction: Setting<CustomBarAction>
  val customNotificationAction: Setting<CustomNotificationAction>
  val playFromLibrary: Setting<PlayFromLibrary>
  val playFromItemDetails: Setting<PlayFromItemDetails>
  val replayGainStrategy: Setting<ReplayGainStrategy>
  val replayGainPreampDb: Setting<Float>
}

/**
 * R.B.3 — Content / scanning sub-page surface. Anything that affects
 * what the library shows or how it's loaded.
 */
interface LibrarySettings {
  val automaticReloading: Setting<Boolean>
  val intelligentSorting: Setting<Boolean>
  val hideCollaborators: Setting<Boolean>
  val forceSquareCovers: Setting<Boolean>
  val albumCoversMode: Setting<AlbumCoversMode>
  val multiValueSeparators: Setting<Set<MultiValueSeparator>>
  /** album-art Phase D — toggle the MusicBrainz auto-fetch worker. */
  val autoDiscoverAlbumArt: Setting<Boolean>
  /** album-art Phase B — toggle the SAF folder cover-art scanner. */
  val scanFoldersForCoverArt: Setting<Boolean>
}

/**
 * R.B.3 — Music-sources dialog surface. Adds / removes individual
 * SAF tree URIs are convenience helpers around the URI set so dialog
 * code doesn't need to rebuild the set itself.
 */
interface MusicSourcesSettings {
  val musicSourceMode: Setting<MusicSourceMode>
  val musicSourceUris: Setting<Set<String>>
  suspend fun addMusicSourceUri(uri: String)
  suspend fun removeMusicSourceUri(uri: String)
  /**
   * D.17.3.6 — first-launch normalisation hook. Idempotent; only
   * writes [MusicSourceMode.Default] when the key is absent.
   */
  suspend fun firstLaunchInitialise()
}

/**
 * R.B.3 — Tab-layout surface (Personalise screen + the per-tab view
 * mode toggle in the Library top bar + the custom-tab content
 * surface). Per-tab and per-custom-tab handles are factory functions
 * so the consumer binds to the specific tab / id it cares about.
 *
 * The factories carry the `Setting` suffix to make room for the
 * legacy Flow-returning factories (`tabSort(tab): Flow<TabSort>`,
 * `viewModeFor(tab)`, `customTabViewMode(id, default)`) that R.B.4
 * still has callers for — both sets of factories coexist on the
 * impl until the migration completes.
 */
interface TabLayoutSettings {
  val libraryTabs: Setting<List<LibraryTab>>
  fun tabSortSetting(tab: LibraryTab): Setting<TabSort>
  fun viewModeSetting(tab: LibraryTab): Setting<ViewMode>
  fun customTabViewModeSetting(id: Long, default: ViewMode): Setting<ViewMode>
  /**
   * Whole-map view-mode projection used by the library scaffold to
   * read every tab's mode from one subscription. Backed by raw store
   * access; the per-tab fallback already lives in [viewModeSetting].
   */
  val viewModes: Flow<Map<LibraryTab, ViewMode>>
}
