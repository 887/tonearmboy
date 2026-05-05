package com.eight87.tonearmboy.data.albumart

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * album-art Phase B — folder cover-art scanner.
 *
 * Walks the user's persisted SAF tree URIs (the ones configured in
 * Music sources › FilePicker mode) and returns a map of
 * `directoryName → coverUriString` for every directory that contains
 * an image file matching the canonical cover-art conventions.
 *
 * **Why SAF only:** scoped storage on Android 10+ blocks `File()`
 * enumeration of `/storage/emulated/0/...` paths. We already have
 * persistable read grants on the user's music tree URIs (the audio
 * scanner walks those). Re-using the same grants here avoids any
 * new permission ask. **Tradeoff:** users in System mode (no SAF
 * trees configured) get nothing from this scanner — they should fall
 * back to manual "Replace cover" or Phase D's MusicBrainz auto-fetch.
 *
 * **Match priority** (first match per directory wins):
 *
 *   1. `cover.{jpg,jpeg,png,webp}`
 *   2. `folder.{jpg,jpeg,png,webp}`
 *   3. `albumart.{jpg,jpeg,png,webp}`
 *   4. `front.{jpg,jpeg,png,webp}`
 *
 * The directory key is the lower-cased base name of the directory
 * (e.g. `velvet den`). Callers (`LibraryRepository`) match against
 * the parent-dir name of each track's `DATA` path, also lower-cased.
 */
object FolderArtScanner {

  internal val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp")

  /** Filenames (without extension) we look for, in match-priority order. */
  private val COVER_BASENAMES: List<String> = listOf("cover", "folder", "albumart", "front")

  /**
   * Walk every tree URI in [treeUris]; return `dirName → coverUri`
   * for every directory whose listing contains a matching image.
   *
   * Sub-directories are walked recursively; nested album folders are
   * each indexed by their own dir name. The returned URI is opaque
   * — typically a `content://...` SAF document URI ready to be
   * stored in `album_covers.coverUri`.
   */
  fun walk(context: Context, treeUris: Iterable<Uri>): Map<String, String> {
    val out = HashMap<String, String>()
    for (uri in treeUris) {
      val root = runCatching { DocumentFile.fromTreeUri(context, uri) }
        .getOrNull() ?: continue
      walkInto(root, out)
    }
    return out
  }

  private fun walkInto(node: DocumentFile, out: HashMap<String, String>) {
    if (!node.isDirectory) return
    val children = runCatching { node.listFiles() }.getOrNull() ?: return
    val cover = pickCover(children)
    if (cover != null) {
      val dirName = node.name?.lowercase()?.trim()
      if (!dirName.isNullOrBlank() && dirName !in out) {
        out[dirName] = cover.uri.toString()
      }
    }
    for (child in children) {
      if (child.isDirectory) walkInto(child, out)
    }
  }

  private fun pickCover(children: Array<DocumentFile>): DocumentFile? {
    // Index by lowercase basename for O(1) priority lookup.
    val byBase: Map<String, DocumentFile> = children
      .filter { !it.isDirectory && isImage(it) }
      .mapNotNull { f ->
        val name = f.name ?: return@mapNotNull null
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return@mapNotNull null
        val base = name.substring(0, dot).lowercase()
        base to f
      }
      .toMap()
    for (basename in COVER_BASENAMES) {
      byBase[basename]?.let { return it }
    }
    return null
  }

  private fun isImage(node: DocumentFile): Boolean {
    val name = node.name ?: return false
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.length - 1) return false
    val ext = name.substring(dot + 1).lowercase()
    return ext in IMAGE_EXTENSIONS
  }
}
