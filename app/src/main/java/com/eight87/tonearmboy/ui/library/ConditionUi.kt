package com.eight87.tonearmboy.ui.library

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.eight87.tonearmboy.R
import com.eight87.tonearmboy.data.FilterCondition

/**
 * R.F.2 — per-variant UI registry for [FilterCondition]. (UI-F10.)
 *
 * Each [FilterCondition] variant pairs with one [ConditionUi] singleton
 * that knows how to (a) label it in the conditions list and the
 * "Add filter" sheet, (b) render a one-line summary, (c) construct a
 * neutral default instance for the add flow, (d) render the inline
 * editor.
 *
 * The editor screen iterates [ConditionUiRegistry.all] for the
 * "Add filter" sheet and looks up the right [ConditionUi] via
 * [ConditionUiRegistry.uiFor] when rendering an existing condition.
 * **OCP**: adding a new [FilterCondition] variant is a new
 * [ConditionUi] object + one entry in `all` — the editor screen
 * never changes.
 */
internal interface ConditionUi {
  /** Display name (also used as the type label in the conditions list). */
  @get:StringRes
  val labelRes: Int

  /** Subtitle shown under [labelRes] in the "Add filter" picker sheet. */
  @get:StringRes
  val addSubtitleRes: Int

  /** True when this UI renders the given [condition] (type-class matcher). */
  fun matches(condition: FilterCondition): Boolean

  /** Construct a neutral default — used when the user picks this kind from the add sheet. */
  fun defaultInstance(): FilterCondition

  /**
   * One-line summary shown under [labelRes] in the conditions list.
   *
   * Takes a [Context] so each variant can resolve its translated
   * fragments (e.g. "Any", "from %1$d") via [Context.getString].
   */
  fun summary(condition: FilterCondition, context: Context): String

  /** Inline editor body, expanded under the row when the user taps it. */
  @Composable
  fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  )
}

internal object ConditionUiRegistry {
  /** Render order in the "Add filter" sheet matches this list. */
  val all: List<ConditionUi> = listOf(
    GenreInUi,
    ArtistInUi,
    AlbumInUi,
    YearBetweenUi,
    DateAddedBetweenUi,
    HasAlbumArtUi,
    TitleContainsUi,
    PathContainsUi,
  )

  fun uiFor(condition: FilterCondition): ConditionUi =
    all.first { it.matches(condition) }
}

// -- Per-variant UIs ---------------------------------------------------

internal object GenreInUi : ConditionUi {
  override val labelRes: Int = R.string.library_filter_genre_label
  override val addSubtitleRes: Int = R.string.library_filter_genre_add_subtitle
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.GenreIn
  override fun defaultInstance(): FilterCondition = FilterCondition.GenreIn(emptyList())
  override fun summary(condition: FilterCondition, context: Context): String {
    val c = condition as FilterCondition.GenreIn
    return if (c.values.isEmpty()) context.getString(R.string.library_filter_summary_any)
    else c.values.joinToString(", ")
  }
  @Composable
  override fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  ) {
    val c = condition as FilterCondition.GenreIn
    MultiCheckList(
      options = universe.genres,
      selected = c.values.toSet(),
      onToggle = { v ->
        val next = if (v in c.values) c.values - v else c.values + v
        onChange(FilterCondition.GenreIn(next))
      },
      tagPrefix = "genre",
    )
  }
}

internal object ArtistInUi : ConditionUi {
  override val labelRes: Int = R.string.library_filter_artist_label
  override val addSubtitleRes: Int = R.string.library_filter_artist_add_subtitle
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.ArtistIn
  override fun defaultInstance(): FilterCondition = FilterCondition.ArtistIn(emptyList())
  override fun summary(condition: FilterCondition, context: Context): String {
    val c = condition as FilterCondition.ArtistIn
    return if (c.values.isEmpty()) context.getString(R.string.library_filter_summary_any)
    else c.values.joinToString(", ")
  }
  @Composable
  override fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  ) {
    val c = condition as FilterCondition.ArtistIn
    MultiCheckList(
      options = universe.artists,
      selected = c.values.toSet(),
      onToggle = { v ->
        val next = if (v in c.values) c.values - v else c.values + v
        onChange(FilterCondition.ArtistIn(next))
      },
      tagPrefix = "artist",
    )
  }
}

internal object AlbumInUi : ConditionUi {
  override val labelRes: Int = R.string.library_filter_album_label
  override val addSubtitleRes: Int = R.string.library_filter_album_add_subtitle
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.AlbumIn
  override fun defaultInstance(): FilterCondition = FilterCondition.AlbumIn(emptyList())
  override fun summary(condition: FilterCondition, context: Context): String {
    val c = condition as FilterCondition.AlbumIn
    return if (c.values.isEmpty()) context.getString(R.string.library_filter_summary_any)
    else c.values.joinToString(", ")
  }
  @Composable
  override fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  ) {
    val c = condition as FilterCondition.AlbumIn
    MultiCheckList(
      options = universe.albums,
      selected = c.values.toSet(),
      onToggle = { v ->
        val next = if (v in c.values) c.values - v else c.values + v
        onChange(FilterCondition.AlbumIn(next))
      },
      tagPrefix = "album",
    )
  }
}

