package studio.pyreels.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline crash report: the last failure is written to a private file and shown on the next
 * launch with a copy button, so a problem on a specific phone can be reported precisely.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, error, "crash on ${thread.name}")
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, error: Throwable, where: String) {
        runCatching {
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file(context).writeText(
                "PyReels Studio ${BuildConfig.VERSION_NAME} · $where · $stamp\n" +
                    "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n" +
                    sw.toString().take(6000),
            )
        }
    }

    fun read(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) { runCatching { file(context).delete() } }
}
