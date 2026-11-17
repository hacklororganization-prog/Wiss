package studio.pyreels.core.model

/** Hard and soft limits of the renderer. Shared by the validator, the quality checker and the prompt builder. */
object Limits {
    const val TARGET_SECONDS = 90
    const val WARN_SECONDS = 130
    /** A calm pace may run this much over the target instead of rushing the reader. */
    const val CALM_STRETCH = 1.35
    const val HARD_CAP_SECONDS = 180
    const val MIN_TARGET_SECONDS = 30
    const val TARGET_TOLERANCE = 0.10

    const val MAX_CHARS_PER_LINE = 34
    const val MAX_LINES_PER_BUBBLE = 4
    const val MAX_BUBBLE_CHARS = MAX_CHARS_PER_LINE * MAX_LINES_PER_BUBBLE
    const val MAX_CODE_LINES = 12
    const val MAX_CODE_LINE_CHARS = 38
    const val MAX_KEY_SENTENCE_WORDS = 10
    const val MAX_TEACHERS = 4
    const val MAX_CAST = MAX_TEACHERS + 1
    const val MAX_SCENES = 60
    const val MAX_OUTPUT_LINES = 6

    /** Beginner-friendly reading speed ceiling (Arabic words per second). */
    const val WORDS_PER_SECOND = 2.0
    const val MIN_SCENE_MS = 1200L
    const val MAX_SCENE_MS = 15_000L
    const val MAX_STATIC_MS = 8_000L
    const val MAX_SILENT_GAP_MS = 2_000L
    const val HOOK_MS = 3_000L
    const val ENGAGE_WINDOW_START_MS = 40_000L
    const val ENGAGE_WINDOW_END_MS = 75_000L
    const val MAX_SIMULTANEOUS_MOVERS = 3

    // Safe zones (fractions of the 1080x1920 frame) reserved for platform UI.
    const val SAFE_TOP = 0.12f
    const val SAFE_BOTTOM = 0.20f
    const val SAFE_RIGHT = 0.14f
    const val SAFE_LEFT = 0.04f

    const val WIDTH = 1080
    const val HEIGHT = 1920
    const val FPS = 30
}

/** Procedural SFX ids (stable; referenced by scenarios). */
object SfxIds {
    const val KEY_TAP = "key_tap"
    const val MSG_SEND = "msg_send"
    const val MSG_RECV = "msg_recv"
    const val POP = "pop"
    const val SOFT_WHOOSH = "soft_whoosh"
    const val SUCCESS_CHIME = "success_chime"
    const val GENTLE_ERROR = "gentle_error"
    const val HEART_PULSE = "heart_pulse"
    const val TICK = "tick"
    const val HOOK_ENTER = "hook_enter"
    const val RUN_START = "run_start"
    const val TYPE_LOOP = "type_loop"
    const val NONE = "none"

    val ALL = listOf(
        KEY_TAP, MSG_SEND, MSG_RECV, POP, SOFT_WHOOSH, SUCCESS_CHIME, GENTLE_ERROR,
        HEART_PULSE, TICK, HOOK_ENTER, RUN_START, TYPE_LOOP, NONE,
    )
}

object AmbientMoods {
    val ALL = listOf("calm_pad", "soft_rain", "warm_keys", "none")
}

object Themes {
    const val DARK = "calm-dark"
    const val LIGHT = "calm-light"
    val ALL = listOf(DARK, LIGHT)
}

object Transitions {
    val ALL = listOf("fade", "slide", "zoom")
}

object EngageActions {
    val ALL = listOf("like", "follow", "share")
}

/** Personality sheet of a cast member (used in the prompt, the colours and the TTS voice). */
data class Persona(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val accentDark: Long,
    val accentLight: Long,
    val personality: String,
    val style: String,
    val dos: List<String>,
    val donts: List<String>,
    val samples: List<String>,
    val pitch: Float,
    val rate: Float,
    val monogram: String,
)

/**
 * Cast sheets. Ahmed (the learner) is the only built-in character. A teacher is whatever language
 * the creator adds: its colour, voice and monogram are derived from its name, so there is no
 * language list anywhere.
 */
object Personas {
    val AHMED = Persona(
        id = "ahmed", nameAr = "أحمد", nameEn = "Ahmed",
        accentDark = 0xFF8FB8DE, accentLight = 0xFF3D6A95,
        personality = "متعلّم مبتدئ، فضولي، يرتبك أحيانًا لكنه ليس غبيًا أبدًا.",
        style = "أسئلة قصيرة صادقة كما يسألها أي مبتدئ؛ يحاول ويخطئ ويفرح حين يفهم.",
        dos = listOf("يسأل أسئلة حقيقية", "يجرّب قبل أن يسأل", "يعبّر عن فرحه بالفهم"),
        donts = listOf("لا يسخر من نفسه", "لا يبدو غبيًا", "لا يستخدم مصطلحات متقدمة لم يتعلمها"),
        samples = listOf("طيب، يعني المتغير مثل الصندوق؟", "جرّبت وطلع لي خطأ… ليش؟", "آه! فهمت الآن 😊"),
        pitch = 1.08f, rate = 1.0f, monogram = "أ",
    )

    const val DEFAULT_PERSONALITY = "معلّم صبور ودود، يشرح بجمل قصيرة وتشبيهات من الحياة."

    /** A teacher persona for any language [language] shown as [displayName]. */
    fun forLanguage(language: String?, displayName: String? = null, note: String? = null): Persona {
        val id = Languages.slug(language ?: displayName)
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: language?.trim()?.takeIf { it.isNotEmpty() } ?: "معلّم"
        val h = (id.fold(7) { a, c -> a * 31 + c.code } and 0x7FFFFFFF)
        val hue = (h % 360).toFloat()
        return Persona(
            id, name, name, hsv(hue, 0.42f, 0.93f), hsv(hue, 0.65f, 0.55f),
            note?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_PERSONALITY,
            "جمل قصيرة دافئة، تشبيه من الحياة أولًا ثم الكود.",
            listOf("يطمئن أحمد", "يستخدم تشبيهات يومية", "يمدح المحاولة"),
            listOf("لا يستعجل", "لا يسخر", "لا يستخدم مصطلحات بلا شرح"),
            listOf("سؤال ممتاز يا أحمد!"),
            0.88f + (h / 7 % 25) / 100f, 0.94f + (h / 13 % 14) / 100f,
            monogramOf(name),
        )
    }

    fun forCast(member: CastMember): Persona =
        if (member.role == "learner") AHMED else forLanguage(member.language ?: member.id, member.name)

    private fun monogramOf(name: String): String {
        val n = name.trim()
        if (n.length <= 3) return n
        val first = n.first()
        return if (first.code < 128) n.take(2).replaceFirstChar { it.uppercase() } else first.toString()
    }

    private fun hsv(h: Float, s: Float, v: Float): Long {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f); h < 120 -> Triple(x, c, 0f); h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c); h < 300 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
