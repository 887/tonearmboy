package com.eight87.tonearmboy.data.mediastore

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.9c.1 — Robolectric-runnable JVM tests for [MultiValueSplitter].
 * The splitter is pure Kotlin — no Android stubs needed — but lives
 * inside the test source set the Android Gradle plugin treats as
 * Robolectric-eligible, so it runs under `./gradlew testDebugUnitTest`
 * alongside the rest of the unit tests.
 */
class MultiValueSeparatorTest {

  @Test
  fun split_with_no_separators_returns_singleton() {
    assertEquals(listOf("Jane Doe"), MultiValueSplitter.split("Jane Doe", emptySet()))
  }

  @Test
  fun split_blank_or_null_returns_empty() {
    assertEquals(emptyList<String>(), MultiValueSplitter.split(null, setOf(";")))
    assertEquals(emptyList<String>(), MultiValueSplitter.split("", setOf(";")))
    assertEquals(emptyList<String>(), MultiValueSplitter.split("   ", setOf(";")))
  }

  @Test
  fun split_on_semicolon_trims_each_fragment() {
    assertEquals(
      listOf("Jane Doe", "John Smith"),
      MultiValueSplitter.split("Jane Doe; John Smith", setOf(";")),
    )
  }

  @Test
  fun split_on_slash_works_alongside_semicolon() {
    assertEquals(
      listOf("Jane", "John", "Jill"),
      MultiValueSplitter.split("Jane; John / Jill", setOf(";", "/")),
    )
  }

  @Test
  fun split_drops_empty_fragments() {
    assertEquals(
      listOf("Jane", "John"),
      MultiValueSplitter.split("Jane;;John;", setOf(";")),
    )
  }

  @Test
  fun split_respects_longest_match_for_feat() {
    // `feat.` (5 chars) vs `,` — at the same position the longer wins.
    // "Jane Doe feat. John Smith" → ["Jane Doe", "John Smith"]
    assertEquals(
      listOf("Jane Doe", "John Smith"),
      MultiValueSplitter.split("Jane Doe feat. John Smith", setOf("feat.", ",", ";")),
    )
  }

  @Test
  fun split_does_not_break_the_word_feature() {
    // `feat.` requires the trailing dot to match. `feature` has no dot,
    // so the splitter never fires. (`Featherweight` is the same case
    // since `feat` without the dot is not a registered separator.)
    assertEquals(
      listOf("The Feature Length"),
      MultiValueSplitter.split("The Feature Length", setOf("feat.", "ft.")),
    )
    assertEquals(
      listOf("Featherweight"),
      MultiValueSplitter.split("Featherweight", setOf("feat.", "ft.")),
    )
  }

  @Test
  fun split_letter_boundary_protects_words_starting_with_feat() {
    // "Defeat. Hour" — the `feat.` token appears mid-word. Letter-
    // boundary protection on the leading side keeps it intact.
    assertEquals(
      listOf("Defeat. Hour"),
      MultiValueSplitter.split("Defeat. Hour", setOf("feat.")),
    )
  }

  @Test
  fun split_is_case_insensitive_for_letter_separators() {
    assertEquals(
      listOf("Jane", "John"),
      MultiValueSplitter.split("Jane FT. John", setOf("ft.")),
    )
    assertEquals(
      listOf("Jane", "John"),
      MultiValueSplitter.split("Jane Feat. John", setOf("feat.")),
    )
  }

  @Test
  fun split_by_ampersand_with_multiple_artists() {
    assertEquals(
      listOf("AC", "DC"),
      MultiValueSplitter.split("AC & DC", setOf("&")),
    )
  }

  @Test
  fun split_handles_mixed_separators_in_one_string() {
    assertEquals(
      listOf("A", "B", "C", "D"),
      MultiValueSplitter.split("A; B / C feat. D", setOf(";", "/", "feat.")),
    )
  }

  @Test
  fun split_with_only_separator_returns_empty() {
    assertEquals(emptyList<String>(), MultiValueSplitter.split(";", setOf(";")))
    assertEquals(emptyList<String>(), MultiValueSplitter.split(" ; ; ", setOf(";")))
  }

  @Test
  fun split_falls_back_when_no_separator_matches_inside_string() {
    assertEquals(
      listOf("Sigur Rós"),
      MultiValueSplitter.split("Sigur Rós", setOf(";", "/", ",", "&", "feat.", "ft.")),
    )
  }

  @Test
  fun split_handles_ft_alongside_ampersand() {
    assertEquals(
      listOf("Jane", "John", "Jill"),
      MultiValueSplitter.split("Jane ft. John & Jill", setOf("ft.", "&")),
    )
  }
}
