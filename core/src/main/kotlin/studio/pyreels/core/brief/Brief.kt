package studio.pyreels.core.brief

import kotlinx.serialization.Serializable
import studio.pyreels.core.model.Limits

/** Answers of the setup wizard («إعداد الدرس»). Stored with the project and inside presets. */
@Serializable
data class Brief(
    /** Teacher ids (one per language the creator added). */
    val teachers: List<String> = emptyList(),
    /** Display names and optional personality notes of the teachers, by id. */
    val teacherNames: Map<String, String> = emptyMap(),
    val teacherNotes: Map<String, String> = emptyMap(),
    val lessonId: String? = null,
    val lessonTitle: String = "",
    val level: String = "beginner",
    val exampleMode: String = "auto",
    val customExample: String = "",
    val methods: List<String> = listOf("analogy", "visual_trace"),
    val tone: String = "calm",
    val humor: Int = 2,
    val languageStyle: String = "msa",
    val includeMistake: Boolean = true,
    val includeChallenge: Boolean = true,
    val teaserMode: String = "auto",
    val customTeaser: String = "",
    /** What the previous episode taught, in the creator's own words (the AI polishes it). Empty = no recap. */
    val customRecap: String = "",
    val voiceOver: Boolean = false,
    val voices: Map<String, String> = emptyMap(),
    val targetSeconds: Int = Limits.TARGET_SECONDS,
    val theme: String = "calm-dark",
    val frame: String = "phone",
    val chatColor: String = "green",
    val celebrate: Boolean = true,
    val notes: String = "",
) {
    fun normalized(): Brief = copy(
        teachers = teachers.distinct().take(Limits.MAX_TEACHERS),
        humor = humor.coerceIn(0, 5),
        targetSeconds = targetSeconds.coerceIn(Limits.MIN_TARGET_SECONDS, Limits.HARD_CAP_SECONDS),
    )

    /** Display name of a teacher id. */
    fun nameOf(id: String) = teacherNames[id]?.takeIf { it.isNotBlank() } ?: id

    companion object {
        val METHODS = linkedMapOf(
            "analogy" to "تشبيه من الحياة",
            "step_by_step" to "خطوة بخطوة",
            "comparison" to "مقارنة بين اللغات",
            "mistake_fixing" to "إصلاح الأخطاء",
            "visual_trace" to "تتبّع مرئي للتنفيذ",
        )
        val TONES = linkedMapOf("calm" to "هادئ", "funny" to "مضحك", "entertaining" to "مسلٍّ", "serious" to "جاد")
        val STYLES = linkedMapOf("msa" to "فصحى مبسّطة", "dialect" to "لهجة خفيفة", "english" to "English")
        val LEVELS = linkedMapOf("beginner" to "مبتدئ تمامًا", "basics" to "يعرف الأساسيات")
    }
}

/** A style the creator saved (nothing is built in): all answers of the wizard except the topic. */
@Serializable
data class StylePreset(
    val id: String,
    val name: String,
    val brief: Brief,
    val builtIn: Boolean = false,
)

/** One episode in the local series history (section 11.1). */
@Serializable
data class EpisodeRecord(
    val episode: Int,
    val lessonId: String,
    val concept: String,
    val title: String,
    val keySentence: String,
    val mistake: String?,
    val challenge: String?,
    val analogy: String?,
    val savedAt: Long,
)

object SeriesMemory {
    /** Ahmed matures as the series progresses. */
    fun ahmedLevel(completed: Int): Int = when {
        completed < 5 -> 1
        completed < 12 -> 2
        completed < 22 -> 3
        else -> 4
    }

    fun ahmedLevelDescription(level: Int): String = when (level) {
        1 -> "مبتدئ تمامًا: أسئلته بسيطة جدًا، يخاف من الأخطاء قليلًا."
        2 -> "بدأ يفهم: يربط الدرس الجديد بما تعلّمه، ويجرّب بنفسه قبل أن يسأل."
        3 -> "متمكّن من الأساسيات: يسأل «لماذا؟» ويقترح حلولًا أحيانًا."
        else -> "متعلّم واثق: يلاحظ الأخطاء بنفسه ويسأل عن أفضل طريقة."
    }

    fun recapFor(history: List<EpisodeRecord>): String? {
        val last = history.maxByOrNull { it.episode } ?: return null
        return "في الحلقة الماضية تعلّمنا ${last.title}: ${last.keySentence}"
    }

    /** Ahmed's opening question suggestion, matured by level. */
    fun suggestedQuestion(level: Int, topic: String, previous: String?): String = when (level) {
        1 -> "ما هو $topic؟ وهل هو صعب؟"
        2 -> if (previous != null) "فهمت $previous، لكن ما علاقة $topic به؟" else "كيف أستخدم $topic؟"
        3 -> "لماذا نحتاج $topic أصلًا؟ ألا يكفي ما تعلّمناه؟"
        else -> "ما أفضل طريقة لاستخدام $topic دون أخطاء؟"
    }

    fun summary(history: List<EpisodeRecord>): String {
        if (history.isEmpty()) return "(no earlier episodes recorded)"
        return history.sortedBy { it.episode }.joinToString("\n") { e ->
            "- الحلقة ${e.episode} (${e.lessonId}): ${e.title} — جملة اليوم: «${e.keySentence}»" +
                (e.mistake?.let { " — الخطأ المعروض: $it" } ?: "") + (e.analogy?.let { " — التشبيه: $it" } ?: "")
        }
    }
}
