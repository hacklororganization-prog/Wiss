package studio.pyreels.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Profile pictures chosen from the gallery: one per language teacher (key = language id such as
 * "python"), one for Ahmed ("ahmed") and one for the group ("group"). Pictures are copied into the
 * app's private storage as small square JPEGs, so they keep working offline and after the original
 * is deleted. The same pictures are used by the preview and the export.
 */
object AvatarStore {
    const val GROUP = "group"
    private const val SIZE = 384

    private val _version = MutableStateFlow(0)
    /** Bumped whenever a picture changes, so previews can be rebuilt. */
    val version: StateFlow<Int> = _version

    private val cache = HashMap<String, Pair<Long, Bitmap>>()

    private fun safe(key: String) = key.lowercase().replace(Regex("[^a-z0-9_+#-]"), "_").replace("+", "p").replace("#", "sharp")
    private fun file(context: Context, key: String) = File(File(context.filesDir, "avatars").apply { mkdirs() }, "${safe(key)}.jpg")

    fun has(context: Context, key: String) = file(context, key).exists()

    /** The picture for [key], or null when none was chosen. Never throws. */
    @Synchronized
    fun load(context: Context, key: String): Bitmap? = try {
        val f = file(context, key)
        if (!f.exists()) { cache.remove(key); null } else {
            val hit = cache[key]
            if (hit != null && hit.first == f.lastModified() && !hit.second.isRecycled) hit.second
            else BitmapFactory.decodeFile(f.path)?.also { cache[key] = f.lastModified() to it }
        }
    } catch (_: Throwable) { null }

    /** Copies the picked image (centre-cropped to a square). Returns false when it cannot be read. */
    fun save(context: Context, key: String, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val src = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (src == null) false else {
            val side = minOf(src.width, src.height)
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val x = (src.width - side) / 2
            val y = (src.height - side) / 2
            Canvas(out).drawBitmap(src, Rect(x, y, x + side, y + side), Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            src.recycle()
            val f = file(context, key)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (f.exists()) f.delete()
            val ok = tmp.renameTo(f)
            synchronized(this) { cache.remove(key) }
            _version.value++
            ok
        }
    } catch (_: Throwable) { false }

    fun remove(context: Context, key: String) {
        runCatching { file(context, key).delete() }
        synchronized(this) { cache.remove(key) }
        _version.value++
    }
}
