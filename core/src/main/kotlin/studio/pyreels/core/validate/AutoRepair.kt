package studio.pyreels.core.validate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import studio.pyreels.core.model.AmbientMoods
import studio.pyreels.core.model.SCENE_TYPES
import studio.pyreels.core.model.SfxIds
import studio.pyreels.core.model.Themes

/**
 * Safe auto-repair for trivial issues. Never invents content: it trims, normalises ids, maps
 * common aliases, drops unknown fields and fixes the order of the fixed structural scenes.
 */
object AutoRepair {

    data class Result(val text: String, val applied: List<String>)

    private val typeAliases = mapOf(
        "hook" to "hook_predict", "predict" to "hook_predict", "message" to "chat", "msg" to "chat",
        "code" to "code_type", "typing" to "code_type", "execute" to "run", "exec" to "run",
        "engage" to "engage_moment", "cta" to "engage_moment", "answer" to "reveal",
        "mistake" to "common_mistake", "error" to "common_mistake", "key" to "key_sentence",
        "sentence_of_the_day" to "key_sentence", "next" to "teaser",
    )

    private val sceneKeys = mapOf(
        "hook_predict" to setOf("language", "code", "question", "holdMs"),
        "recap" to setOf("text"),
        "chat" to setOf("from", "text", "code", "reactions", "challenge"),
        "analogy" to setOf("title", "items", "from", "text"),
        "code_type" to setOf("language", "code", "typing", "focusLines", "typo", "caption"),
        "run" to setOf("language", "exec", "code", "trace", "output", "error", "caption"),
        "engage_moment" to setOf("from", "text", "actions", "bridge"),
        "reveal" to setOf("answer", "text", "from"),
        "common_mistake" to setOf("language", "code", "error", "explain", "fix", "fixOutput", "libraryId", "from"),
        "key_sentence" to setOf("text"),
        "teaser" to setOf("text", "challenge"),
    ).mapValues { it.value + setOf("type", "durationMs", "say", "sfx", "spoken", "speakCode", "transition") }

    private val langAliases = mapOf("c++" to "cpp", "js" to "javascript", "py" to "python", "c#" to "csharp", "ts" to "typescript", "golang" to "go")

    fun repair(text: String): Result {
        val applied = ArrayList<String>()
        val cleaned = ScenarioValidator.stripFences(text.trim().removePrefix("﻿"))
        if (cleaned != text.trim()) applied += "أُزيلت علامات ``` المحيطة بالـ JSON."
        val root = try {
            ScenarioValidator.json.parseToJsonElement(cleaned)
        } catch (e: Exception) {
            return Result(cleaned, applied)
        } as? JsonObject ?: return Result(cleaned, applied)

        val fixed = if (root.containsKey("batch")) {
            val batch = (root["batch"] as? JsonArray)?.map { (it as? JsonObject)?.let { s -> repairScenario(s, applied) } ?: it }
            JsonObject(root + ("schema" to JsonPrimitive("pyreels/1")) + ("batch" to JsonArray(batch ?: emptyList())))
        } else repairScenario(root, applied)
        return Result(ScenarioValidator.json.encodeToString(JsonElement.serializer(), fixed), applied.distinct())
    }

