package studio.pyreels.core.brief

import kotlinx.serialization.Serializable
import studio.pyreels.core.model.Limits

/** Answers of the setup wizard («إعداد الدرس»). Stored with the project and inside presets. */
@Serializable
data class Brief(
    val teachers: List<String> = listOf("python"),
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
        teachers = teachers.distinct().take(Limits.MAX_TEACHERS).ifEmpty { listOf("python") },
        humor = humor.coerceIn(0, 5),
        targetSeconds = targetSeconds.coerceIn(Limits.MIN_TARGET_SECONDS, Limits.HARD_CAP_SECONDS),
    )

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

/** Saved style preset (section 11.7). The lesson topic is the only thing left to choose. */
@Serializable
data class StylePreset(
    val id: String,
    val name: String,
    val brief: Brief,
    val builtIn: Boolean = false,
) {
    companion object {
        val DEFAULTS = listOf(
            StylePreset("calm", "دروس هادئة", Brief(teachers = listOf("python"), tone = "calm", humor = 1, methods = listOf("analogy", "visual_trace")), true),
            StylePreset("funny", "دروس مضحكة", Brief(teachers = listOf("python", "javascript"), tone = "funny", humor = 4, methods = listOf("analogy", "mistake_fixing")), true),
            StylePreset(
                "compare", "مقارنة اللغات",
                Brief(teachers = listOf("python", "java", "cpp"), tone = "entertaining", humor = 3, methods = listOf("comparison", "visual_trace")), true,
            ),
        )
    }
}

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
        if (history.isEmpty()) return "هذه أول حلقة في السلسلة؛ أحمد لا يعرف شيئًا عن البرمجة بعد."
        return history.sortedBy { it.episode }.joinToString("\n") { e ->
            "- الحلقة ${e.episode} (${e.lessonId}): ${e.title} — جملة اليوم: «${e.keySentence}»" +
                (e.mistake?.let { " — الخطأ المعروض: $it" } ?: "") + (e.analogy?.let { " — التشبيه: $it" } ?: "")
        }
    }
}
