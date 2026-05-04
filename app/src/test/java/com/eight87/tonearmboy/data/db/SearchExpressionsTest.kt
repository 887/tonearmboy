package com.eight87.tonearmboy.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchExpressionsTest {

  @Test fun `single token gets prefix wildcard`() {
    assertEquals("hello*", SearchExpressions.ftsMatch("hello"))
  }

  @Test fun `multi-token keeps head plain and adds wildcard to last`() {
    assertEquals("hello world*", SearchExpressions.ftsMatch("hello world"))
  }

  @Test fun `pure punctuation returns null so callers fall back to LIKE`() {
    assertNull(SearchExpressions.ftsMatch("--*"))
  }

  @Test fun `whitespace input returns null`() {
    assertNull(SearchExpressions.ftsMatch("   "))
  }

  @Test fun `metacharacters are stripped per token`() {
    val out = SearchExpressions.ftsMatch("foo \"bar*\" baz")
    assertNotNull(out)
    // Last token retains *; head tokens retain alphanumerics.
    assertEquals("foo bar baz*", out)
  }

  @Test fun `like pattern escapes metacharacters`() {
    assertEquals("%50\\%off%", SearchExpressions.likePattern("50%off"))
    assertEquals("%a\\_b%", SearchExpressions.likePattern("a_b"))
  }
}
