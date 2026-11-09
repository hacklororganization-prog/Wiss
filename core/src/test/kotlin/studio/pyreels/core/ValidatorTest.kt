package studio.pyreels.core

import studio.pyreels.core.validate.AutoRepair
import studio.pyreels.core.validate.ScenarioValidator
import studio.pyreels.core.validate.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatorTest {
    private val sample = Resources.text("samples/02-variables.pyreels.json")

    @Test
    fun syntaxErrorIsReportedWithLine() {
        val r = ScenarioValidator.parse("{\n \"schema\": \"pyreels/1\",\n \"meta\": {\n}")
        assertFalse(r.ok)
        assertEquals("syntax", r.problems.first().code)
    }

    @Test
    fun missingFieldPointsAtSceneAndField() {
        val broken = sample.replaceFirst("\"text\": \"تعلّمنا أن print تُظهر ما بين القوسين.\"", "\"txt\": \"x\"")
        val r = ScenarioValidator.parse(broken)
        assertFalse(r.ok)
        val p = r.errors.first { it.code == "schema.required" }
        assertEquals(1, p.sceneIndex)
        assertTrue(p.where.contains("المشهد 2 (recap)"), p.where)
        assertTrue(p.message.contains("text"))
        // the unknown field is a warning, not an error
        assertTrue(r.problems.any { it.code == "schema.unknownField" && it.severity == Severity.WARNING })
    }

    @Test
    fun unknownSceneTypeIsReported() {
        val r = ScenarioValidator.parse(sample.replaceFirst("\"type\": \"recap\"", "\"type\": \"game\""))
        assertFalse(r.ok)
        assertTrue(r.errors.any { it.code == "schema.enum" && it.where.contains("المشهد 2") }, r.errors.toString())
    }

    @Test
    fun tooLongBubbleIsReported() {
        val long = "كلمة ".repeat(40).trim()
        val r = ScenarioValidator.parse(sample.replaceFirst("عندي سؤال… كيف يتذكّر البرنامج الأرقام؟", long))
        assertTrue(r.errors.any { it.code == "schema.maxLength" && it.sceneIndex == 2 }, r.errors.toString())
    }

    @Test
    fun unknownCastReferenceIsAnError() {
        val r = ScenarioValidator.parse(sample.replaceFirst("\"from\": \"ahmed\"", "\"from\": \"rust\""))
        assertTrue(r.errors.any { it.code == "ref.cast" }, r.problems.toString())
    }

    @Test
    fun moreThanOneConceptIsRejected() {
        val r = ScenarioValidator.parse(sample.replace("\"concept\": \"variable\"", "\"concept\": \"variable, loop\""))
        assertTrue(r.errors.any { it.code == "lesson.oneConcept" })
    }

    @Test
    fun structureOrderIsEnforced() {
        val s = ScenarioValidator.parse(sample).scenarios.single()
        val shuffled = s.copy(scenes = s.scenes.drop(1) + s.scenes.first())
        val problems = ScenarioValidator.semantic(shuffled)
        assertTrue(problems.any { it.code == "order.hook" })
        assertTrue(problems.any { it.code == "order.teaser" })
    }

    @Test
    fun fencesAreAccepted() {
        assertTrue(ScenarioValidator.parse("```json\n$sample\n```").ok)
    }

    @Test
    fun autoRepairFixesTrivialIssues() {
        val s = ScenarioValidator.parse(sample).scenarios.single()
        var json = ScenarioValidator.encode(s.copy(scenes = s.scenes.drop(1) + s.scenes.first()))
        json = json.replaceFirst("\"type\": \"recap\"", "\"type\": \"recap\", \"sfx\": \"boom\", \"color\": \"red\"")
            .replace("\"type\": \"key_sentence\"", "\"type\": \"key\"")
        assertFalse(ScenarioValidator.parse(json).ok)
        val fixed = AutoRepair.repair(json)
        val r = ScenarioValidator.parse(fixed.text)
        assertTrue(r.ok, r.problems.joinToString("\n") { it.render() } + "\n" + fixed.applied)
        assertTrue(fixed.applied.isNotEmpty())
        assertTrue(r.problems.none { it.code == "schema.unknownField" })
    }

    @Test
    fun capAbove180IsError() {
        val r = ScenarioValidator.parse(sample.replace("\"targetSeconds\": 90", "\"targetSeconds\": 200"))
        assertFalse(r.ok)
    }

    @Test
    fun wrapCountsLines() {
        assertEquals(1, ScenarioValidator.wrapCount("قصير"))
        assertTrue(ScenarioValidator.wrapCount("كلمة ".repeat(30)) >= 4)
    }
}
