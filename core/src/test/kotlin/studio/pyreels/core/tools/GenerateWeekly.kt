package studio.pyreels.core.tools

import studio.pyreels.core.brief.Brief
import studio.pyreels.core.brief.EpisodeRecord
import studio.pyreels.core.content.ContentRepository
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.ScenarioBatch
import studio.pyreels.core.model.Scene
import studio.pyreels.core.templates.Templates
import studio.pyreels.core.validate.ScenarioValidator
import java.io.File

/**
 * Regenerates samples/weekly-batch.pyreels.json: 7 linked episodes drafted from the roadmap
 * (each teaser announces the next episode, each recap recalls the previous one).
 * Run: gradle :core:generateWeekly
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "core/src/main/resources/pyreels/samples/weekly-batch.pyreels.json")
    val c = ContentRepository.curriculum
    val lessons = c.lessons.sortedBy { it.order }.drop(2).take(7) // variables … if
    val history = ArrayList<EpisodeRecord>()
    // the two first roadmap lessons are "already published"
    c.lessons.take(2).forEachIndexed { i, l -> history += EpisodeRecord(i + 1, l.id, l.concept, l.title, l.keySentence, null, null, null, 0) }
    val teacherSets = listOf(listOf("python"), listOf("python", "java"), listOf("python"), listOf("python", "javascript"), listOf("python", "cpp"), listOf("python"), listOf("python", "java", "cpp"))
    val episodes = ArrayList<Scenario>()
    lessons.forEachIndexed { i, l ->
        val brief = Brief(teachers = teacherSets[i], lessonId = l.id, tone = if (i % 2 == 0) "calm" else "funny", humor = if (i % 2 == 0) 1 else 3)
        var s = Templates.draft(l, brief, history)
        // the recap of episode N+1 must match the teaser of episode N
        if (i > 0) {
            val prevTeaser = episodes.last().scenes.filterIsInstance<Scene.Teaser>().first().text
            s = s.copy(scenes = s.scenes.map { if (it is Scene.Recap) it.copy(text = "تذكير: ${lessons[i - 1].keySentence}".take(118)) else it })
            check(prevTeaser.contains(l.title))
        }
        episodes += s
        history += Templates.record(s, 0)
    }
    val json = ScenarioValidator.encodeBatch(ScenarioBatch(batch = episodes))
    val r = ScenarioValidator.parse(json)
    check(r.ok) { r.problems.joinToString("\n") { it.render() } }
    out.parentFile.mkdirs()
    out.writeText(json + "\n")
    println("wrote ${out.path} (${episodes.size} episodes)")
}
