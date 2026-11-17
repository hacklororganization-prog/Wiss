package studio.pyreels.core.util

import kotlinx.serialization.json.JsonPrimitive
import studio.pyreels.core.brief.EpisodeRecord
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.TraceStep

/** Small helpers for working with a scenario (no content is generated here). */
object ScenarioTools {

    /**
     * A plain line-by-line trace for simple assignments (`x = 5`, `x = "a"`); the creator can edit
     * it. Real execution (Python) replaces it automatically.
     */
    fun simpleTrace(code: String): List<TraceStep> {
        val lines = code.lines()
        val vars = LinkedHashMap<String, String>()
        val steps = ArrayList<TraceStep>()
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
            Regex("^(?:[A-Za-z_][\\w<>:*&\\[\\]]*\\s+)?([A-Za-z_]\\w*)\\s*=\\s*(-?\\d+);?$").find(line)?.let { vars[it.groupValues[1]] = it.groupValues[2] }
            Regex("^(?:[A-Za-z_][\\w<>:*&\\[\\]]*\\s+)?([A-Za-z_]\\w*)\\s*=\\s*\"([^\"]*)\";?$").find(line)?.let { vars[it.groupValues[1]] = "\"${it.groupValues[2]}\"" }
            steps += TraceStep(line = i + 1, vars = vars.mapValues { (_, v) -> if (v.startsWith("\"")) JsonPrimitive(v.trim('"')) else JsonPrimitive(v.toLong()) })
        }
        return steps.take(16)
    }

    /** History record of a finished episode (for series memory). */
    fun record(s: Scenario, now: Long): EpisodeRecord = EpisodeRecord(
        episode = s.meta.series.episode ?: 0,
        lessonId = s.meta.lesson.id,
        concept = s.meta.lesson.concept,
        title = s.meta.title,
        keySentence = s.scenes.filterIsInstance<Scene.KeySentence>().firstOrNull()?.text ?: "",
        mistake = s.scenes.filterIsInstance<Scene.CommonMistake>().firstOrNull()?.error,
        challenge = s.scenes.filterIsInstance<Scene.Chat>().lastOrNull { it.challenge }?.text
            ?: s.scenes.filterIsInstance<Scene.Teaser>().firstOrNull()?.challenge,
        analogy = s.scenes.filterIsInstance<Scene.Analogy>().firstOrNull()?.title,
        savedAt = now,
    )
}
