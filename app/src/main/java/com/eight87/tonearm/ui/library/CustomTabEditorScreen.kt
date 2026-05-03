package com.eight87.tonearm.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.tonearm.data.FilterCriteria
import com.eight87.tonearm.data.db.CustomTabContentType
import com.eight87.tonearm.data.db.CustomTabEntity

/**
 * D.30.3 — full-screen editor for a custom library tab.
 *
 * Replaces the pre-D.30.3 [CustomTabEditorSheet] (a `ModalBottomSheet`).
 * The same composable handles both intents: pass [existing] to edit,
 * pass null to create. State is held locally until Save fires; back
 * discards.
 *
 * Save lives in the top app bar's right slot so the form scrolls
 * unbothered. Disabled until the name is non-empty.
 *
 * The library-derived option pools — known genres, artists, albums,
 * year bounds — come in via the [universe] argument so the screen
 * stays a pure UI surface (no Flow plumbing here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTabEditorScreen(
  existing: CustomTabEntity?,
  universe: FilterUniverse,
  onBack: () -> Unit,
  onSave: (name: String, contentType: CustomTabContentType, criteria: FilterCriteria) -> Unit,
) {
  val initial: FilterCriteria = remember(existing) {
    existing?.let { FilterCriteria.fromJson(it.criteriaJson) } ?: FilterCriteria()
  }
  var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
  var contentType by remember(existing) {
    mutableStateOf(existing?.contentType ?: CustomTabContentType.SONGS)
  }
  var selectedGenres by remember(existing) { mutableStateOf(initial.genres.toSet()) }
  var selectedArtists by remember(existing) { mutableStateOf(initial.artists.toSet()) }
  var selectedAlbums by remember(existing) { mutableStateOf(initial.albums.toSet()) }
  val yearLowerBound = universe.minYear ?: 1900
  val yearUpperBound = universe.maxYear ?: 2030
  var yearMin by remember(existing) { mutableStateOf(initial.yearMin?.toFloat() ?: yearLowerBound.toFloat()) }
  var yearMax by remember(existing) { mutableStateOf(initial.yearMax?.toFloat() ?: yearUpperBound.toFloat()) }
  var yearActive by remember(existing) { mutableStateOf(initial.yearMin != null || initial.yearMax != null) }
  var dateAddedSel by remember(existing) {
    mutableStateOf(DateAddedOption.fromEpoch(initial.dateAddedAfter))
  }
  var hasArtSel by remember(existing) {
    mutableStateOf(when (initial.hasAlbumArt) {
      null -> HasArtOption.Any
      true -> HasArtOption.Only
      false -> HasArtOption.Without
    })
  }
  var pathContains by remember(existing) { mutableStateOf(initial.pathContains.orEmpty()) }

  val canSave = name.trim().isNotEmpty()

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .semantics { testTag = "custom_tab_editor" },
    topBar = {
      TopAppBar(
        title = { Text(if (existing == null) "New custom tab" else "Edit custom tab") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          TextButton(
            onClick = {
              val criteria = FilterCriteria(
                genres = selectedGenres.toList().sorted(),
                artists = selectedArtists.toList().sorted(),
                albums = selectedAlbums.toList().sorted(),
                yearMin = if (yearActive) yearMin.toInt() else null,
                yearMax = if (yearActive) yearMax.toInt() else null,
                dateAddedAfter = dateAddedSel.toEpochOffset(),
                hasAlbumArt = when (hasArtSel) {
                  HasArtOption.Any -> null
                  HasArtOption.Only -> true
                  HasArtOption.Without -> false
                },
                pathContains = pathContains.takeIf { it.isNotBlank() },
              )
              val trimmed = name.trim()
              if (trimmed.isNotEmpty()) onSave(trimmed, contentType, criteria)
            },
            enabled = canSave,
            modifier = Modifier.semantics { testTag = "editor_save" },
          ) { Text(if (existing == null) "Create" else "Save") }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
    ) {
      item {
        OutlinedTextField(
          value = name,
          onValueChange = { if (it.length <= 32) name = it },
          label = { Text("Name") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .semantics { testTag = "editor_name" },
        )
      }
      item {
        Text(
          "Content",
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        val all = CustomTabContentType.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          all.forEachIndexed { index, ct ->
            SegmentedButton(
              selected = contentType == ct,
              onClick = { contentType = ct },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = all.size),
              modifier = Modifier.semantics { testTag = "editor_ct_${ct.name}" },
            ) { Text(contentTypeLabel(ct)) }
          }
        }
      }
      item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
      item {
        CollapsibleSection(
          title = "Genres",
          summary = if (selectedGenres.isEmpty()) "Any" else "${selectedGenres.size} selected",
          testTag = "section_genres",
        ) {
          MultiCheckList(
            options = universe.genres,
            selected = selectedGenres,
            onToggle = { v ->
              selectedGenres = if (v in selectedGenres) selectedGenres - v else selectedGenres + v
            },
            tagPrefix = "genre",
            initialVisible = Int.MAX_VALUE,
          )
        }
      }
      item {
        CollapsibleSection(
          title = "Artists",
          summary = if (selectedArtists.isEmpty()) "Any" else "${selectedArtists.size} selected",
          testTag = "section_artists",
        ) {
          MultiCheckList(
            options = universe.artists,
            selected = selectedArtists,
            onToggle = { v ->
              selectedArtists = if (v in selectedArtists) selectedArtists - v else selectedArtists + v
            },
            tagPrefix = "artist",
          )
        }
      }
      item {
        CollapsibleSection(
          title = "Albums",
          summary = if (selectedAlbums.isEmpty()) "Any" else "${selectedAlbums.size} selected",
          testTag = "section_albums",
        ) {
          MultiCheckList(
            options = universe.albums,
            selected = selectedAlbums,
            onToggle = { v ->
              selectedAlbums = if (v in selectedAlbums) selectedAlbums - v else selectedAlbums + v
            },
            tagPrefix = "album",
          )
        }
      }
      item {
        CollapsibleSection(
          title = "Year",
          summary = if (yearActive) "${yearMin.toInt()} – ${yearMax.toInt()}" else "Any",
          testTag = "section_year",
        ) {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = yearActive,
                onCheckedChange = { yearActive = it },
                modifier = Modifier.semantics { testTag = "year_active" },
              )
              Text("Restrict by year")
            }
            if (yearActive) {
              RangeSlider(
                value = yearMin..yearMax,
                onValueChange = { range ->
                  yearMin = range.start
                  yearMax = range.endInclusive
                },
                valueRange = yearLowerBound.toFloat()..yearUpperBound.toFloat(),
                modifier = Modifier.fillMaxWidth().semantics { testTag = "year_range" },
              )
              Text(
                "${yearMin.toInt()} – ${yearMax.toInt()}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
      item {
        CollapsibleSection(
          title = "Date added",
          summary = dateAddedLabel(dateAddedSel),
          testTag = "section_date_added",
        ) {
          Column {
            DateAddedOption.entries.forEach { opt ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .selectable(selected = dateAddedSel == opt, onClick = { dateAddedSel = opt })
                  .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(selected = dateAddedSel == opt, onClick = null)
                Text(dateAddedLabel(opt), modifier = Modifier.padding(start = 12.dp))
              }
            }
          }
        }
      }
      item {
        CollapsibleSection(
          title = "Album art",
          summary = hasArtLabel(hasArtSel),
          testTag = "section_has_art",
        ) {
          Column {
            HasArtOption.entries.forEach { opt ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .selectable(selected = hasArtSel == opt, onClick = { hasArtSel = opt })
                  .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(selected = hasArtSel == opt, onClick = null)
                Text(hasArtLabel(opt), modifier = Modifier.padding(start = 12.dp))
              }
            }
          }
        }
      }
      item {
        CollapsibleSection(
          title = "File path contains",
          summary = if (pathContains.isBlank()) "Any" else "\"$pathContains\"",
          testTag = "section_path",
        ) {
          OutlinedTextField(
            value = pathContains,
            onValueChange = { pathContains = it },
            label = { Text("Substring") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { testTag = "path_contains" },
          )
        }
      }
    }
  }
}

/** D.18.2 — universe of options the editor offers, derived from the live library. */
data class FilterUniverse(
  val genres: List<String>,
  val artists: List<String>,
  val albums: List<String>,
  val minYear: Int?,
  val maxYear: Int?,
)

