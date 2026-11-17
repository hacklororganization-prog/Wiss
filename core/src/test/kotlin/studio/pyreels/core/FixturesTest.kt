package studio.pyreels.core

import studio.pyreels.core.model.Languages
import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Personas
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.resolved
import studio.pyreels.core.quality.QualityChecker
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.AutoRepair
import studio.pyreels.core.validate.ScenarioValidator
import studio.pyreels.core.validate.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The fixtures are test data only (not shipped). They include languages the app has never heard of. */
class FixturesTest {
    @Test
    fun everyFixtureIsValidAndFitsTheTarget() {
        for (f in Fixtures.ALL) {
            val r = ScenarioValidator.parse(Fixtures.text(f))
            assertTrue(r.ok, "$f: " + r.problems.joinToString("\n") { it.render() })
            assertTrue(r.problems.none { it.severity == Severity.WARNING }, "$f warnings: " + r.warnings.joinToString("\n") { it.render() })
            val s = r.scenarios.single()
            val tl = Timeline.build(s)
            val engage = tl.scenes.first { it.scene is Scene.EngageMoment }
            println("$f total=${tl.totalMs} reachable=${tl.fit.reachable} engage=${engage.startMs} scenes=${s.scenes.size}")
            assertTrue(tl.fit.reachable, "$f does not fit: ${tl.totalMs}")
            assertTrue(tl.totalMs in 81_000..126_000, "$f total ${tl.totalMs}")
            assertTrue(engage.startMs in Limits.ENGAGE_WINDOW_START_MS..Limits.ENGAGE_WINDOW_END_MS, "$f engage at ${engage.startMs}")
            val q = QualityChecker.check(s, tl)
            assertTrue(q.canExport, "$f blocking: ${q.blocking}")
            assertTrue(q.score >= 80, "$f quality ${q.score}: ${q.problems.map { it.render() }}")
        }
    }

    @Test
    fun fixturesRoundTripThroughEncoder() {
        for (f in Fixtures.ALL) {
            val s = Fixtures.scenario(f)
            val again = ScenarioValidator.parse(ScenarioValidator.encode(s))
            assertTrue(again.ok, again.problems.toString())
            assertEquals(s, again.scenarios.single())
        }
    }

    @Test
    fun batchOfScenariosIsAccepted() {
        val a = Fixtures.text("zig-variables").trim()
        val b = Fixtures.text("variables").trim()
        val r = ScenarioValidator.parse("{\"schema\":\"pyreels/1\",\"batch\":[$a,$b]}")
        assertTrue(r.ok, r.problems.joinToString("\n") { it.render() })
        assertEquals(2, r.scenarios.size)
    }

    @Test
    fun anyLanguageWorks() {
        // an unknown language, and a language whose name has no Latin letters
        val zig = Fixtures.scenario("zig-variables")
        assertTrue(zig.scenes.filterIsInstance<Scene.CodeType>().all { it.language == "zig" })
        val ar = Fixtures.scenario("arabic-lang")
        assertTrue(ar.scenes.filterIsInstance<Scene.CodeType>().all { it.language == "l5b7d2c1" }, "blank language resolves to the first teacher's")
        assertEquals("main.zig", Languages.fileName("zig"))
        assertEquals("main.l5b7d", Languages.fileName("l5b7d2c1"))
        assertEquals("cpp", Languages.slug("C++"))
        assertEquals("csharp", Languages.slug("C#"))
        assertTrue(Languages.slug("لغة الأمل").matches(Regex("^[a-z][a-z0-9_]{0,23}$")))
        assertEquals(Languages.slug("لغة الأمل"), Languages.slug("لغة الأمل"))
        assertTrue(Languages.isPython("Py") && !Languages.isPython("cpp"))
    }

    @Test
    fun personasAreGeneratedNotBuiltIn() {
        val a = Personas.forLanguage("zig", "Zig"); val b = Personas.forLanguage("zig", "Zig"); val c = Personas.forLanguage("go", "Go")
        assertEquals(a, b)
        assertTrue(a.accentDark != c.accentDark, "different languages get different colours")
        assertEquals("Zig", a.monogram)
        assertEquals("Go", c.monogram)
        assertEquals("ل", Personas.forLanguage("l1", "لغة الأمل").monogram)
        assertEquals("note", Personas.forLanguage("zig", "Zig", "note").personality)
    }

    @Test
    fun autoRepairNormalisesLanguageIds() {
        val broken = Fixtures.text("zig-variables").replace("\"language\": \"zig\"", "\"language\": \"C++\"")
        val repaired = AutoRepair.repair(broken)
        assertTrue(repaired.text.contains("\"language\": \"cpp\""))
        assertTrue(ScenarioValidator.parse(repaired.text).ok)
    }

    @Test
    fun paceChangesLengthButStaysValid() {
        val base = Fixtures.scenario("variables")
        val slow = Timeline.build(base.copy(meta = base.meta.copy(pace = "slow")))
        val calm = Timeline.build(base)
        val normal = Timeline.build(base.copy(meta = base.meta.copy(pace = "normal")))
        println("slow=${slow.totalMs} calm=${calm.totalMs} normal=${normal.totalMs}")
        assertTrue(slow.totalMs >= calm.totalMs && calm.totalMs >= normal.totalMs)
        assertTrue(slow.totalMs <= Limits.HARD_CAP_SECONDS * 1000L)
    }

    @Test
    fun shortExportFixtureIsValidAndShort() {
        // used by the on-device end-to-end export tests (a full export through the service, kept short)
        val r = ScenarioValidator.parse(Fixtures.text("short"))
        assertTrue(r.ok, r.problems.joinToString("\n") { it.render() })
        val tl = Timeline.build(r.scenarios.single())
        println("short total=${tl.totalMs}")
        assertTrue(tl.totalMs in 20_000..60_000, "short total ${tl.totalMs}")
    }

    @Test
    fun resolvedIsIdempotent() {
        val s = Fixtures.scenario("arabic-lang")
        assertEquals(s, s.resolved())
    }
}
