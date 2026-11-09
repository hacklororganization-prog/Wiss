package studio.pyreels.core

import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Scene
import studio.pyreels.core.quality.QualityChecker
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.ScenarioValidator
import studio.pyreels.core.validate.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamplesTest {
    @Test
    fun allSamplesAreValidAndFitTheTarget() {
        for (f in Resources.SAMPLE_FILES) {
            val r = ScenarioValidator.parse(Resources.text(f))
            assertTrue(r.ok, "$f: " + r.problems.joinToString("\n") { it.render() })
            assertTrue(r.problems.none { it.severity == Severity.WARNING }, "$f warnings: " + r.warnings.joinToString("\n") { it.render() })
            val s = r.scenarios.single()
            val tl = Timeline.build(s)
            val engage = tl.scenes.first { it.scene is Scene.EngageMoment }
            println("$f total=${tl.totalMs} reachable=${tl.fit.reachable} engage=${engage.startMs} scenes=${s.scenes.size}")
            assertTrue(tl.fit.reachable, "$f does not fit 90 s: ${tl.totalMs}")
            assertTrue(tl.totalMs in 81_000..126_000, "$f total ${tl.totalMs}")
            assertTrue(engage.startMs in Limits.ENGAGE_WINDOW_START_MS..Limits.ENGAGE_WINDOW_END_MS, "$f engage at ${engage.startMs}")
            val q = QualityChecker.check(s, tl)
            println("  quality=${q.score} " + q.problems.joinToString(" | ") { it.render() })
            assertTrue(q.canExport, "$f blocking: ${q.blocking}")
            assertTrue(q.score >= 80, "$f quality ${q.score}: ${q.problems.map { it.render() }}")
        }
    }

    @Test
    fun weeklyBatchIsValid() {
        val r = ScenarioValidator.parse(Resources.text(Resources.WEEKLY_SAMPLE))
        assertTrue(r.ok, r.problems.joinToString("\n") { it.render() })
        assertTrue(r.isBatch)
        assertEquals(7, r.scenarios.size)
        r.scenarios.forEach { s ->
            val tl = Timeline.build(s)
            assertTrue(tl.totalMs in 81_000..126_000, "${s.meta.lesson.id} total ${tl.totalMs}")
        }
    }

    @Test
    fun samplesRoundTripThroughEncoder() {
        for (f in Resources.SAMPLE_FILES) {
            val s = ScenarioValidator.parse(Resources.text(f)).scenarios.single()
            val again = ScenarioValidator.parse(ScenarioValidator.encode(s))
            assertTrue(again.ok, again.problems.toString())
            assertEquals(s, again.scenarios.single())
        }
    }
}
