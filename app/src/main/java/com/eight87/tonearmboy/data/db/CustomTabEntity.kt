package com.eight87.tonearmboy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * D.18.1 — A user-defined library tab keyed on a content kind plus a
 * filter expression. The criteria are stored as JSON encoded by
 * [com.eight87.tonearmboy.data.FilterCriteria]; we keep them as a single
 * column rather than splitting per-predicate so the schema doesn't
 * fan out every time a new predicate is added.
 *
 * `position` is the user's ordering relative to other custom tabs;
 * built-in tabs have their own ordering in `SettingsRepository.libraryTabs`
 * and the rail concatenates the two lists at render time.
 */
@Entity(tableName = "custom_tabs")
data class CustomTabEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val position: Int,
  val contentType: CustomTabContentType,
  val criteriaJson: String,
)

/** D.18.1 — what a custom tab renders. */
enum class CustomTabContentType { SONGS, ALBUMS, ARTISTS, GENRES }