internal object YearBetweenUi : ConditionUi {
  override val labelRes: Int = R.string.library_filter_year_label
  override val addSubtitleRes: Int = R.string.library_filter_year_add_subtitle
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.YearBetween
  override fun defaultInstance(): FilterCondition = FilterCondition.YearBetween()
  override fun summary(condition: FilterCondition, context: Context): String {
    val c = condition as FilterCondition.YearBetween
    return when {
      c.min == null && c.max == null -> context.getString(R.string.library_filter_summary_any)
      c.min != null && c.max != null ->
        context.getString(R.string.library_filter_summary_year_range, c.min, c.max)
      c.min != null -> context.getString(R.string.library_filter_summary_year_from, c.min)
      else -> context.getString(R.string.library_filter_summary_year_up_to, c.max)
    }
  }
  @Composable
  override fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  ) {
    YearRangeEditor(condition as FilterCondition.YearBetween, universe) { onChange(it) }
  }
}

internal object DateAddedBetweenUi : ConditionUi {
  override val labelRes: Int = R.string.library_filter_date_added_label
  override val addSubtitleRes: Int = R.string.library_filter_date_added_add_subtitle
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.DateAddedBetween
  override fun defaultInstance(): FilterCondition = FilterCondition.DateAddedBetween()
  override fun summary(condition: FilterCondition, context: Context): String {
    val c = condition as FilterCondition.DateAddedBetween
    return when {
      c.afterEpochSeconds == null && c.beforeEpochSeconds == null ->
        context.getString(R.string.library_filter_summary_any)
      c.afterEpochSeconds != null && c.beforeEpochSeconds != null ->
        context.getString(
          R.string.library_filter_summary_date_range,
          formatEpochDay(c.afterEpochSeconds),
          formatEpochDay(c.beforeEpochSeconds),
        )
      c.afterEpochSeconds != null ->
        context.getString(R.string.library_filter_summary_date_since, formatEpochDay(c.afterEpochSeconds))
      else ->
        context.getString(R.string.library_filter_summary_date_until, formatEpochDay(c.beforeEpochSeconds!!))
    }
  }
  @Composable
  override fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  ) {
    DateAddedEditor(condition as FilterCondition.DateAddedBetween) { onChange(it) }
  }
}

internal object HasAlbumArtUi : ConditionUi {
  override val labelRes: Int = R.string.library_filter_album_art_label
  override val addSubtitleRes: Int = R.string.library_filter_album_art_add_subtitle
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.HasAlbumArt
  override fun defaultInstance(): FilterCondition = FilterCondition.HasAlbumArt(true)
  override fun summary(condition: FilterCondition, context: Context): String {
    val c = condition as FilterCondition.HasAlbumArt
    return context.getString(
      if (c.value) R.string.library_filter_summary_album_art_with
      else R.string.library_filter_summary_album_art_without,
    )
  }
  @Composable
  override fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  ) {
    HasAlbumArtEditor(condition as FilterCondition.HasAlbumArt) { onChange(it) }
  }
}

internal object PathContainsUi : ConditionUi {
  override val labelRes: Int = R.string.library_filter_path_label
  override val addSubtitleRes: Int = R.string.library_filter_path_add_subtitle
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.PathContains
  override fun defaultInstance(): FilterCondition = FilterCondition.PathContains("")
  override fun summary(condition: FilterCondition, context: Context): String {
    val c = condition as FilterCondition.PathContains
    return if (c.needle.isBlank()) context.getString(R.string.library_filter_summary_any)
    else context.getString(R.string.library_filter_summary_quoted, c.needle)
  }
  @Composable
  override fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  ) {
    val c = condition as FilterCondition.PathContains
    SubstringEditor(
      value = c.needle,
      label = androidx.compose.ui.res.stringResource(R.string.library_filter_path_field_label),
      tag = "path_contains",
      onChange = { onChange(FilterCondition.PathContains(it)) },
    )
  }
}

internal object TitleContainsUi : ConditionUi {
  override val labelRes: Int = R.string.library_filter_title_label
  override val addSubtitleRes: Int = R.string.library_filter_title_add_subtitle
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.TitleContains
  override fun defaultInstance(): FilterCondition = FilterCondition.TitleContains("")
  override fun summary(condition: FilterCondition, context: Context): String {
    val c = condition as FilterCondition.TitleContains
    return if (c.needle.isBlank()) context.getString(R.string.library_filter_summary_any)
    else context.getString(R.string.library_filter_summary_quoted, c.needle)
  }
  @Composable
  override fun Editor(
    condition: FilterCondition,
    universe: FilterUniverse,
    onChange: (FilterCondition) -> Unit,
  ) {
    val c = condition as FilterCondition.TitleContains
    SubstringEditor(
      value = c.needle,
      label = androidx.compose.ui.res.stringResource(R.string.library_filter_title_field_label),
      tag = "title_contains",
      onChange = { onChange(FilterCondition.TitleContains(it)) },
    )
  }
}

// formatEpochDay lives in `CustomTabEditorScreen.kt` next to DateAddedEditor.
