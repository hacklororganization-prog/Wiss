package studio.pyreels.core.validate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import studio.pyreels.core.Resources
import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.ScenarioBatch
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.typeId
import studio.pyreels.core.schema.MiniJsonSchema

enum class Severity { ERROR, WARNING, INFO }

/** A human-friendly problem report. `where` points to the exact scene/field in Arabic. */
data class Problem(
    val severity: Severity,
    val code: String,
    val message: String,
    val where: String = "",
    val sceneIndex: Int? = null,
    val field: String? = null,
    val episode: Int? = null,
    val fix: String? = null,
) {
    fun render(): String = buildString {
        append(if (severity == Severity.ERROR) "✖ " else if (severity == Severity.WARNING) "⚠ " else "ℹ ")
        if (where.isNotEmpty()) append(where).append(": ")
        append(message)
    }
}

data class ParseResult(
    val scenarios: List<Scenario>,
    val problems: List<Problem>,
    val isBatch: Boolean,
) {
    val ok: Boolean get() = scenarios.isNotEmpty() && problems.none { it.severity == Severity.ERROR }
    val errors get() = problems.filter { it.severity == Severity.ERROR }
    val warnings get() = problems.filter { it.severity == Severity.WARNING }
}

/**
 * Parses and validates a scenario file (single or batch). Validation has three layers:
 * 1. JSON syntax, 2. JSON Schema (structure, limits), 3. semantic rules (order, cast references,
 * one concept, durations, line lengths).
 */
