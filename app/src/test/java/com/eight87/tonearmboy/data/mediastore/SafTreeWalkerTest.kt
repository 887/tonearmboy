package com.eight87.tonearmboy.data.mediastore

import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.FakeDocumentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D.9d.1 — drive [SafTreeWalker.walkRoot] against a hand-built tree of
 * fake [DocumentFile] nodes. Confirms three properties:
 *
 *   - audio extensions are recognised case-insensitively (mp3, flac,
 *     ogg, opus, m4a, wav)
 *   - non-audio leaves are skipped
 *   - the result is sorted by name with `String.CASE_INSENSITIVE_ORDER`
 *     so the worker emits files in deterministic order independent of
 *     the underlying DocumentsProvider's native ordering
 */
class SafTreeWalkerTest {

  @Test
  fun walks_recursively_and_keeps_only_audio_extensions() {
    val root = dir(
      "Music",
      file("track1.mp3"),
      file("track2.FLAC"),
      file("readme.txt"),
      file("cover.jpg"),
      dir(
        "Album",
        file("01.opus"),
        file("02.OGG"),
        file("notes.md"),
      ),
      dir(
        "Empty",
      ),
    )
    val out = SafTreeWalker.walkRoot(root).map { it.name }
    // Expect mp3, flac, opus, ogg — sorted case-insensitively by name.
    assertEquals(listOf("01.opus", "02.OGG", "track1.mp3", "track2.FLAC"), out)
  }

  @Test
  fun handles_all_six_extensions() {
    val root = dir(
      "All",
      file("a.mp3"), file("b.flac"), file("c.ogg"),
      file("d.opus"), file("e.m4a"), file("f.wav"),
      // Each not-audio MUST be filtered.
      file("g.aac"), file("h.wma"), file("i.dsf"),
    )
    val out = SafTreeWalker.walkRoot(root).map { it.name }
    assertEquals(
      listOf("a.mp3", "b.flac", "c.ogg", "d.opus", "e.m4a", "f.wav"),
      out,
    )
  }

  @Test
  fun returns_empty_list_for_empty_tree() {
    val root = dir("Empty")
    assertEquals(emptyList<String>(), SafTreeWalker.walkRoot(root).map { it.name })
  }

  @Test
  fun docId_to_path_prefix_handles_primary_volume() {
    assertEquals(
      "/storage/emulated/0/Music",
      SafScopeMapping.docIdToPathPrefix("primary:Music"),
    )
    assertEquals(
      "/storage/emulated/0",
      SafScopeMapping.docIdToPathPrefix("primary:"),
    )
    assertEquals(
      "/storage/emulated/0",
      SafScopeMapping.docIdToPathPrefix("primary"),
    )
  }

  @Test
  fun docId_to_path_prefix_handles_sd_card_volume() {
    assertEquals(
      "/storage/1A2B-3C4D/Music",
      SafScopeMapping.docIdToPathPrefix("1A2B-3C4D:Music"),
    )
    assertEquals(
      "/storage/1A2B-3C4D/Music/Albums",
      SafScopeMapping.docIdToPathPrefix("1A2B-3C4D:Music/Albums"),
    )
  }

  @Test
  fun audio_extensions_set_is_six_entries() {
    assertEquals(6, SafTreeWalker.AUDIO_EXTENSIONS.size)
    assertEquals(
      setOf("mp3", "flac", "ogg", "opus", "m4a", "wav"),
      SafTreeWalker.AUDIO_EXTENSIONS,
    )
  }

  // -- Tiny in-memory DocumentFile fake --------------------------------------

  private fun file(name: String): DocumentFile = FakeDocumentFile(name, isDir = false)
  private fun dir(name: String, vararg children: DocumentFile): DocumentFile =
    FakeDocumentFile(name, isDir = true, children = children.toMutableList())

  @Test
  fun null_root_is_tolerated() {
    // Defensive: null filename leaf returns no audio entry.
    val root = dir("nameless", file(""), file(".hidden"))
    val out = SafTreeWalker.walkRoot(root)
    assertNull(out.firstOrNull())
    assertEquals(emptyList<DocumentFile>(), out)
  }
}
