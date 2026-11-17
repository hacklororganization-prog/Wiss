package studio.pyreels.app.engine.export

import android.content.Context
import java.io.File
import java.util.Properties

/**
 * Small persistent state shared by the app process and the separate export process (":export").
 * It is a properties file read from disk on every access (SharedPreferences would cache a stale
 * copy in the other process) and written atomically, so it survives a hard crash of either process.
 *
 * - `dirty`: an export job is in progress. Still set when a new job starts = the previous export
 *   process died without finishing (native crash, killed by the system…).
 * - `stage`: where that export was (python, tts, encode, save, pack).
 * - `noPython`, `noVoice`, `encodeSafe` (0–2): what this device must avoid, learned from such deaths.
 *   They are kept for later exports (the incompatibility belongs to the device) until reset.
 */
object ExportState {
    private fun file(c: Context) = File(c.filesDir, "export_state.properties")
    private fun beatFile(c: Context) = File(c.filesDir, "export_heartbeat")

    @Synchronized
    private fun load(c: Context): Properties = Properties().apply {
        runCatching { file(c).takeIf { it.exists() }?.inputStream()?.use { load(it) } }
    }

    @Synchronized
    private fun save(c: Context, p: Properties) {
        runCatching {
            val f = file(c)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { out -> p.store(out, null); out.fd.sync() }
            tmp.renameTo(f)
        }
    }

    private fun edit(c: Context, block: Properties.() -> Unit) { val p = load(c); p.block(); save(c, p) }

    fun stage(c: Context, s: String) = edit(c) { setProperty("stage", s) }

    data class Plan(val noPython: Boolean, val noVoice: Boolean, val encodeSafe: Int, val previousDied: Boolean, val diedAt: String?)

    /**
     * Called when a job starts. If the previous export died, learns from the stage it died in, then
     * marks this export as running. Returns what this export must avoid.
     */
    fun begin(c: Context): Plan {
        val p = load(c)
        val died = p.getProperty("dirty") == "1"
        val at = p.getProperty("stage")
        if (died) {
            when (at) {
                "python" -> p.setProperty("noPython", "1")
                "tts" -> p.setProperty("noVoice", "1")
                else -> p.setProperty("encodeSafe", ((p.getProperty("encodeSafe")?.toIntOrNull() ?: 0) + 1).coerceAtMost(2).toString())
            }
        }
        p.setProperty("dirty", "1")
        p.setProperty("stage", "start")
        save(c, p)
        return plan(p, died, if (died) at else null)
    }

    /** The export ended without killing the process (done, failed with an error, or cancelled). */
    fun end(c: Context) = edit(c) { setProperty("dirty", "0"); setProperty("stage", "idle") }

    fun current(c: Context): Plan = load(c).let { plan(it, false, null) }

    private fun plan(p: Properties, died: Boolean, at: String?) = Plan(
        noPython = p.getProperty("noPython") == "1",
        noVoice = p.getProperty("noVoice") == "1",
        encodeSafe = p.getProperty("encodeSafe")?.toIntOrNull() ?: 0,
        previousDied = died, diedAt = at,
    )

    /** Forget what was learned (Export screen → "reset"). */
    fun reset(c: Context) = edit(c) { remove("noPython"); remove("noVoice"); remove("encodeSafe") }

    // ---- heartbeat: the export process writes it every 2 s; the app restarts a silent export

    fun beat(c: Context) { runCatching { beatFile(c).writeText(System.currentTimeMillis().toString()) } }

    fun clearBeat(c: Context) { runCatching { beatFile(c).delete() } }

    /** Milliseconds since the export process last proved it is alive (Long.MAX_VALUE if never). */
    fun silenceMs(c: Context): Long = runCatching {
        val t = beatFile(c).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: return Long.MAX_VALUE
        System.currentTimeMillis() - t
    }.getOrDefault(Long.MAX_VALUE)
}
