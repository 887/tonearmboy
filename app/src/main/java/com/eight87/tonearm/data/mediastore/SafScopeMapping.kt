package com.eight87.tonearm.data.mediastore

import android.net.Uri
import android.provider.DocumentsContract

/**
 * D.9d.1 — map a persisted SAF tree URI to the filesystem path prefix
 * MediaStore stores in its `DATA` column, so the scanner can filter
 * rows to those that live under one of the user-configured directories.
 *
 * The standard `ExternalStorageProvider` encodes a tree id as
 * `<volume>:<relative-path>`. The `primary` volume maps to
 * `/storage/emulated/0`; any other volume id (for SD cards / USB OTG)
 * maps to `/storage/<volume>`. A null is returned when the URI does
 * not come from a recognised provider — we surface those tracks via
 * the SAF walk regardless, but they cannot be matched against the
 * MediaStore index.
 */
object SafScopeMapping {

  /** Translate a tree URI to a `/storage/...` filesystem prefix or null. */
  fun treeUriToPathPrefix(uri: Uri): String? {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
      .getOrNull() ?: return null
    return docIdToPathPrefix(docId)
  }

  /** Pure helper: map a tree document id like `primary:Music` to a path. */
  internal fun docIdToPathPrefix(docId: String): String? {
    val sep = docId.indexOf(':')
    val volume: String
    val rel: String
    if (sep < 0) {
      volume = docId
      rel = ""
    } else {
      volume = docId.substring(0, sep)
      rel = docId.substring(sep + 1)
    }
    val base = if (volume.equals("primary", ignoreCase = true)) {
      "/storage/emulated/0"
    } else {
      "/storage/$volume"
    }
    val relTrimmed = rel.trim('/')
    return if (relTrimmed.isEmpty()) base else "$base/$relTrimmed"
  }
}
