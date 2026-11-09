package studio.pyreels.core.templates

import kotlinx.serialization.json.JsonPrimitive
import studio.pyreels.core.brief.Brief
import studio.pyreels.core.brief.EpisodeRecord
import studio.pyreels.core.brief.SeriesMemory
import studio.pyreels.core.content.ContentRepository
import studio.pyreels.core.content.CurriculumLesson
import studio.pyreels.core.model.CastMember
import studio.pyreels.core.model.Lesson
import studio.pyreels.core.model.Meta
import studio.pyreels.core.model.Personas
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.Series
import studio.pyreels.core.model.TraceStep
import studio.pyreels.core.model.VarBox
import studio.pyreels.core.validate.ScenarioValidator

/**
 * Built-in scene templates. They produce complete, valid scenarios from the roadmap (no AI needed)
 * and turn a pasted plain explanation into proposed scenes ("Paste lesson text" mode).
 */
object Templates {

    fun cast(teachers: List<String>): List<CastMember> =
        listOf(CastMember("ahmed", "learner", "أحمد")) +
            teachers.distinct().take(4).map { id -> Personas.forLanguage(id).let { CastMember(it.id, "teacher", it.nameAr, it.id) } }

    /** A complete draft scenario for a roadmap lesson, following the fixed structure. */
    fun draft(lesson: CurriculumLesson, brief: Brief = Brief(), history: List<EpisodeRecord> = emptyList()): Scenario {
        val b = brief.normalized()
        val teachers = b.teachers
        val t1 = teachers.first()
        val t2 = teachers.getOrNull(1)
        val level = SeriesMemory.ahmedLevel(history.size)
        val prev = ContentRepository.curriculum.previous(lesson.id)
        val recap = SeriesMemory.recapFor(history) ?: prev?.let { "تذكير: ${it.keySentence}" } ?: "أهلًا بك في أول حلقة! نتعلّم البرمجة خطوة بخطوة."
        val code = if (b.exampleMode == "custom" && b.customExample.isNotBlank()) b.customExample.trim() else lesson.example
        val hookCode = code.lines().take(4).joinToString("\n")
        val output = lesson.expectedOutput
        val mistake = ContentRepository.errors.relevant(lesson.id, ContentRepository.curriculum).firstOrNull { e -> history.none { it.mistake == e.message } }
        val say = b.voiceOver
        val analogyBoxes = analogyItems(code)
        val teaserText = if (b.teaserMode == "custom" && b.customTeaser.isNotBlank()) b.customTeaser else lesson.teaser

        val scenes = ArrayList<Scene>()
        scenes += Scene.HookPredict(code = hookCode)
        scenes += Scene.Recap(recap.take(118), say = say)
        scenes += Scene.Chat("ahmed", SeriesMemory.suggestedQuestion(level, lesson.title, history.lastOrNull()?.title).take(120))
        scenes += Scene.Chat(t1, "سؤال ممتاز يا أحمد! ${lesson.summary}".take(130), say = say)
        scenes += Scene.Chat("ahmed", "طيب، كيف أتخيّل ذلك؟")
        scenes += Scene.Analogy(title = lesson.analogy.take(46), items = analogyBoxes, from = t1, text = "تخيّلها هكذا، وسيصبح الكود سهلًا.", say = say)
        scenes += Scene.Chat("ahmed", "آه! بدأت أفهم 😊", reactions = listOf("heart"))
        if (t2 != null) scenes += Scene.Chat(t2, comparisonLine(t2, lesson).take(130))
        scenes += Scene.Chat(t1, "لنكتبها معًا بالكود.")
        scenes += Scene.CodeType(code = code, focusLines = listOf(1), typo = b.methods.contains("mistake_fixing"), caption = "تابع كل سطر بهدوء")
        scenes += Scene.Run(exec = "real", trace = simpleTrace(code), output = output)
        scenes += Scene.Chat("ahmed", "اشتغل! ظهرت النتيجة 🎉")
        scenes += Scene.EngageMoment(
            from = t1,
            text = if (b.tone == "funny") "أحمد صار مبرمجًا! إن أعجبك الدرس ادعمنا بلايك 😄" else "أحسنت يا أحمد! إن استفدت، تابعنا لنكمل الرحلة معًا.",
            bridge = "حسنًا يا أحمد، نكمل!",
        )
        scenes += Scene.Reveal(answer = output.lines().firstOrNull()?.take(78) ?: "✓", text = "هذه إجابة سؤال البداية! هل توقّعتها؟", from = t1, say = say)
        if (b.includeMistake && mistake != null) {
            scenes += Scene.Chat("ahmed", "وماذا لو أخطأت في الكتابة؟")
            scenes += Scene.CommonMistake(
                code = mistake.snippet, error = mistake.message.take(158), explain = mistake.explain.take(134), fix = mistake.fix,
                fixOutput = mistake.fixOutput, libraryId = mistake.id, from = t1, say = say,
            )
        }
        scenes += Scene.Chat(t1, "تذكّر هذه القاعدة دائمًا:")
        scenes += Scene.KeySentence(lesson.keySentence.take(68), say = say)
        if (b.includeChallenge) scenes += Scene.Chat("ahmed", "تحدٍّ لكم: غيّروا القيمة في الكود، ماذا سيُطبع؟ اكتبوا في التعليقات 👇", challenge = true)
        scenes += Scene.Teaser(teaserText.take(118), challenge = null, say = say)

        val draftScenario = Scenario(
            meta = Meta(
                title = lesson.title.take(58), language = if (b.languageStyle == "english") "en" else "ar",
                lesson = Lesson(lesson.id, lesson.concept, b.level), targetSeconds = b.targetSeconds, theme = b.theme,
                frame = b.frame, chatColor = b.chatColor, celebrate = b.celebrate,
                series = Series(name = "بايثون مع أحمد", episode = history.size + 1, previousRecap = recap.take(118), nextTeaser = teaserText.take(118)),
            ),
            cast = cast(teachers),
            scenes = scenes,
        )
        return draftScenario.copy(meta = draftScenario.meta.copy(publish = studio.pyreels.core.publish.PublishPack.localKit(draftScenario)))
    }

