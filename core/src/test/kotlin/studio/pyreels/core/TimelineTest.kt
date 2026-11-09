package studio.pyreels.core

import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Scene
import studio.pyreels.core.timeline.DurationFitter
import studio.pyreels.core.timeline.SceneBounds
import studio.pyreels.core.timeline.Stage
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.ScenarioValidator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineTest {
    private val scenario = ScenarioValidator.parse(Resources.text("samples/02-variables.pyreels.json")).scenarios.single()

    @Test
    fun fitterHitsTargetAndRespectsBounds() {
        val b = listOf(SceneBounds(3000, 3000, 3000), SceneBounds(2000, 4000, 8000), SceneBounds(1000, 2000, 3000))
        val f = DurationFitter.fit(b, 12_000)
        assertEquals(3000, f.durations[0])
        assertTrue(f.durations[2] <= 3000)
        assertTrue(abs(f.totalMs - 12_000) <= 50, "${f.totalMs}")
        val tooShort = DurationFitter.fit(b, 1_000)
        assertTrue(!tooShort.reachable)
        assertEquals(6000, tooShort.totalMs)
    }

    @Test
    fun hookIsExactlyThreeSecondsAndFirst() {
        val tl = Timeline.build(scenario)
        assertEquals(Limits.HOOK_MS, tl.scenes[0].durationMs)
        assertEquals(Stage.HOOK, tl.frameAt(100).layers.single().stage)
    }

    @Test
    fun framesAreDeterministic() {
        val a = Timeline.build(scenario)
        val b = Timeline.build(scenario)
        for (t in 0 until a.totalMs step 997) {
            val fa = a.frameAt(t); val fb = b.frameAt(t)
            assertEquals(fa.sceneIndex, fb.sceneIndex)
            assertEquals(fa.layers.map { it.stage }, fb.layers.map { it.stage })
            assertEquals(fa.layers.mapNotNull { it.code?.text }, fb.layers.mapNotNull { it.code?.text })
            assertEquals(fa.layers.mapNotNull { it.chat?.messages?.size }, fb.layers.mapNotNull { it.chat?.messages?.size })
        }
        assertEquals(a.sfx, b.sfx)
    }

    @Test
    fun codeIsTypedProgressivelyAndCompletely() {
        val tl = Timeline.build(scenario)
        val ct = tl.scenes.first { it.scene is Scene.CodeType }
        val code = (ct.scene as Scene.CodeType).code
        val early = tl.frameAt(ct.startMs + 600).layers.last().code!!.text
        val end = tl.frameAt(ct.endMs - 10).layers.last().code!!.text
        assertTrue(early.length < code.length)
        assertEquals(code, end)
    }

    @Test
    fun chatMessagesAccumulateAndTypingPrecedesEachMessage() {
        val tl = Timeline.build(scenario)
        val chat = tl.scenes.filter { it.scene is Scene.Chat }
        val c = chat[1]
        val typing = tl.frameAt(c.startMs + 100).layers.last().chat!!
        assertEquals((c.scene as Scene.Chat).from, typing.typingFrom)
        val after = tl.frameAt(c.endMs - 10).layers.last().chat!!
        assertTrue(after.messages.any { it.text == (c.scene as Scene.Chat).text })
        assertTrue(after.messages.size >= 2)
    }

    @Test
    fun runTraceHighlightsLinesAndShowsVariables() {
        val tl = Timeline.build(scenario)
        val run = tl.scenes.first { it.scene is Scene.Run }
        var sawX7 = false; var sawLine2 = false; var sawOutput = false
        var t = run.startMs
        while (t < run.endMs) {
            val c = tl.frameAt(t).layers.last().code
            if (c != null) {
                if (c.activeLine == 2) sawLine2 = true
                if (c.vars.any { it.name == "x" && it.value == "7" }) sawX7 = true
                if (c.terminal.any { it.text == "7" }) sawOutput = true
            }
            t += 100
        }
        assertTrue(sawLine2 && sawX7 && sawOutput, "$sawLine2 $sawX7 $sawOutput")
    }

    @Test
    fun stageChangesCrossfade() {
        val tl = Timeline.build(scenario)
        val ct = tl.scenes.first { it.scene is Scene.CodeType }
        val f = tl.frameAt(ct.startMs + 100)
        assertEquals(2, f.layers.size)
        assertTrue(f.layers[0].alpha > 0f && f.layers[1].alpha > 0f)
    }

    @Test
    fun noBlinkFasterThanOneHertz() {
        val tl = Timeline.build(scenario)
        val ct = tl.scenes.first { it.scene is Scene.CodeType }
        // after typing, the cursor toggles at most once per 500 ms (≤ 1 Hz full cycle)
        var last: Boolean? = null; var lastChange = -1L; var minGap = Long.MAX_VALUE
        var t = ct.endMs - 1000
        while (t < ct.endMs) {
            val v = tl.frameAt(t).layers.last().code!!.cursorVisible
            if (last != null && v != last) { if (lastChange >= 0) minGap = minOf(minGap, t - lastChange); lastChange = t }
            last = v; t += 10
        }
        assertTrue(minGap >= 500, "cursor toggled after $minGap ms")
        // the hook ticks are 1 s apart
        val ticks = tl.sfx.filter { it.id == "tick" && it.timeMs < 3000 }.map { it.timeMs }
        ticks.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 1000) }
    }

    @Test
    fun voiceDurationsDriveSceneLength() {
        val idx = scenario.scenes.indexOfFirst { it.say && it is Scene.Chat }
        val long = Timeline.build(scenario, voiceMs = mapOf(idx to 9_000L))
        assertTrue(long.scenes[idx].durationMs >= 9_000)
    }

    @Test
    fun simultaneousMoversStayWithinLimit() {
        val tl = Timeline.build(scenario)
        var t = 0L
        while (t < tl.totalMs) {
            val n = studio.pyreels.core.quality.QualityChecker.movers(tl, t)
            assertTrue(n <= Limits.MAX_SIMULTANEOUS_MOVERS + 1, "too many movers ($n) at $t")
            t += 100
        }
    }
}
