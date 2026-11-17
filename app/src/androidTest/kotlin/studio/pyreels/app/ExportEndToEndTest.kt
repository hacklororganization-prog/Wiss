package studio.pyreels.app

import android.app.ActivityManager
import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.pyreels.app.data.AppDatabase
import studio.pyreels.app.data.ExportJobEntity
import studio.pyreels.app.data.ProjectRepository
import studio.pyreels.app.engine.export.ExportService
import studio.pyreels.app.engine.export.ExportState
import studio.pyreels.core.timeline.Timeline
import javax.inject.Inject

/**
 * The whole export, exactly as on a phone: a job goes through the real ExportService (in its own
 * ":export" process), the MP4 is encoded, verified and saved to the gallery (DCIM/PyReelsStudio),
 * and the saved file is then opened through MediaStore like any gallery app would.
 *
 * The second test kills the export process in the middle of the export (what a native crash or the
 * system would do) and checks that the app is still alive and that the export recovers on its own.
 */
@HiltAndroidTest
class ExportEndToEndTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var db: AppDatabase

    private val context get() = compose.activity.applicationContext
    private val saved = ArrayList<Uri>()

    @Before fun setUp() {
        hilt.inject()
        ExportState.end(context)
        ExportState.reset(context)
    }

    @After fun tearDown() {
        // leave the gallery and the device state clean
        saved.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        ExportState.end(context)
        ExportState.reset(context)
    }

    private suspend fun newJob(): Long {
        val s = TestFixtures.scenario("short")
        val projectId = projects.create(s, null)
        return db.jobs().insert(
            ExportJobEntity(
                projectId = projectId, platform = "tiktok", quality = "standard", publishPack = false, packTreeUri = null,
                coverTimeMs = null, state = ExportJobEntity.QUEUED, createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun waitFor(id: Long, timeoutMs: Long, ok: (ExportJobEntity) -> Boolean): ExportJobEntity? {
        val end = System.currentTimeMillis() + timeoutMs
        var j = db.jobs().get(id)
        var last = ""
        while ((j == null || !ok(j)) && System.currentTimeMillis() < end) {
            delay(500)
            j = db.jobs().get(id)
            val now = "${j?.state} ${j?.progress} ${j?.message}"
            if (now != last) { Log.i("PyReelsTest", "job $id: $now"); last = now }
        }
        return j
    }

    /** Opens the saved video through MediaStore and checks it is a playable 9:16 MP4 with sound in DCIM/PyReelsStudio. */
    private fun assertPlayableInGallery(uriText: String?, expectedMs: Long, width: Int, height: Int) {
        assertNotNull("no output uri", uriText)
        val uri = Uri.parse(uriText)
        saved += uri
        context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE),
            null, null, null,
        ).use { c ->
            assertTrue("not in MediaStore", c != null && c.moveToFirst())
            val name = c!!.getString(0); val path = c.getString(1); val size = c.getLong(2); val mime = c.getString(3)
            Log.i("PyReelsTest", "saved: $name in $path, $size bytes, $mime")
            assertTrue(name, name.endsWith(".mp4"))
            assertTrue(path, path.startsWith("DCIM/PyReelsStudio"))
            assertEquals("video/mp4", mime)
            assertTrue("file too small: $size", size > 100_000)
        }
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            Log.i("PyReelsTest", "metadata: ${w}x$h ${d}ms audio=$audio")
            assertEquals(width, w); assertEquals(height, h)
            assertEquals("yes", audio)
            assertTrue("duration $d vs $expectedMs", kotlin.math.abs(d - expectedMs) <= 300)
            // a real decoded picture from the middle of the video
            assertNotNull("cannot decode a frame", r.getFrameAtTime(d * 1000 / 2))
        } finally {
            r.release()
        }
    }

    @Test
    fun fullExportThroughTheServiceSavesAPlayableMp4() = runBlocking {
        val expected = Timeline.build(TestFixtures.scenario("short")).totalMs
        val id = newJob()
        ExportService.start(context)
        val j = waitFor(id, 10 * 60_000) { it.state == ExportJobEntity.DONE || it.state == ExportJobEntity.FAILED }
        assertEquals("export did not finish: ${j?.message}", ExportJobEntity.DONE, j?.state)
        assertPlayableInGallery(j!!.outputUri, expected, 1080, 1920)
    }

    @Test
    fun exportSurvivesItsProcessBeingKilled() = runBlocking {
        val expected = Timeline.build(TestFixtures.scenario("short")).totalMs
        val id = newJob()
        ExportService.start(context)
        val running = waitFor(id, 3 * 60_000) { it.state == ExportJobEntity.RUNNING && it.progress > 0.05f }
        assertEquals("export never started rendering: ${running?.message}", ExportJobEntity.RUNNING, running?.state)

        // what a native crash in the encoder, or the system, would do
        val am = context.getSystemService(ActivityManager::class.java)
        val export = am.runningAppProcesses.firstOrNull { it.processName.endsWith(":export") }
        assertNotNull("the export does not run in its own process", export)
        assertTrue("the export must not run in the app's process", export!!.pid != android.os.Process.myPid())
        android.os.Process.killProcess(export.pid)
        Log.i("PyReelsTest", "killed export process ${export.pid}")

        // the app is still here, and its watchdog restarts the export in safe mode
        val j = waitFor(id, 12 * 60_000) { it.state == ExportJobEntity.DONE || it.state == ExportJobEntity.FAILED }
        assertTrue("the app process died too", !compose.activity.isDestroyed)
        assertEquals("export did not recover: ${j?.message}", ExportJobEntity.DONE, j?.state)
        assertEquals("the device should now use the safer encoder path", 1, ExportState.current(context).encodeSafe)
        assertPlayableInGallery(j!!.outputUri, expected, 1080, 1920)
    }
}
