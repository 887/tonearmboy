package com.eight87.tonearmboy.ui.library

import androidx.compose.runtime.Composable
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
  val label: String

  /** Subtitle shown under [label] in the "Add filter" picker sheet. */
  val addSubtitle: String

  /** True when this UI renders the given [condition] (type-class matcher). */
  fun matches(condition: FilterCondition): Boolean

  /** Construct a neutral default — used when the user picks this kind from the add sheet. */
  fun defaultInstance(): FilterCondition

  /** One-line summary shown under [label] in the conditions list. */
  fun summary(condition: FilterCondition): String

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
  override val label: String = "Genre"
  override val addSubtitle: String = "Match selected genres"
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.GenreIn
  override fun defaultInstance(): FilterCondition = FilterCondition.GenreIn(emptyList())
  override fun summary(condition: FilterCondition): String {
    val c = condition as FilterCondition.GenreIn
    return if (c.values.isEmpty()) "Any" else c.values.joinToString(", ")
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
  override val label: String = "Artist"
  override val addSubtitle: String = "Match selected artists"
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.ArtistIn
  override fun defaultInstance(): FilterCondition = FilterCondition.ArtistIn(emptyList())
  override fun summary(condition: FilterCondition): String {
    val c = condition as FilterCondition.ArtistIn
    return if (c.values.isEmpty()) "Any" else c.values.joinToString(", ")
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
  override val label: String = "Album"
  override val addSubtitle: String = "Match selected albums"
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.AlbumIn
  override fun defaultInstance(): FilterCondition = FilterCondition.AlbumIn(emptyList())
  override fun summary(condition: FilterCondition): String {
    val c = condition as FilterCondition.AlbumIn
    return if (c.values.isEmpty()) "Any" else c.values.joinToString(", ")
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
  override val label: String = "Year range"
  override val addSubtitle: String = "Limit by release year"
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.YearBetween
  override fun defaultInstance(): FilterCondition = FilterCondition.YearBetween()
  override fun summary(condition: FilterCondition): String {
    val c = condition as FilterCondition.YearBetween
    return when {
      c.min == null && c.max == null -> "Any"
      c.min != null && c.max != null -> "${c.min} – ${c.max}"
      c.min != null -> "from ${c.min}"
      else -> "up to ${c.max}"
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
  override val label: String = "Date added"
  override val addSubtitle: String = "Limit by when added to library"
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.DateAddedBetween
  override fun defaultInstance(): FilterCondition = FilterCondition.DateAddedBetween()
  override fun summary(condition: FilterCondition): String {
    val c = condition as FilterCondition.DateAddedBetween
    return when {
      c.afterEpochSeconds == null && c.beforeEpochSeconds == null -> "Any"
      c.afterEpochSeconds != null && c.beforeEpochSeconds != null ->
        "${formatEpochDay(c.afterEpochSeconds)} – ${formatEpochDay(c.beforeEpochSeconds)}"
      c.afterEpochSeconds != null -> "since ${formatEpochDay(c.afterEpochSeconds)}"
      else -> "until ${formatEpochDay(c.beforeEpochSeconds!!)}"
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
  override val label: String = "Album art"
  override val addSubtitle: String = "Only tracks with / without art"
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.HasAlbumArt
  override fun defaultInstance(): FilterCondition = FilterCondition.HasAlbumArt(true)
  override fun summary(condition: FilterCondition): String {
    val c = condition as FilterCondition.HasAlbumArt
    return if (c.value) "Only with album art" else "Only without album art"
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
  override val label: String = "File path contains"
  override val addSubtitle: String = "Substring match on the file path"
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.PathContains
  override fun defaultInstance(): FilterCondition = FilterCondition.PathContains("")
  override fun summary(condition: FilterCondition): String {
    val c = condition as FilterCondition.PathContains
    return if (c.needle.isBlank()) "Any" else "\"${c.needle}\""
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
      label = "Path contains",
      tag = "path_contains",
      onChange = { onChange(FilterCondition.PathContains(it)) },
    )
  }
}

internal object TitleContainsUi : ConditionUi {
  override val label: String = "Title contains"
  override val addSubtitle: String = "Substring match on title / artist / album"
  override fun matches(condition: FilterCondition): Boolean = condition is FilterCondition.TitleContains
  override fun defaultInstance(): FilterCondition = FilterCondition.TitleContains("")
  override fun summary(condition: FilterCondition): String {
    val c = condition as FilterCondition.TitleContains
    return if (c.needle.isBlank()) "Any" else "\"${c.needle}\""
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
      label = "Title contains",
      tag = "title_contains",
      onChange = { onChange(FilterCondition.TitleContains(it)) },
    )
  }
}

// formatEpochDay lives in `CustomTabEditorScreen.kt` next to DateAddedEditor.
