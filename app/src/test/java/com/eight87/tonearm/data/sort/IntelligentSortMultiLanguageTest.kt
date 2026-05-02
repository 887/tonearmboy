package com.eight87.tonearm.data.sort

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D.9c.2 — leading-article stripping for the languages listed in
 * `docs/plans/main.md`. The display string is unchanged; the test
 * targets the **sort key** path used by `sortNameKey` in
 * `LibraryScreen.kt`.
 */
class IntelligentSortMultiLanguageTest {

  private fun strip(name: String) = IntelligentSort.stripLeadingArticle(name)

  // English -----------------------------------------------------------

  @Test
  fun english_the() {
    assertEquals("Strokes", strip("The Strokes"))
  }

  @Test
  fun english_a() {
    assertEquals("Perfect Circle", strip("A Perfect Circle"))
  }

  @Test
  fun english_an() {
    assertEquals("Innocent Man", strip("An Innocent Man"))
  }

  @Test
  fun english_an_American_in_Paris() {
    assertEquals("American In Paris", strip("An American In Paris"))
  }

  // French ------------------------------------------------------------

  @Test
  fun french_le() {
    assertEquals("Roi Soleil", strip("Le Roi Soleil"))
  }

  @Test
  fun french_la() {
    assertEquals("Vie en Rose", strip("La Vie en Rose"))
  }

  @Test
  fun french_les() {
    assertEquals("Misérables", strip("Les Misérables"))
  }

  @Test
  fun french_l_apostrophe_amour() {
    assertEquals("amour", strip("L'amour"))
  }

  @Test
  fun french_l_apostrophe_capital_estate() {
    assertEquals("Estate", strip("L'Estate"))
  }

  // German ------------------------------------------------------------

  @Test
  fun german_der_schwarze_falke() {
    assertEquals("Schwarze Falke", strip("Der Schwarze Falke"))
  }

  @Test
  fun german_die_walkure() {
    assertEquals("Walküre", strip("Die Walküre"))
  }

  @Test
  fun german_das_lied() {
    assertEquals("Lied von der Erde", strip("Das Lied von der Erde"))
  }

  @Test
  fun german_den_dem_des() {
    assertEquals("Wald", strip("Den Wald"))
    assertEquals("Spiegel", strip("Dem Spiegel"))
    assertEquals("Mondes", strip("Des Mondes"))
  }

  // Spanish -----------------------------------------------------------

  @Test
  fun spanish_el() {
    assertEquals("Cóndor Pasa", strip("El Cóndor Pasa"))
  }

  @Test
  fun spanish_la_los_las() {
    assertEquals("Bamba", strip("La Bamba"))
    assertEquals("Lobos", strip("Los Lobos"))
    assertEquals("Mañanitas", strip("Las Mañanitas"))
  }

  // Italian -----------------------------------------------------------

  @Test
  fun italian_il_lo_la() {
    assertEquals("Postino", strip("Il Postino"))
    assertEquals("Trovatore", strip("Lo Trovatore"))
    assertEquals("Bohème", strip("La Bohème"))
  }

  @Test
  fun italian_i_gli_le() {
    assertEquals("Pooh", strip("I Pooh"))
    assertEquals("Squallor", strip("Gli Squallor"))
    assertEquals("Vibrazioni", strip("Le Vibrazioni"))
  }

  @Test
  fun italian_l_apostrophe() {
    assertEquals("Italiano", strip("L'Italiano"))
  }

  // Dutch -------------------------------------------------------------

  @Test
  fun dutch_de_het() {
    assertEquals("Dijk", strip("De Dijk"))
    assertEquals("Land", strip("Het Land"))
  }

  @Test
  fun dutch_t_apostrophe() {
    assertEquals("Hooft", strip("'t Hooft"))
  }

  // Edge cases --------------------------------------------------------

  @Test
  fun the_the_strips_only_first_article() {
    // The band "The The" — strip the leading "The ", leaving "The".
    assertEquals("The", strip("The The"))
  }

  @Test
  fun bare_article_is_preserved_to_avoid_empty_key() {
    // If stripping would leave an empty string (e.g. just "The"), the
    // function returns the input unchanged.
    assertEquals("The", strip("The"))
    assertEquals("La", strip("La"))
    assertEquals("L'", strip("L'"))
  }

  @Test
  fun leading_whitespace_is_trimmed_first() {
    assertEquals("Strokes", strip("   The Strokes  "))
  }

  @Test
  fun word_starting_with_an_article_token_is_not_stripped() {
    // "Theatre Royal" begins with "The" but the next char is `a`, not
    // whitespace — so it must NOT be stripped.
    assertEquals("Theatre Royal", strip("Theatre Royal"))
    // "Anthropology" begins with "An" but the next char is `t`.
    assertEquals("Anthropology", strip("Anthropology"))
  }

  @Test
  fun letter_apostrophe_articles_match_without_following_space() {
    // `l'amour` (no space) and `l' amour` (space) both strip to "amour".
    assertEquals("amour", strip("l'amour"))
    assertEquals("amour", strip("l' amour"))
  }

  @Test
  fun empty_or_whitespace_returns_empty_string() {
    assertEquals("", strip(""))
    assertEquals("", strip("   "))
  }
}