@Composable
private fun CollapsibleSection(
  title: String,
  summary: String,
  testTag: String,
  content: @Composable () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Column(modifier = Modifier.fillMaxWidth().semantics { this.testTag = testTag }) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
        .padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(summary, style = MaterialTheme.typography.bodySmall)
      }
      Icon(
        imageVector = Icons.Filled.ExpandMore,
        contentDescription = if (expanded) "Collapse" else "Expand",
        modifier = Modifier.rotate(if (expanded) 180f else 0f),
      )
    }
    if (expanded) {
      content()
      HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    } else {
      HorizontalDivider()
    }
  }
}

@Composable
private fun MultiCheckList(
  options: List<String>,
  selected: Set<String>,
  onToggle: (String) -> Unit,
  tagPrefix: String,
  initialVisible: Int = 20,
) {
  var showAll by remember { mutableStateOf(false) }
  if (options.isEmpty()) {
    Text(
      "No values yet — scan your library first.",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(vertical = 8.dp),
    )
    return
  }
  val visible = if (showAll || options.size <= initialVisible) options else options.take(initialVisible)
  Column(modifier = Modifier.heightIn(max = 240.dp)) {
    visible.forEach { value ->
      val checked = value in selected
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .selectable(selected = checked, onClick = { onToggle(value) })
          .padding(vertical = 4.dp)
          .semantics { testTag = "${tagPrefix}_${value}" },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(value, modifier = Modifier.padding(start = 8.dp))
      }
    }
    if (!showAll && options.size > initialVisible) {
      TextButton(onClick = { showAll = true }) {
        Text("Show all (${options.size})")
      }
    }
  }
}

