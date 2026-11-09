package studio.pyreels.core

import studio.pyreels.core.content.ContentRepository
import studio.pyreels.core.templates.Templates
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.ScenarioValidator
import studio.pyreels.core.brief.Brief
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentTest {
    @Test
    fun curriculumHasAtLeast30OrderedLinkedLessons() {
        val c = ContentRepository.curriculum
        assertTrue(c.lessons.size >= 30)
        assertEquals((1..c.lessons.size).toList(), c.lessons.map { it.order })
        c.lessons.dropLast(1).forEach { assertTrue(c.byId(it.next) != null, it.id) }
        c.lessons.forEach { assertTrue(it.keySentence.split(' ').size <= 10, it.id) }
    }

    @Test
    fun errorsLibraryHasAtLeast40EntriesLinkedToLessons() {
        val e = ContentRepository.errors
        assertTrue(e.errors.size >= 40)
        val types = e.errors.map { it.errorType }.toSet()
        listOf("NameError", "SyntaxError", "IndentationError", "TypeError", "IndexError", "KeyError", "ZeroDivisionError", "ValueError", "AttributeError", "ModuleNotFoundError")
            .forEach { assertTrue(it in types, it) }
        e.errors.forEach { err -> err.lessons.forEach { assertTrue(ContentRepository.curriculum.byId(it) != null, "${err.id} -> $it") } }
        assertEquals(e.errors.size, e.errors.map { it.id }.toSet().size)
    }

    private fun python(): String? {
        val wanted = System.getProperty("pyreels.python") ?: "python3"
        for (p in listOf("python3.11", wanted)) {
            try {
                val pr = ProcessBuilder(p, "--version").redirectErrorStream(true).start()
                if (pr.waitFor(5, TimeUnit.SECONDS) && pr.exitValue() == 0) return p
            } catch (_: Exception) { }
        }
        return null
    }

    private fun run(py: String, code: String): Pair<Int, String> {
        val dir = File(System.getProperty("java.io.tmpdir"), "pyreels-err").apply { mkdirs() }
        // Write the snippet as a UTF-8 file: passing Arabic text as an argument depends on the JVM locale.
        val file = File(dir, "snippet.py").apply { writeText(code, Charsets.UTF_8) }
        val pb = ProcessBuilder(py, "-I", file.name).directory(dir).redirectErrorStream(false)
        pb.environment()["PYTHONUTF8"] = "1"
        val pr = pb.start()
        pr.waitFor(10, TimeUnit.SECONDS)
        return pr.exitValue() to pr.errorStream.readBytes().toString(Charsets.UTF_8)
    }

    /** Verifies each library message against REAL Python execution (skipped when no python3.11). */
    @Test
    fun errorMessagesMatchRealPython() {
        val py = python() ?: return println("python not found, skipping")
        val version = ProcessBuilder(py, "-c", "import sys;print(f'{sys.version_info[0]}.{sys.version_info[1]}')").start().inputStream.readBytes().toString(Charsets.UTF_8).trim()
        if (version != ContentRepository.errors.python) return println("python $version != library ${ContentRepository.errors.python}, skipping exact check")
        for (e in ContentRepository.errors.errors) {
            val (code, err) = run(py, e.snippet)
            assertTrue(code != 0, "${e.id} should fail")
            val last = err.trim().lines().last { it.isNotBlank() }
            assertEquals(e.message, last, e.id)
            val (fixCode, fixErr) = run(py, e.fix)
            assertEquals(0, fixCode, "${e.id} fix fails: $fixErr")
        }
    }

    @Test
    fun everyRoadmapLessonDraftsIntoAValidVideo() {
        for (l in ContentRepository.curriculum.lessons) {
            for (teachers in listOf(listOf("python"), listOf("python", "java", "cpp", "javascript"))) {
                val s = Templates.draft(l, Brief(teachers = teachers, voiceOver = true))
                val r = ScenarioValidator.parse(ScenarioValidator.encode(s))
                assertTrue(r.ok, "${l.id}: " + r.errors.joinToString { it.render() })
                val tl = Timeline.build(s)
                assertTrue(tl.totalMs in 60_000..120_000, "${l.id} ${tl.totalMs}")
            }
        }
    }

    @Test
    fun pastedTextProducesValidScenario() {
        val text = "ما هي القائمة؟\nالقائمة تحفظ عدة قيم بالترتيب. نكتبها بين قوسين مربعين.\nnums = [1, 2, 3]\nprint(nums[0])\nالعدّ يبدأ من صفر."
        val s = Templates.fromPastedText(text, Brief(lessonTitle = "القوائم", lessonId = "py-lists"))
        val r = ScenarioValidator.parse(ScenarioValidator.encode(s))
        assertTrue(r.ok, r.errors.joinToString { it.render() })
        assertTrue(s.scenes.any { it is studio.pyreels.core.model.Scene.CodeType && it.code.contains("nums") })
    }
}
