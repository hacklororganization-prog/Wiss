package studio.pyreels.core

import studio.pyreels.core.brief.Brief
import studio.pyreels.core.brief.EpisodeRecord
import studio.pyreels.core.content.ContentRepository
import studio.pyreels.core.prompt.PromptBuilder
import studio.pyreels.core.publish.PublishPack
import studio.pyreels.core.quality.QualityChecker
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.ScenarioValidator
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptAndPublishTest {
    private val brief = Brief(teachers = listOf("python", "java"), lessonId = "py-variables", tone = "funny", humor = 3)

    @Test
    fun promptHasAllSectionsInOrder() {
        val p = PromptBuilder.build(PromptBuilder.Input(brief))
        val heads = (1..12).map { p.indexOf("\n## $it. ") }
        assertTrue(heads.all { it > 0 }, heads.toString())
        assertEquals(heads.sorted(), heads)
        listOf("hook_predict", "engage_moment", "\"\$schema\"", "return JSON only", "Self-check", "جافا", "name-undefined", "key_tap").forEach {
            assertTrue(p.contains(it, ignoreCase = true), "missing $it")
        }
    }

    @Test
    fun promptIncludesSeriesHistoryAndWeeklyContract() {
        val hist = listOf(EpisodeRecord(1, "py-print", "print", "الطباعة", "print تُظهر ما بين القوسين.", null, null, null, 0))
        val week = ContentRepository.curriculum.proposeWeek(setOf("py-print"))
        val p = PromptBuilder.build(PromptBuilder.Input(brief, hist, week))
        assertTrue(p.contains("الحلقة 1 (py-print)"))
        assertTrue(p.contains("Weekly batch contract"))
        assertTrue(p.contains("\"batch\""))
        assertEquals(7, week.size)
    }

    @Test
    fun fileNameIsSuggested() {
        assertEquals("prompt_py-variables_2026-11-09.md", PromptBuilder.fileName(brief, LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun repairPromptListsProblems() {
        val r = ScenarioValidator.parse("{\"schema\":\"pyreels/1\"}")
        val p = PromptBuilder.repairPrompt("{\"schema\":\"pyreels/1\"}", r.problems)
        assertTrue(p.contains("meta") && p.contains("Original JSON"))
    }

    @Test
    fun srtIsWellFormedAndOrdered() {
        val s = ScenarioValidator.parse(Resources.text("samples/02-variables.pyreels.json")).scenarios.single()
        val tl = Timeline.build(s)
        val srt = PublishPack.srt(tl)
        val blocks = srt.trim().split("\n\n")
        assertTrue(blocks.size > 20)
        var last = -1L
        blocks.forEachIndexed { i, b ->
            val lines = b.lines()
            assertEquals((i + 1).toString(), lines[0])
            val (a, z) = lines[1].split(" --> ")
            val sa = parse(a); val sz = parse(z)
            assertTrue(sa >= last && sz > sa, b)
            last = sa
        }
        assertEquals("00:01:02,005", PublishPack.ts(62_005))
    }

    private fun parse(t: String): Long {
        val (h, m, rest) = t.split(":"); val (s, ms) = rest.split(",")
        return h.toLong() * 3600000 + m.toLong() * 60000 + s.toLong() * 1000 + ms.toLong()
    }

    @Test
    fun captionHasHashtagsAndChallenge() {
        val s = ScenarioValidator.parse(Resources.text("samples/02-variables.pyreels.json")).scenarios.single()
        val c = PublishPack.caption(s)
        assertTrue(c.hashtags.contains("#python"))
        assertTrue(c.pinnedComment.isNotBlank())
        val window = PublishPack.teaserWindow(Timeline.build(s))
        val len = window.sumOf { it.last - it.first + 1 }
        assertTrue(len in 10_000..15_000, "teaser $len ms")
    }

    @Test
    fun qualityFixesWork() {
        val s = ScenarioValidator.parse(Resources.text("samples/02-variables.pyreels.json")).scenarios.single()
        val idx = s.scenes.indexOfFirst { it is studio.pyreels.core.model.Scene.EngageMoment }
        val moved = s.copy(scenes = s.scenes.toMutableList().apply { add(3, removeAt(idx)) })
        val tl = Timeline.build(moved)
        val q = QualityChecker.check(moved, tl)
        assertTrue(q.problems.any { it.code == "q.engage" })
        val fixed = QualityChecker.shiftEngage(moved, tl)
        val q2 = QualityChecker.check(fixed, Timeline.build(fixed))
        assertTrue(q2.problems.none { it.code == "q.engage" }, q2.problems.map { it.render() }.toString())

        val chatIdx = s.scenes.indexOfFirst { it is studio.pyreels.core.model.Scene.Chat }
        val split = QualityChecker.splitBubble(s.copy(scenes = s.scenes.toMutableList().apply {
            this[chatIdx] = (this[chatIdx] as studio.pyreels.core.model.Scene.Chat).copy(text = "هذه جملة أولى طويلة قليلًا، وهذه جملة ثانية بعدها.")
        }), chatIdx)
        assertEquals(s.scenes.size + 1, split.scenes.size)
    }
}
