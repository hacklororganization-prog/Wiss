package studio.pyreels.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import studio.pyreels.app.engine.PreparedVideo
import studio.pyreels.app.engine.export.VideoExporter
import studio.pyreels.core.Resources
import studio.pyreels.core.model.Limits
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.ScenarioValidator
import java.io.File

/** Encodes real MP4s on the device and asserts resolution, fps, duration and audio/video sync. */
@RunWith(AndroidJUnit4::class)
class ExportIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun video(sample: String): PreparedVideo {
        val s = ScenarioValidator.parse(Resources.text(sample)).scenarios.single()
        return PreparedVideo(s, Timeline.build(s), emptyMap(), emptyList())
    }

    @Test
    fun exportsVerifiedClip() = runBlocking {
        val v = video("samples/02-variables.pyreels.json")
        val out = File(context.cacheDir, "it_clip.mp4")
        // first 8 s (hook + recap + chat): long enough to cover a stage transition, short enough for emulators
        val result = VideoExporter(context).export(v, out, VideoExporter.Options(ranges = listOf(0L until 8_000L))) { }
        assertEquals(Limits.WIDTH, result.width)
        assertEquals(Limits.HEIGHT, result.height)
        assertTrue("duration ${result.durationMs}", kotlin.math.abs(result.durationMs - 8_000) <= 250)
        assertTrue("fps ${result.fps}", result.fps == 0.0 || kotlin.math.abs(result.fps - 30) <= 1.0)
        assertTrue(result.hasAudio)
        assertTrue("sync ${result.problems}", result.problems.none { it.startsWith("audio/video") })
        assertTrue(result.problems.toString(), result.ok)
    }

    @Test
    fun exportsTeaserRanges() = runBlocking {
        val v = video("samples/04-for-loop.pyreels.json")
        val ranges = studio.pyreels.core.publish.PublishPack.teaserWindow(v.timeline)
        val out = File(context.cacheDir, "it_teaser.mp4")
        val r = VideoExporter(context).export(v, out, VideoExporter.Options(ranges = ranges)) { }
        val expected = ranges.sumOf { it.last - it.first + 1 }
        assertTrue("teaser ${r.durationMs} vs $expected", kotlin.math.abs(r.durationMs - expected) <= 300)
        assertTrue(r.hasAudio)
    }
}