    private fun comparisonLine(teacher: String, lesson: CurriculumLesson): String = when (teacher) {
        "java" -> "وعندي في جافا نفس الفكرة، لكن أحب أن أكتب النوع أولًا."
        "cpp" -> "وأنا في سي++ أنفّذها أسرع من رمشة عين 😎"
        "javascript" -> "وعندي في جافاسكربت تعمل في المتصفح فورًا! 🎉"
        else -> "وعندي فكرة ${lesson.title} موجودة أيضًا بصيغة مختلفة قليلًا."
    }

    private fun analogyItems(code: String): List<VarBox> {
        val assigns = Regex("^\\s*([A-Za-z_]\\w*)\\s*=\\s*(.+)$", RegexOption.MULTILINE).findAll(code)
            .map { it.groupValues[1] to it.groupValues[2].trim() }.filter { it.second.length <= 16 }.distinctBy { it.first }.take(3).toList()
        return assigns.map { VarBox(it.first.take(16), it.second.take(16)) }
    }

    /** A simple line-by-line trace used until real execution replaces it. */
    fun simpleTrace(code: String): List<TraceStep> {
        val lines = code.lines()
        val vars = LinkedHashMap<String, String>()
        val steps = ArrayList<TraceStep>()
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            Regex("^([A-Za-z_]\\w*)\\s*=\\s*(-?\\d+)$").find(line)?.let { vars[it.groupValues[1]] = it.groupValues[2] }
            Regex("^([A-Za-z_]\\w*)\\s*=\\s*\"([^\"]*)\"$").find(line)?.let { vars[it.groupValues[1]] = "\"${it.groupValues[2]}\"" }
            steps += TraceStep(line = i + 1, vars = vars.mapValues { (_, v) -> if (v.startsWith("\"")) JsonPrimitive(v.trim('"')) else JsonPrimitive(v.toLong()) })
        }
        return steps.take(16)
    }

    /**
     * "Paste lesson text" mode: questions become Ahmed's messages, explanation sentences become the
     * teacher's messages, code-looking lines become a code_type + run pair. The fixed structural scenes
     * (hook, recap, engage, reveal, key sentence, teaser) are added around them.
     */
    fun fromPastedText(text: String, brief: Brief = Brief()): Scenario {
        val b = brief.normalized()
        val t1 = b.teachers.first()
        val codeLines = ArrayList<String>()
        val prose = ArrayList<String>()
        text.lines().forEach { l ->
            val trimmed = l.trimEnd()
            if (trimmed.isBlank()) return@forEach
            if (studio.pyreels.core.speech.SpeakCode.looksLikeCode(trimmed) || trimmed.startsWith("    ")) codeLines += trimmed else prose += trimmed
        }
        val sentences = prose.joinToString(" ").split(Regex("(?<=[.!؟?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val code = codeLines.joinToString("\n").ifBlank { "print(\"مرحبا\")" }
        val chats = ArrayList<Scene>()
        sentences.take(10).forEach { s ->
            val from = if (s.endsWith("؟") || s.endsWith("?")) "ahmed" else t1
            ScenarioValidator.wrap(s, 120).forEach { part -> chats += Scene.Chat(from, part.take(130)) }
        }
        if (chats.none { (it as Scene.Chat).from == "ahmed" }) chats.add(0, Scene.Chat("ahmed", "ما معنى ${b.lessonTitle.ifBlank { "هذا" }}؟"))
        val firstSentence = sentences.firstOrNull { !it.endsWith("؟") } ?: "قاعدة اليوم"
        val scenes = ArrayList<Scene>()
        scenes += Scene.HookPredict(code = code.lines().take(4).joinToString("\n"))
        scenes += Scene.Recap("في الحلقة الماضية تعلّمنا خطوة جديدة.")
        scenes += chats.take(6)
        scenes += Scene.CodeType(code = code)
        scenes += Scene.Run(exec = "real", trace = simpleTrace(code), output = "")
        scenes += Scene.EngageMoment(from = t1, text = "رائع يا أحمد! إن أفادك الدرس تابعنا للمزيد.")
        scenes += chats.drop(6)
        scenes += Scene.Reveal(answer = "شاهد المخرجات", text = "هذه نتيجة الكود الذي رأيته في البداية.", from = t1)
        scenes += Scene.KeySentence(firstSentence.split(' ').take(10).joinToString(" ").take(68))
        scenes += Scene.Teaser("في الحلقة القادمة نكمل خطوة جديدة!")
        val title = b.lessonTitle.ifBlank { "درس جديد" }
        return Scenario(
            meta = Meta(title.take(58), lesson = Lesson(b.lessonId ?: "custom-lesson", "concept"), targetSeconds = b.targetSeconds, theme = b.theme),
            cast = cast(b.teachers), scenes = scenes,
        )
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
