package com.eight87.tonearmboy.data.mediastore

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * D.9d.1 — walk a list of SAF tree URIs and return every leaf
 * [DocumentFile] whose name has an audio extension.
 *
 * Why SAF instead of plain `File` enumeration: scoped storage on API
 * 30+ blocks an app process from reading raw `/sdcard/<arbitrary>`
 * paths. The user picks a tree once via `ACTION_OPEN_DOCUMENT_TREE`
 * and we use `ContentResolver.takePersistableUriPermission` so the
 * permission survives process death; the tree URI is then walked via
 * [DocumentFile.fromTreeUri] which goes through the system's
 * DocumentsProvider and respects user consent.
 *
 * Trade-off: SAF-rooted scanning is measurably slower than the
 * MediaStore index for system-indexed paths (the provider does a
 * round-trip for each [DocumentFile.listFiles] call), but it is the
 * only way to scan paths MediaStore does not index — SD cards mounted
 * read-only, USB OTG drives, and arbitrary user directories outside
 * `/sdcard/Music`. When sources are configured we use SAF
 * exclusively; when no sources are configured we fall back to the
 * existing MediaStore default.
 */
object SafTreeWalker {

  /** Audio extensions we surface during a SAF walk. Lowercase, no dot. */
  internal val AUDIO_EXTENSIONS: Set<String> =
    setOf("mp3", "flac", "ogg", "opus", "m4a", "wav")

  /**
   * Walk every tree URI in [treeUris] and return audio leaves in
   * stable name-order (case-insensitive). Roots are walked in the
   * iteration order of the input collection.
   *
   * Errors reading any single subtree are swallowed (logged at the
   * caller's discretion) so a transient failure on one volume cannot
   * fail the whole scan.
   */
  fun walk(context: Context, treeUris: Iterable<Uri>): List<DocumentFile> {
    val out = ArrayList<DocumentFile>()
    for (uri in treeUris) {
      val root = runCatching { DocumentFile.fromTreeUri(context, uri) }
        .getOrNull() ?: continue
      walkInto(root, out)
    }
    out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
    return out
  }

  /**
   * Pure variant of [walk] that operates on an already-resolved root
   * `DocumentFile`. Visible for unit-test use with fakes.
   */
  fun walkRoot(root: DocumentFile): List<DocumentFile> {
    val out = ArrayList<DocumentFile>()
    walkInto(root, out)
    out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })
    return out
  }

  private fun walkInto(node: DocumentFile, out: MutableList<DocumentFile>) {
    if (!node.isDirectory) {
      if (isAudio(node)) out += node
      return
    }
    val children = runCatching { node.listFiles() }.getOrNull() ?: return
    for (child in children) {
      if (child.isDirectory) {
        walkInto(child, out)
      } else if (isAudio(child)) {
        out += child
      }
    }
  }

  private fun isAudio(node: DocumentFile): Boolean {
    val name = node.name ?: return false
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.length - 1) return false
    val ext = name.substring(dot + 1).lowercase()
    return ext in AUDIO_EXTENSIONS
  }
}
