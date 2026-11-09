package studio.pyreels.core.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import studio.pyreels.core.Resources

@Serializable
data class CurriculumLesson(
    val id: String,
    val order: Int,
    val concept: String,
    val title: String,
    val summary: String,
    val analogy: String,
    val example: String,
    val expectedOutput: String,
    val keySentence: String,
    val next: String? = null,
    val teaser: String,
)

@Serializable
data class Curriculum(val version: Int, val language: String, val lessons: List<CurriculumLesson>) {
    fun byId(id: String?) = lessons.firstOrNull { it.id == id }
    fun after(id: String?): CurriculumLesson? = byId(id)?.next?.let { byId(it) }
    fun previous(id: String?): CurriculumLesson? = byId(id)?.let { l -> lessons.firstOrNull { it.next == l.id } }

    /** First lesson not yet completed. */
    fun nextFor(completed: Set<String>): CurriculumLesson? = lessons.sortedBy { it.order }.firstOrNull { it.id !in completed }

    fun progress(completed: Set<String>): Float = if (lessons.isEmpty()) 0f else lessons.count { it.id in completed }.toFloat() / lessons.size

    /** Proposes up to [n] lessons for a weekly plan. */
    fun proposeWeek(completed: Set<String>, n: Int = 7): List<CurriculumLesson> =
        lessons.sortedBy { it.order }.filter { it.id !in completed }.take(n)
}

@Serializable
data class BeginnerError(
    val id: String,
    val errorType: String,
    val snippet: String,
    val message: String,
    val explain: String,
    val fix: String,
    val fixOutput: String? = null,
    val lessons: List<String> = emptyList(),
)

@Serializable
data class ErrorsLibrary(val version: Int, val python: String, val errors: List<BeginnerError>) {
    fun forLesson(lessonId: String?): List<BeginnerError> = errors.filter { lessonId != null && lessonId in it.lessons }
    fun byId(id: String?) = errors.firstOrNull { it.id == id }

    /** Errors for a lesson, falling back to the previous lessons when none match exactly. */
    fun relevant(lessonId: String?, curriculum: Curriculum, max: Int = 6): List<BeginnerError> {
        val exact = forLesson(lessonId)
        if (exact.size >= 3) return exact.take(max)
        val order = curriculum.byId(lessonId)?.order ?: return exact.take(max)
        val earlier = curriculum.lessons.filter { it.order < order }.sortedByDescending { it.order }.map { it.id }
        val extra = earlier.flatMap { forLesson(it) }.filter { it !in exact }
        return (exact + extra).distinct().take(max)
    }
}

object ContentRepository {
    private val json = Json { ignoreUnknownKeys = true }
    val curriculum: Curriculum by lazy { json.decodeFromString(Curriculum.serializer(), Resources.text("curriculum.json")) }
    val errors: ErrorsLibrary by lazy { json.decodeFromString(ErrorsLibrary.serializer(), Resources.text("errors.json")) }
}