object ScenarioValidator {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
        classDiscriminator = "type"
    }

    private val schema by lazy { MiniJsonSchema.load(Resources.schemaText) }

    fun parse(text: String): ParseResult {
        val cleaned = stripFences(text.trim().removePrefix("﻿"))
        val element = try {
            json.parseToJsonElement(cleaned)
        } catch (e: Exception) {
            return ParseResult(emptyList(), listOf(syntaxProblem(cleaned, e)), false)
        }
        return parseElement(element)
    }

    fun parseElement(element: JsonElement): ParseResult {
        val problems = ArrayList<Problem>()
        val obj = element as? JsonObject
            ?: return ParseResult(emptyList(), listOf(Problem(Severity.ERROR, "notObject", "الملف يجب أن يكون كائن JSON يبدأ بـ { وينتهي بـ }.")), false)
        val isBatch = obj.containsKey("batch")
        schema.validate(obj).forEach { problems += humanize(it, isBatch, obj) }
        if (problems.any { it.severity == Severity.ERROR }) return ParseResult(emptyList(), problems, isBatch)

        val scenarios = try {
            if (isBatch) json.decodeFromJsonElement(ScenarioBatch.serializer(), obj).batch
            else listOf(json.decodeFromJsonElement(Scenario.serializer(), obj))
        } catch (e: Exception) {
            problems += Problem(Severity.ERROR, "decode", "تعذّر قراءة الملف: ${e.message?.take(160)}")
            return ParseResult(emptyList(), problems, isBatch)
        }
        scenarios.forEachIndexed { i, s ->
            problems += semantic(s).map { if (isBatch) it.copy(episode = i + 1, where = "الحلقة ${i + 1} ← ${it.where}".trimEnd(' ', '←')) else it }
        }
        if (isBatch) problems += batchRules(scenarios)
        return ParseResult(scenarios, problems, isBatch)
    }

    fun encode(s: Scenario): String = json.encodeToString(Scenario.serializer(), s)
    fun encodeBatch(b: ScenarioBatch): String = json.encodeToString(ScenarioBatch.serializer(), b)

    /** AIs often wrap JSON in markdown fences despite instructions; accept that. */
    internal fun stripFences(t: String): String {
        if (!t.startsWith("```")) return t
        return t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim().removeSuffix("```").trim()
    }

    private fun syntaxProblem(text: String, e: Exception): Problem {
        val msg = e.message ?: ""
        val offset = Regex("offset (\\d+)").find(msg)?.groupValues?.get(1)?.toIntOrNull()
        val where = if (offset != null) {
            val upTo = text.take(offset.coerceAtMost(text.length))
            val line = upTo.count { it == '\n' } + 1
            "السطر $line"
        } else ""
        return Problem(Severity.ERROR, "syntax", "خطأ في صياغة JSON (فاصلة أو قوس أو علامة تنصيص ناقصة).", where, fix = "تحقّق من الأقواس والفواصل حول هذا السطر.")
    }

    // ----- Arabic messages for schema issues -----

    private val fieldNames = mapOf(
        "text" to "النص", "from" to "المرسل", "code" to "الكود", "title" to "العنوان", "answer" to "الإجابة",
        "error" to "رسالة الخطأ", "explain" to "الشرح", "fix" to "الإصلاح", "question" to "السؤال", "output" to "المخرجات",
        "trace" to "التتبّع", "items" to "العناصر", "bridge" to "جملة العودة", "actions" to "الأزرار", "language" to "اللغة",
        "sfx" to "المؤثر الصوتي", "meta" to "البيانات العامة", "cast" to "الشخصيات", "scenes" to "المشاهد", "lesson" to "الدرس",
        "concept" to "المفهوم", "type" to "نوع المشهد", "schema" to "رقم الإصدار", "targetSeconds" to "المدة المستهدفة",
    )

    fun describePath(path: List<Any>, root: JsonObject?, isBatch: Boolean): Triple<String, Int?, Int?> {
        val parts = ArrayList<String>()
        var sceneIndex: Int? = null
        var episode: Int? = null
        var node: JsonElement? = root
        var i = 0
        while (i < path.size) {
            val p = path[i]
            val next = path.getOrNull(i + 1)
            when {
                p == "batch" && next is Int -> { episode = next + 1; parts += "الحلقة ${next + 1}"; node = (node as? JsonObject)?.get("batch")?.let { (it as? JsonArray)?.getOrNull(next) }; i += 2; continue }
                p == "scenes" && next is Int -> {
                    sceneIndex = next
                    val sceneObj = ((node as? JsonObject)?.get("scenes") as? JsonArray)?.getOrNull(next) as? JsonObject
                    val type = (sceneObj?.get("type") as? JsonPrimitive)?.content
                    parts += "المشهد ${next + 1}" + (type?.let { " ($it)" } ?: "")
                    node = sceneObj; i += 2; continue
                }
                p == "cast" && next is Int -> { parts += "الشخصية ${next + 1}"; node = ((node as? JsonObject)?.get("cast") as? JsonArray)?.getOrNull(next); i += 2; continue }
                p is Int -> parts += "العنصر ${p + 1}"
                else -> { parts += fieldNames[p.toString()]?.let { "$it ($p)" } ?: p.toString(); node = (node as? JsonObject)?.get(p.toString()) }
            }
            i++
        }
        return Triple(parts.joinToString(" ← "), sceneIndex, episode)
    }

    private fun humanize(issue: MiniJsonSchema.Issue, isBatch: Boolean, root: JsonObject): Problem {
        val (where, scene, episode) = describePath(issue.path, root, isBatch)
        val a = issue.args
        val msg = when (issue.code) {
            "required" -> "الحقل «${a["name"]}» مطلوب ولم يُكتب."
            "type" -> "النوع غير صحيح: المتوقع ${typeAr(a["expected"])} والموجود ${typeAr(a["actual"])}."
            "const" -> "القيمة يجب أن تكون ${a["expected"]}."
            "enum" -> "القيمة ${a["actual"]} غير مسموحة. المسموح: ${a["allowed"]}."
            "minLength" -> "النص فارغ أو قصير جدًا."
            "maxLength" -> "النص طويل جدًا (${a["actual"]} حرفًا، الحد ${a["max"]}). قسّمه أو اختصره."
            "minItems" -> "عدد العناصر قليل (${a["actual"]}، الحد الأدنى ${a["min"]})."
            "maxItems" -> "عدد العناصر كثير (${a["actual"]}، الحد الأقصى ${a["max"]})."
            "minimum" -> "القيمة ${a["actual"]} أصغر من المسموح (${a["min"]})."
            "maximum" -> "القيمة ${a["actual"]} أكبر من المسموح (${a["max"]})."
            "pattern" -> "الصيغة غير صحيحة (حروف إنجليزية صغيرة وأرقام وشرطة فقط)."
            "uniqueItems" -> "توجد عناصر مكررة."
            "maxProperties" -> "عدد المتغيرات كبير (${a["actual"]}، الحد ${a["max"]})."
            "unknownField" -> "حقل غير معروف «${a["name"]}» سيتم تجاهله."
            "oneOfAmbiguous" -> "تعذّر تحديد نوع الكائن."
            else -> issue.code
        }
        val sev = if (issue.severity == MiniJsonSchema.Severity.WARNING) Severity.WARNING else Severity.ERROR
        val field = issue.path.lastOrNull { it is String }?.toString()
        return Problem(sev, "schema.${issue.code}", msg, where, scene, field, episode)
    }

    private fun typeAr(t: String?) = when (t) {
        "string" -> "نص"; "integer" -> "عدد صحيح"; "number" -> "رقم"; "boolean" -> "صح/خطأ (true/false)"
        "array" -> "قائمة [ ]"; "object" -> "كائن { }"; "null" -> "فارغ (null)"; else -> t ?: ""
    }

    // ----- Semantic rules -----

    fun semantic(s: Scenario): List<Problem> {
        val p = ArrayList<Problem>()
        val castIds = s.cast.map { it.id }.toSet()
        if (s.cast.count { it.role == "learner" } != 1 || s.cast.none { it.id == "ahmed" }) {
            p += Problem(Severity.ERROR, "cast.ahmed", "يجب أن يوجد أحمد (id = \"ahmed\" و role = \"learner\") مرة واحدة فقط.", "الشخصيات")
        }
        val teachers = s.cast.count { it.role == "teacher" }
        if (teachers !in 1..Limits.MAX_TEACHERS) {
            p += Problem(Severity.ERROR, "cast.teachers", "عدد المعلّمين يجب أن يكون من 1 إلى ${Limits.MAX_TEACHERS} (الموجود $teachers).", "الشخصيات")
        }
        if (castIds.size != s.cast.size) p += Problem(Severity.ERROR, "cast.duplicate", "يوجد معرّف شخصية مكرر.", "الشخصيات")

        val concepts = s.meta.lesson.concept.split(',', '،', '+', '&', '/').map { it.trim() }.filter { it.isNotEmpty() }
        if (concepts.size > 1 || s.meta.lesson.concept.contains(" و ")) {
            p += Problem(Severity.ERROR, "lesson.oneConcept", "الدرس يجب أن يحتوي مفهومًا واحدًا فقط (الموجود: ${s.meta.lesson.concept}).", "البيانات العامة ← الدرس ← المفهوم (concept)")
        }
        if (s.meta.targetSeconds > Limits.HARD_CAP_SECONDS) {
            p += Problem(Severity.ERROR, "meta.cap", "المدة المستهدفة تتجاوز الحد الأقصى ${Limits.HARD_CAP_SECONDS} ثانية.")
        }

        fun where(i: Int, sc: Scene, f: String? = null) = "المشهد ${i + 1} (${sc.typeId()})" + (f?.let { " ← $it" } ?: "")

        s.scenes.forEachIndexed { i, sc ->
            val refs = when (sc) {
                is Scene.Chat -> listOf("from" to sc.from)
                is Scene.EngageMoment -> listOf("from" to sc.from)
                is Scene.Analogy -> listOfNotNull(sc.from?.let { "from" to it })
                is Scene.Reveal -> listOfNotNull(sc.from?.let { "from" to it })
                is Scene.CommonMistake -> listOfNotNull(sc.from?.let { "from" to it })
                else -> emptyList()
            }
            refs.forEach { (f, id) ->
                if (id !in castIds) p += Problem(Severity.ERROR, "ref.cast", "الشخصية «$id» غير موجودة في قائمة الشخصيات (cast).", where(i, sc, f), i, f)
            }
            if (sc is Scene.Chat) {
                val lines = wrapCount(sc.text)
                if (lines > Limits.MAX_LINES_PER_BUBBLE) {
                    p += Problem(Severity.WARNING, "bubble.lines", "الفقاعة طويلة ($lines أسطر، الأفضل ${Limits.MAX_LINES_PER_BUBBLE} أو أقل). قسّمها إلى رسالتين.", where(i, sc, "text"), i, "text", fix = "split")
                }
            }
            if (sc is Scene.KeySentence) {
                val words = sc.text.split(Regex("\\s+")).count { it.isNotBlank() }
                if (words > Limits.MAX_KEY_SENTENCE_WORDS) {
                    p += Problem(Severity.WARNING, "key.words", "جملة اليوم طويلة ($words كلمة، الحد ${Limits.MAX_KEY_SENTENCE_WORDS}).", where(i, sc, "text"), i, "text")
                }
            }
            val code = when (sc) {
                is Scene.CodeType -> sc.code; is Scene.HookPredict -> sc.code; is Scene.CommonMistake -> sc.code; is Scene.Run -> sc.code; else -> null
            }
            if (code != null) {
                val lines = code.lines()
                if (lines.size > Limits.MAX_CODE_LINES) p += Problem(Severity.WARNING, "code.lines", "الكود طويل (${lines.size} سطرًا، الحد ${Limits.MAX_CODE_LINES}).", where(i, sc, "code"), i, "code")
                val longest = lines.maxOf { it.length }
                if (longest > Limits.MAX_CODE_LINE_CHARS) p += Problem(Severity.WARNING, "code.width", "سطر كود عريض ($longest حرفًا، الأفضل ${Limits.MAX_CODE_LINE_CHARS}) سيصغُر الخط.", where(i, sc, "code"), i, "code")
            }
            if (sc is Scene.CodeType) {
                val n = sc.code.lines().size
                sc.focusLines.filter { it > n }.forEach {
                    p += Problem(Severity.WARNING, "focus.range", "سطر التركيز $it غير موجود في الكود (عدد الأسطر $n).", where(i, sc, "focusLines"), i, "focusLines")
                }
            }
            if (sc is Scene.Run) {
                val code2 = sc.code ?: s.scenes.take(i).filterIsInstance<Scene.CodeType>().lastOrNull()?.code
                if (code2 == null) p += Problem(Severity.ERROR, "run.code", "مشهد التشغيل يحتاج كودًا: أضف code أو ضع مشهد code_type قبله.", where(i, sc), i, "code")
                else {
                    val n = code2.lines().size
                    sc.trace.forEachIndexed { k, t ->
                        if (t.line > n) p += Problem(Severity.ERROR, "trace.line", "خطوة التتبّع ${k + 1} تشير للسطر ${t.line} والكود فيه $n أسطر فقط.", where(i, sc, "trace"), i, "trace")
                    }
                }
                if (sc.exec == "real" && sc.language != "python") {
                    p += Problem(Severity.WARNING, "run.exec", "التنفيذ الحقيقي متاح لبايثون فقط؛ سيُعرض هذا المشهد بشكل محاكاة.", where(i, sc, "exec"), i, "exec")
                }
                if (sc.output.lines().size > Limits.MAX_OUTPUT_LINES) p += Problem(Severity.WARNING, "run.output", "المخرجات طويلة؛ ستظهر آخر ${Limits.MAX_OUTPUT_LINES} أسطر فقط.", where(i, sc, "output"), i, "output")
            }
        }
        p += structure(s)
        return p
    }

    /** The fixed video structure (section 1 of the brief). */
    fun structure(s: Scenario): List<Problem> {
        val p = ArrayList<Problem>()
        val types = s.scenes.map { it.typeId() }
        fun count(t: String) = types.count { it == t }
        if (types.firstOrNull() != "hook_predict") p += Problem(Severity.ERROR, "order.hook", "المشهد الأول يجب أن يكون hook_predict (توقّع قبل أن أشغّل).", "المشاهد", 0, fix = "reorder")
        if (count("hook_predict") != 1) p += Problem(Severity.ERROR, "count.hook", "يجب أن يوجد مشهد hook_predict واحد فقط.", "المشاهد")
        if (types.lastOrNull() != "teaser") p += Problem(Severity.ERROR, "order.teaser", "المشهد الأخير يجب أن يكون teaser (تشويق للحلقة القادمة).", "المشاهد", types.lastIndex, fix = "reorder")
        if (count("teaser") != 1) p += Problem(Severity.ERROR, "count.teaser", "يجب أن يوجد مشهد teaser واحد فقط.", "المشاهد")
        if (count("engage_moment") != 1) p += Problem(Severity.ERROR, "count.engage", "يجب أن توجد لحظة تفاعل engage_moment واحدة بالضبط.", "المشاهد")
        if (count("key_sentence") != 1) p += Problem(Severity.ERROR, "count.key", "يجب أن توجد جملة اليوم key_sentence واحدة بالضبط.", "المشاهد")
        if (count("reveal") != 1) p += Problem(Severity.ERROR, "count.reveal", "يجب أن يوجد مشهد reveal واحد يكشف إجابة الهوك.", "المشاهد")
        if (count("recap") > 1) p += Problem(Severity.ERROR, "count.recap", "مشهد recap واحد فقط.", "المشاهد")
        if (count("recap") == 1 && types.indexOf("recap") != 1) p += Problem(Severity.WARNING, "order.recap", "الأفضل أن يأتي recap مباشرة بعد الهوك.", "المشاهد", types.indexOf("recap"), fix = "reorder")
        if (count("common_mistake") > 1) p += Problem(Severity.WARNING, "count.mistake", "خطأ شائع واحد يكفي في الحلقة.", "المشاهد")
        if (count("run") == 0) p += Problem(Severity.WARNING, "count.run", "لا يوجد مشهد تشغيل run؛ التتبّع المرئي أقوى أداة تعليمية.", "المشاهد")
        val firstRun = types.indexOf("run")
        val reveal = types.indexOf("reveal")
        if (firstRun >= 0 && reveal in 0 until firstRun) p += Problem(Severity.WARNING, "order.reveal", "الأفضل كشف الإجابة بعد أول تشغيل للكود.", "المشاهد", reveal, fix = "reorder")
        val key = types.indexOf("key_sentence")
        if (key >= 0 && key < types.lastIndex - 2) p += Problem(Severity.WARNING, "order.key", "جملة اليوم تأتي قرب النهاية قبل التشويق.", "المشاهد", key, fix = "reorder")
        val ahmedTalks = s.scenes.any { it is Scene.Chat && it.from == "ahmed" }
        if (!ahmedTalks) p += Problem(Severity.WARNING, "ahmed.silent", "أحمد لم يسأل أي سؤال؛ أسئلته هي قلب الحلقة.", "المشاهد")
        val hasChallenge = s.scenes.any { (it is Scene.Chat && it.challenge) || (it is Scene.Teaser && !it.challenge.isNullOrBlank()) }
        if (!hasChallenge) p += Problem(Severity.INFO, "challenge.missing", "لا يوجد تحدٍّ للتعليقات (challenge).", "المشاهد")
        return p
    }

    private fun batchRules(list: List<Scenario>): List<Problem> {
        val p = ArrayList<Problem>()
        for (i in 0 until list.size - 1) {
            val teaser = list[i].scenes.filterIsInstance<Scene.Teaser>().firstOrNull()?.text ?: continue
            val recap = list[i + 1].scenes.filterIsInstance<Scene.Recap>().firstOrNull()?.text
            if (recap == null) p += Problem(Severity.WARNING, "batch.recap", "الحلقة ${i + 2} بلا recap يربطها بالحلقة ${i + 1}.", "الحلقة ${i + 2}")
            if (teaser.isBlank()) p += Problem(Severity.WARNING, "batch.teaser", "تشويق الحلقة ${i + 1} فارغ.", "الحلقة ${i + 1}")
        }
        val ids = list.map { it.meta.lesson.id }
        if (ids.toSet().size != ids.size) p += Problem(Severity.WARNING, "batch.duplicate", "يوجد درس مكرر داخل الخطة الأسبوعية.")
        val mistakes = list.flatMap { s -> s.scenes.filterIsInstance<Scene.CommonMistake>().map { it.error } }
        if (mistakes.toSet().size != mistakes.size) p += Problem(Severity.WARNING, "batch.mistakeRepeat", "نفس الخطأ الشائع مكرر في أكثر من حلقة.")
        val analogies = list.flatMap { s -> s.scenes.filterIsInstance<Scene.Analogy>().map { it.title } }
        if (analogies.toSet().size != analogies.size) p += Problem(Severity.WARNING, "batch.analogyRepeat", "نفس التشبيه مكرر في أكثر من حلقة.")
        return p
    }

    /** Approximate number of rendered lines of a bubble text (greedy word wrap). */
    fun wrapCount(text: String, maxChars: Int = Limits.MAX_CHARS_PER_LINE): Int =
        text.split('\n').sumOf { para -> wrap(para, maxChars).size.coerceAtLeast(1) }

    fun wrap(text: String, maxChars: Int): List<String> {
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            if (line.isNotEmpty() && line.length + 1 + word.length > maxChars) {
                out += line.toString(); line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }
}

/** Convenience for field values used in prompts/repair. */
internal fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.jsonPrimitive?.content