    private fun repairScenario(s: JsonObject, applied: MutableList<String>): JsonObject {
        val m = s.toMutableMap()
        if (m["schema"] != JsonPrimitive("pyreels/1")) { m["schema"] = JsonPrimitive("pyreels/1"); applied += "ضُبط رقم الإصدار إلى pyreels/1." }
        (m["meta"] as? JsonObject)?.let { meta ->
            val mm = trimAll(meta).toMutableMap()
            (mm["theme"] as? JsonPrimitive)?.content?.let { if (it !in Themes.ALL) { mm["theme"] = JsonPrimitive(Themes.DARK); applied += "المظهر غير معروف → calm-dark." } }
            (mm["ambient"] as? JsonPrimitive)?.content?.let { if (it !in AmbientMoods.ALL) { mm["ambient"] = JsonPrimitive("calm_pad"); applied += "خلفية صوتية غير معروفة → calm_pad." } }
            (mm["targetSeconds"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.let { v ->
                val c = v.toInt().coerceIn(30, 180)
                if (c.toDouble() != v || mm["targetSeconds"].toString().contains('"')) { mm["targetSeconds"] = JsonPrimitive(c); applied += "المدة المستهدفة ضُبطت إلى $c ثانية." }
            }
            (mm["publish"] as? JsonObject)?.let { pub ->
                val pm = pub.toMutableMap()
                for (k in listOf("tiktokHashtags", "instagramHashtags")) {
                    val arr = pm[k] as? JsonArray ?: continue
                    val tags = arr.mapNotNull { (it as? JsonPrimitive)?.content?.let { t -> studio.pyreels.core.publish.PublishPack.normalizeTag(t) } }.distinct().take(5)
                    if (tags != arr.map { (it as? JsonPrimitive)?.content }) applied += "نُسّقت الهاشتاغات في $k."
                    pm[k] = JsonArray(tags.map { JsonPrimitive(it) })
                }
                mm["publish"] = JsonObject(pm)
            }
            m["meta"] = JsonObject(mm)
        }
        (m["cast"] as? JsonArray)?.let { cast ->
            val list = cast.map { c ->
                val o = (c as? JsonObject)?.let { trimAll(it) } ?: return@map c
                val id = (o["id"] as? JsonPrimitive)?.content
                if (id != null && id != normId(id)) { applied += "معرّف الشخصية «$id» → «${normId(id)}»."; JsonObject(o + ("id" to JsonPrimitive(normId(id)))) } else o
            }.toMutableList()
            if (list.none { (it as? JsonObject)?.get("id") == JsonPrimitive("ahmed") }) {
                list.add(0, JsonObject(mapOf("id" to JsonPrimitive("ahmed"), "role" to JsonPrimitive("learner"), "name" to JsonPrimitive("أحمد"))))
                applied += "أُضيف أحمد إلى الشخصيات."
            }
            m["cast"] = JsonArray(list)
        }
        (m["scenes"] as? JsonArray)?.let { scenes ->
            var list = scenes.map { sc -> (sc as? JsonObject)?.let { repairScene(trimAll(it), applied) } ?: sc }
            list = reorder(list, applied)
            m["scenes"] = JsonArray(list)
        }
        val allowedTop = setOf("schema", "meta", "cast", "scenes")
        m.keys.filter { it !in allowedTop }.forEach { m.remove(it); applied += "حُذف الحقل غير المعروف «$it»." }
        return JsonObject(m)
    }

    private fun normId(id: String) = id.trim().lowercase().replace(' ', '_').replace("c++", "cpp").replace("c#", "csharp")
        .let { if (it == "js") "javascript" else it }

    private fun repairScene(o: JsonObject, applied: MutableList<String>): JsonObject {
        val m = o.toMutableMap()
        val type = (m["type"] as? JsonPrimitive)?.content?.trim()?.lowercase()
        if (type != null && type !in SCENE_TYPES) {
            typeAliases[type]?.let { m["type"] = JsonPrimitive(it); applied += "نوع المشهد «$type» → «$it»." }
        }
        (m["sfx"] as? JsonPrimitive)?.content?.let { if (it !in SfxIds.ALL) { m.remove("sfx"); applied += "مؤثر صوتي غير معروف «$it» → الافتراضي." } }
        (m["language"] as? JsonPrimitive)?.content?.let { l -> langAliases[l.lowercase()]?.let { m["language"] = JsonPrimitive(it) } ?: if (l != l.lowercase()) m["language"] = JsonPrimitive(l.lowercase()) else Unit }
        (m["from"] as? JsonPrimitive)?.content?.let { if (it != normId(it)) m["from"] = JsonPrimitive(normId(it)) }
        val t = (m["type"] as? JsonPrimitive)?.content
        sceneKeys[t]?.let { allowed ->
            m.keys.filter { it !in allowed }.forEach { k -> m.remove(k); applied += "حُذف الحقل غير المعروف «$k» من مشهد $t." }
        }
        m.entries.removeAll { it.value is JsonNull }
        return JsonObject(m)
    }

    private fun reorder(list: List<JsonElement>, applied: MutableList<String>): List<JsonElement> {
        fun typeOf(e: JsonElement) = ((e as? JsonObject)?.get("type") as? JsonPrimitive)?.content
        val out = list.toMutableList()
        val hook = out.indexOfFirst { typeOf(it) == "hook_predict" }
        if (hook > 0) { out.add(0, out.removeAt(hook)); applied += "نُقل الهوك إلى البداية." }
        val recap = out.indexOfFirst { typeOf(it) == "recap" }
        if (recap > 1 && typeOf(out[0]) == "hook_predict") { out.add(1, out.removeAt(recap)); applied += "نُقل مشهد التذكير بعد الهوك." }
        val teaser = out.indexOfFirst { typeOf(it) == "teaser" }
        if (teaser >= 0 && teaser != out.lastIndex) { out.add(out.removeAt(teaser)); applied += "نُقل التشويق إلى النهاية." }
        return out
    }

    private fun trimAll(o: JsonObject): JsonObject = JsonObject(o.mapValues { (k, v) -> trimValue(k, v) })

    private fun trimValue(key: String, v: JsonElement): JsonElement = when {
        v is JsonPrimitive && v.isString && key != "code" && key != "fix" && key != "output" -> {
            val t = v.content.trim().replace(Regex("[ \\t]{2,}"), " ")
            if (t != v.content) JsonPrimitive(t) else v
        }
        v is JsonPrimitive && v.isString -> JsonPrimitive(v.content.trimEnd().replace("\t", "    ").trimStart('\n'))
        else -> v
    }
}
