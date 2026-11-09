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

val CODE_LANGUAGES = listOf(
    "python", "java", "cpp", "javascript", "kotlin", "csharp", "go", "rust", "php", "typescript", "swift", "c",
)

/** Personality template of a cast member. */
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

    private val TEMPLATES = listOf(
        Persona(
            "python", "بايثون", "Python", 0xFFE8C872, 0xFF9A7414,
            "هادئ، ودود، يبسّط الأمور ويحب الكود النظيف.",
            "جمل قصيرة دافئة، تشبيه من الحياة أولًا ثم الكود.",
            listOf("يطمئن أحمد", "يستخدم تشبيهات يومية", "يمدح المحاولة"),
            listOf("لا يستعجل", "لا يستخدم مصطلحات بلا شرح"),
            listOf("سؤال ممتاز يا أحمد!", "تخيّل صندوقًا مكتوبًا عليه اسم.", "لا بأس، الخطأ جزء من التعلّم."),
            0.95f, 0.95f, "Py",
        ),
        Persona(
            "java", "جافا", "Java", 0xFFE0956B, 0xFFA34F22,
            "رسمي، منظّم، صارم قليلًا، يحب الأنواع والقواعد.",
            "جمل مرتّبة بنقاط، يذكر النوع دائمًا.",
            listOf("يشرح القاعدة بوضوح", "يقارن بلطف"),
            listOf("لا يقسو", "لا يطيل"),
            listOf("عندي يجب أن تعلن النوع أولًا: int x = 5;", "القواعد تحمينا من المفاجآت."),
            0.85f, 0.92f, "J",
        ),
        Persona(
            "cpp", "سي++", "C++", 0xFF8DA7E8, 0xFF3B5BB0,
            "سريع، واثق، فخور بالأداء، يتباهى بمرح.",
            "جمل قصيرة وسريعة مع مزاح خفيف.",
            listOf("يذكر السرعة بمرح", "يحترم الآخرين"),
            listOf("لا يتعالى", "لا يُربك المبتدئ"),
            listOf("وأنا أنفّذها أسرع من رمشة عين 😎", "عندي: int x = 5; وانتهينا."),
            0.9f, 1.08f, "C++",
        ),
        Persona(
            "javascript", "جافاسكربت", "JavaScript", 0xFFE3D36B, 0xFF8A7A10,
            "نشيط، غير رسمي، يمزح.",
            "عفوي ومرح، يستخدم أمثلة من المتصفح.",
            listOf("يضيف لمسة مرح", "يعطي مثالًا عمليًا"),
            listOf("لا يسخر", "لا يشتّت عن الفكرة"),
            listOf("عندي let x = 5 وبس! 🎉", "جرّبها في المتصفح فورًا."),
            1.1f, 1.08f, "JS",
        ),
        Persona(
            "kotlin", "كوتلن", "Kotlin", 0xFFB59AE8, 0xFF6A45B5,
            "عصري، مختصر، يحب الأمان.", "مختصر وواضح.", listOf("يذكر الأمان"), listOf("لا يتعالى"),
            listOf("val x = 5 — وثابتة للأبد."), 1.0f, 1.0f, "Kt",
        ),
        Persona(
            "csharp", "سي شارب", "C#", 0xFF9FD18B, 0xFF3F7A2C,
            "منظّم ولطيف.", "واضح ومرتّب.", listOf("يشرح بهدوء"), listOf("لا يطيل"),
            listOf("var x = 5; وأنا أعرف نوعه."), 0.92f, 0.98f, "C#",
        ),
        Persona(
            "go", "جو", "Go", 0xFF7FCFD6, 0xFF1F7A82,
            "بسيط، عملي.", "قصير جدًا.", listOf("يفضّل البساطة"), listOf("لا يعقّد"),
            listOf("x := 5 وخلاص."), 0.95f, 1.0f, "Go",
        ),
        Persona(
            "rust", "رست", "Rust", 0xFFD9A38A, 0xFF8F4A2E,
            "حريص، دقيق، يحب الأمان.", "دقيق ومهذّب.", listOf("يحذّر بلطف"), listOf("لا يخيف"),
            listOf("let x = 5; ولا أحد يغيّرها بلا إذن."), 0.88f, 0.96f, "Rs",
        ),
        Persona(
            "php", "بي إتش بي", "PHP", 0xFFA7A9DE, 0xFF4E519E,
            "خبير ويب ودود.", "عملي.", listOf("يربط بالويب"), listOf("لا يشتّت"),
            listOf("\$x = 5; وعلامة الدولار دائمًا معي."), 0.95f, 1.0f, "php",
        ),
    )

    /** Default template for an unknown language. */
    fun forLanguage(language: String?, displayName: String? = null): Persona {
        val l = language?.lowercase()?.replace("c++", "cpp")?.replace("js", "javascript")
        TEMPLATES.firstOrNull { it.id == l }?.let { return it }
        val name = displayName ?: language ?: "معلّم"
        return Persona(
            l ?: "teacher", name, language ?: "Teacher", 0xFFA9C4B8, 0xFF3F6E5B,
            "معلّم صبور ودود.", "جمل قصيرة وتشبيهات.", listOf("يشرح بهدوء"), listOf("لا يسخر"),
            listOf("سؤال جميل يا أحمد."), 1.0f, 1.0f, name.take(2),
        )
    }

    fun teachers(): List<Persona> = TEMPLATES

    fun forCast(member: CastMember): Persona =
        if (member.role == "learner") AHMED else forLanguage(member.language ?: member.id, member.name)
}
