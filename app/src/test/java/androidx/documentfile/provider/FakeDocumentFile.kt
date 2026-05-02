package androidx.documentfile.provider

import android.net.Uri

/**
 * Test-only fake of [DocumentFile].
 *
 * Lives in `androidx.documentfile.provider` because the
 * `DocumentFile(parent)` constructor is `package-private`. Used by
 * `SafTreeWalkerTest` to drive the walker against a hand-built tree
 * without needing a real `ContentResolver` / `DocumentsProvider`.
 */
class FakeDocumentFile(
  private val nodeName: String,
  private val isDir: Boolean,
  private val children: MutableList<DocumentFile> = mutableListOf(),
) : DocumentFile(/* parent = */ null) {
  override fun createFile(mimeType: String, displayName: String): DocumentFile? = null
  override fun createDirectory(displayName: String): DocumentFile? = null
  override fun getUri(): Uri = Uri.parse("fake:/$nodeName")
  override fun getName(): String? = nodeName.ifEmpty { null }
  override fun getType(): String? = null
  override fun isDirectory(): Boolean = isDir
  override fun isFile(): Boolean = !isDir
  override fun isVirtual(): Boolean = false
  override fun lastModified(): Long = 0L
  override fun length(): Long = 0L
  override fun canRead(): Boolean = true
  override fun canWrite(): Boolean = false
  override fun delete(): Boolean = false
  override fun exists(): Boolean = true
  override fun listFiles(): Array<DocumentFile> = children.toTypedArray()
  override fun renameTo(displayName: String): Boolean = false
}