internal enum class DateAddedOption {
  Any, Last7Days, Last30Days, LastYear;

  fun toEpochOffset(nowSeconds: Long = System.currentTimeMillis() / 1000): Long? = when (this) {
    Any -> null
    Last7Days -> nowSeconds - 7L * 24 * 3600
    Last30Days -> nowSeconds - 30L * 24 * 3600
    LastYear -> nowSeconds - 365L * 24 * 3600
  }

  companion object {
    fun fromEpoch(epoch: Long?): DateAddedOption {
      if (epoch == null) return Any
      val nowSeconds = System.currentTimeMillis() / 1000
      val daysAgo = (nowSeconds - epoch) / (24 * 3600)
      return when {
        daysAgo <= 8 -> Last7Days
        daysAgo <= 31 -> Last30Days
        daysAgo <= 366 -> LastYear
        else -> Any
      }
    }
  }
}

internal fun dateAddedLabel(opt: DateAddedOption): String = when (opt) {
  DateAddedOption.Any -> "Any"
  DateAddedOption.Last7Days -> "Last 7 days"
  DateAddedOption.Last30Days -> "Last 30 days"
  DateAddedOption.LastYear -> "Last year"
}

internal enum class HasArtOption { Any, Only, Without }

internal fun hasArtLabel(opt: HasArtOption): String = when (opt) {
  HasArtOption.Any -> "Any"
  HasArtOption.Only -> "Only with album art"
  HasArtOption.Without -> "Only without album art"
}

internal fun contentTypeLabel(ct: CustomTabContentType): String = when (ct) {
  CustomTabContentType.SONGS -> "Songs"
  CustomTabContentType.ALBUMS -> "Albums"
  CustomTabContentType.ARTISTS -> "Artists"
  CustomTabContentType.GENRES -> "Genres"
}
