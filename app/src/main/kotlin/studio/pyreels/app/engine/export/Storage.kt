package studio.pyreels.app.engine.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/** Output helpers: MediaStore (default) and Storage Access Framework trees. No broad storage permission. */
object Storage {
    const val FOLDER = "PyReelsStudio"

    enum class Kind(val mime: String) { VIDEO("video/mp4"), IMAGE("image/png"), TEXT("text/plain"), SRT("application/x-subrip") }

    /**
     * Saves into the public gallery/Downloads through MediaStore (scoped storage, Android 10+: no
     * storage permission). A half-written entry is removed again if anything fails.
     */
    fun saveToMediaStore(context: Context, file: File, displayName: String, kind: Kind): Uri? {
        val resolver = context.contentResolver
        val (collection, dir) = when (kind) {
            // DCIM: videos and covers appear directly in the phone's gallery
            Kind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DCIM
            Kind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DCIM
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, kind.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        var uri: Uri? = null
        return try {
            uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("cannot open $uri")
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            null
        }
    }

    /** Writes a file into a SAF tree the user picked (Publish Pack "save to folder…"). */
    fun saveToTree(context: Context, tree: Uri, file: File, displayName: String, kind: Kind): Uri? = try {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(context.contentResolver, parent, kind.mime, displayName)
        doc?.also { u -> context.contentResolver.openOutputStream(u)?.use { out -> file.inputStream().use { it.copyTo(out) } } }
    } catch (_: Exception) {
        null
    }

    fun copyToUri(context: Context, file: File, target: Uri): Boolean = try {
        context.contentResolver.openOutputStream(target, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
    } catch (_: Exception) {
        false
    }

    fun exportsDir(context: Context) = File(context.filesDir, "exports").apply { mkdirs() }
}
