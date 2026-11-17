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

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        runCatching { context.getSharedPreferences("crash_state", Context.MODE_PRIVATE).edit().putLong("shownExit", System.currentTimeMillis()).apply() }
    }

    // ---- breadcrumbs: written synchronously, so they survive a native crash or a kill of the process

    /** Marks where an export is (stage, progress, memory). Read after the next launch if the process died. */
    fun crumb(context: Context, text: String) {
        runCatching {
            val rt = Runtime.getRuntime()
            val mem = "heap ${(rt.totalMemory() - rt.freeMemory()) shr 20}/${rt.maxMemory() shr 20} MB"
            context.getSharedPreferences("crash_state", Context.MODE_PRIVATE).edit().putString("crumb", "$text · $mem").commit()
        }
    }

    fun lastCrumb(context: Context): String? = runCatching { context.getSharedPreferences("crash_state", Context.MODE_PRIVATE).getString("crumb", null) }.getOrNull()

    /**
     * Why the previous run of the app ended abnormally (Android 11+): crash, native crash, ANR, low
     * memory, killed by the system… — including the head of the native crash report when there is one.
     */
    fun previousExit(context: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            val shown = context.getSharedPreferences("crash_state", Context.MODE_PRIVATE).getLong("shownExit", 0L)
            val bad = setOf(
                android.app.ApplicationExitInfo.REASON_CRASH, android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                android.app.ApplicationExitInfo.REASON_ANR, android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
                android.app.ApplicationExitInfo.REASON_SIGNALED, android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                android.app.ApplicationExitInfo.REASON_OTHER, android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
            )
            val e = am.getHistoricalProcessExitReasons(context.packageName, 0, 5).firstOrNull { it.reason in bad && it.timestamp > shown } ?: return null
            val name = when (e.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
                android.app.ApplicationExitInfo.REASON_ANR -> "ANR (not responding)"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (killed)"
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
                else -> "reason ${e.reason}"
            }
            val sb = StringBuilder("Previous exit: $name · status ${e.status} · ${e.description ?: "-"}\nrss ${e.rss shr 10} MB · pss ${e.pss shr 10} MB · importance ${e.importance}")
            if (e.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE) {
                e.traceInputStream?.bufferedReader()?.use { r -> sb.append("\n--- native report ---\n").append(r.readText().take(2500)) }
            }
            sb.toString()
        }.getOrNull()
    }

    /** Everything worth showing after an abnormal end: Java crash report, exit reason, last export breadcrumb. */
    fun pending(context: Context): String? {
        val parts = listOfNotNull(read(context), previousExit(context))
        if (parts.isEmpty()) return null
        val crumb = lastCrumb(context)
        return (parts + listOfNotNull(crumb?.let { "Last export step: $it" })).joinToString("\n\n")
    }
}
