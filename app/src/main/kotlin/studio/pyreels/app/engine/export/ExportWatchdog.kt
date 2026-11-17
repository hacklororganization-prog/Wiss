package studio.pyreels.app.engine.export

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import studio.pyreels.app.data.AppDatabase

/**
 * Runs in the app process while a screen is visible. The export runs in its own process, so a
 * crash there never closes the app; this watchdog notices that the export went silent (no
 * heartbeat) and starts it again. The restarted export avoids what the dead one was doing
 * (see [ExportState]), so it converges to a mode that works on this device.
 */
object ExportWatchdog {
    private const val SILENCE_MS = 15_000L

    suspend fun watch(context: Context, db: AppDatabase) {
        val app = context.applicationContext
        while (true) {
            delay(4_000)
            val pending = runCatching { withContext(Dispatchers.IO) { db.jobs().unfinished() } }.getOrDefault(emptyList())
            if (pending.isEmpty()) continue
            val newest = pending.maxOf { it.createdAt }
            if (ExportState.silenceMs(app) > SILENCE_MS && System.currentTimeMillis() - newest > SILENCE_MS) {
                android.util.Log.i("PyReelsExport", "watchdog: export process silent, restarting")
                ExportService.start(app)
                delay(SILENCE_MS) // give the new process time to start beating
            }
        }
    }
}
