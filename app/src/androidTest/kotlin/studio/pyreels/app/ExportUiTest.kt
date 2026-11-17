package studio.pyreels.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * The export path through the real UI: sample → editor → export screen → "Export video" starts the
 * foreground service → the progress card appears → cancel works. Guards against a crash on export.
 */
@HiltAndroidTest
class ExportUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var projects: studio.pyreels.app.data.ProjectRepository
    @Inject lateinit var db: studio.pyreels.app.data.AppDatabase

    @Before fun setUp() {
        hilt.inject()
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission("studio.pyreels.app", Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun logcat(): String = runCatching {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("logcat -d -t 1500")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readLines()
            .filter { Regex("pyreels|AndroidRuntime|FATAL|ANR|ForegroundService|am_proc_died|Force finishing|Process .* has died").containsMatchIn(it) }
            .takeLast(60).joinToString("\n")
    }.getOrElse { "(logcat unavailable: $it)" }

    /** On timeout the error contains the whole screen (including the export card's status text). */
    private fun waitFor(text: String, ms: Long = 15_000) {
        try {
            compose.waitUntil(ms) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("waiting for «$text». Screen:\n" + runCatching { compose.onRoot().printToString(40) }.getOrElse { "(no root: $it)" } + "\n\nCrashLog: " + (CrashLog.read(compose.activity) ?: "none") + "\n\nLogcat:\n" + logcat(), e)
        }
    }

    private suspend fun waitJob(timeoutMs: Long, ok: (studio.pyreels.app.data.ExportJobEntity?) -> Boolean): studio.pyreels.app.data.ExportJobEntity? {
        val end = System.currentTimeMillis() + timeoutMs
        var job = db.jobs().latest()
        while (!ok(job) && System.currentTimeMillis() < end) { kotlinx.coroutines.delay(300); job = db.jobs().latest() }
        return job
    }

    /**
     * Export through the real UI. The assertions read the job from the database (not the screen), so
     * they do not depend on the emulator's flaky GPU window: the job must start rendering, survive a few
     * seconds, and stop cleanly when cancelled — without crashing the process.
     */
    @Test
    fun exportStartsAndCancelsWithoutCrash() {
        kotlinx.coroutines.runBlocking { projects.create(TestFixtures.scenario("zig-variables"), null) }
        waitFor("تصدير ونشر")
        compose.onNodeWithText("تصدير ونشر").performClick()
        waitFor("تصدير الفيديو")
        // the button stays disabled until the quality check has finished: wait for it, or the click is a no-op
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("تصدير الفيديو") and androidx.compose.ui.test.isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("تصدير الفيديو").performScrollTo().performClick()
        kotlinx.coroutines.runBlocking {
            val started = waitJob(40_000) { it != null && it.state != studio.pyreels.app.data.ExportJobEntity.QUEUED }
            android.util.Log.i("PyReelsTest", "after start: $started")
            org.junit.Assert.assertNotNull("no export job was created", started)
            org.junit.Assert.assertNotEquals("export failed: ${started!!.message}", studio.pyreels.app.data.ExportJobEntity.FAILED, started.state)
            kotlinx.coroutines.delay(5_000)
            studio.pyreels.app.engine.export.ExportService.cancel(compose.activity)
            val end = waitJob(40_000) { it != null && it.state != studio.pyreels.app.data.ExportJobEntity.RUNNING && it.state != studio.pyreels.app.data.ExportJobEntity.QUEUED }
            android.util.Log.i("PyReelsTest", "after cancel: $end")
            org.junit.Assert.assertTrue("job did not stop after cancel: $end",
                end?.state == studio.pyreels.app.data.ExportJobEntity.CANCELLED || end?.state == studio.pyreels.app.data.ExportJobEntity.DONE)
        }
    }
}
