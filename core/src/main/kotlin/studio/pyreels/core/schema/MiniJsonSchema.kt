package studio.pyreels.core.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A small, dependency-free JSON Schema (draft 2020-12 subset) validator.
 *
 * Supported keywords: `$ref` (local `#/$defs/...`), `type`, `const`, `enum`, `required`,
 * `properties`, `additionalProperties` (boolean or schema), `items`, `minItems`, `maxItems`,
 * `uniqueItems`, `minLength`, `maxLength`, `minimum`, `maximum`, `pattern`, `maxProperties`, `oneOf`.
 *
 * `oneOf` is resolved by discriminator when possible (a branch whose `const` properties match
 * the instance), which gives precise error locations for scene objects.
 *
 * Unknown properties (`additionalProperties: false`) are reported as [Severity.WARNING] because the
 * format is forward compatible: unknown fields are ignored with a warning.
 */
class MiniJsonSchema(private val root: JsonObject) {

    enum class Severity { ERROR, WARNING }

    data class Issue(
        val path: List<Any>,
        val code: String,
        val severity: Severity,
        val args: Map<String, String> = emptyMap(),
    )

    fun validate(instance: JsonElement): List<Issue> {
        val out = ArrayList<Issue>()
        check(root, instance, emptyList(), out)
        return out
    }

    private fun resolve(schema: JsonObject): JsonObject {
        val ref = (schema["\$ref"] as? JsonPrimitive)?.content ?: return schema
        require(ref.startsWith("#/")) { "Only local refs are supported: $ref" }
        var node: JsonElement = root
        for (part in ref.removePrefix("#/").split('/')) {
            node = (node as JsonObject)[part] ?: error("Unresolvable ref $ref")
        }
        return resolve(node as JsonObject)
    }

    private fun typeOf(e: JsonElement): String = when (e) {
        is JsonNull -> "null"
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonPrimitive -> when {
            e.isString -> "string"
            e.booleanOrNull != null -> "boolean"
            e.longOrNull != null -> "integer"
            e.doubleOrNull != null -> "number"
            else -> "string"
        }
    }

    private fun typeMatches(expected: String, actual: String) =
        expected == actual || (expected == "number" && actual == "integer")

    private fun check(schemaIn: JsonObject, inst: JsonElement, path: List<Any>, out: MutableList<Issue>) {
        val schema = resolve(schemaIn)

        schema["oneOf"]?.let { oneOf ->
            checkOneOf((oneOf as JsonArray).map { resolve(it as JsonObject) }, inst, path, out)
        }

        schema["type"]?.let { t ->
            val actual = typeOf(inst)
            val allowed = when (t) {
                is JsonArray -> t.map { it.jsonPrimitive.content }
                else -> listOf(t.jsonPrimitive.content)
            }
            if (allowed.none { typeMatches(it, actual) }) {
                out += Issue(path, "type", Severity.ERROR, mapOf("expected" to allowed.joinToString("|"), "actual" to actual))
                return
            }
        }

        schema["const"]?.let { c ->
            if (c != inst) out += Issue(path, "const", Severity.ERROR, mapOf("expected" to c.toString(), "actual" to inst.toString()))
        }
        schema["enum"]?.let { e ->
            val values = e as JsonArray
            if (values.none { it == inst }) {
                out += Issue(
                    path, "enum", Severity.ERROR,
                    mapOf("allowed" to values.joinToString(", ") { (it as? JsonPrimitive)?.content ?: it.toString() }, "actual" to inst.toString()),
                )
            }
        }

        when (inst) {
            is JsonObject -> checkObject(schema, inst, path, out)
            is JsonArray -> checkArray(schema, inst, path, out)
            is JsonPrimitive -> checkPrimitive(schema, inst, path, out)
            else -> Unit
        }
    }

    private fun checkOneOf(branches: List<JsonObject>, inst: JsonElement, path: List<Any>, out: MutableList<Issue>) {
        if (inst is JsonObject) {
            // Discriminator: branches whose const properties all match and whose required keys are present.
            val discriminated = branches.filter { b ->
                val props = b["properties"] as? JsonObject ?: return@filter false
                val consts = props.entries.filter { (it.value as? JsonObject)?.containsKey("const") == true }
                consts.isNotEmpty() && consts.all { (k, v) -> inst[k] == (v as JsonObject)["const"] }
            }
            val byRequired = discriminated.filter { b ->
                val req = (b["required"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
                req.all { inst.containsKey(it) }
            }
            val pick = when {
                byRequired.size == 1 -> byRequired[0]
                discriminated.size == 1 -> discriminated[0]
                else -> null
            }
            if (pick != null) {
                check(pick, inst, path, out)
                return
            }
            // Scenes without a recognised `type`.
            val typeValues = branches.mapNotNull {
                ((it["properties"] as? JsonObject)?.get("type") as? JsonObject)?.get("const")?.jsonPrimitive?.content
            }
            if (typeValues.isNotEmpty()) {
                val actual = (inst["type"] as? JsonPrimitive)?.content
                out += Issue(
                    path + "type", if (actual == null) "required" else "enum", Severity.ERROR,
                    mapOf("allowed" to typeValues.joinToString(", "), "actual" to (actual ?: ""), "name" to "type"),
                )
                return
            }
        }
        val results = branches.map { b -> ArrayList<Issue>().also { check(b, inst, path, it) } }
        val passing = results.count { r -> r.none { it.severity == Severity.ERROR } }
        if (passing == 1) {
            out += results.first { r -> r.none { it.severity == Severity.ERROR } }
        } else {
            out += results.minByOrNull { r -> r.count { it.severity == Severity.ERROR } } ?: emptyList()
            if (passing > 1) out += Issue(path, "oneOfAmbiguous", Severity.ERROR)
        }
    }

    private fun checkObject(schema: JsonObject, inst: JsonObject, path: List<Any>, out: MutableList<Issue>) {
        val props = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
        (schema["required"] as? JsonArray)?.forEach { r ->
            val name = r.jsonPrimitive.content
            if (!inst.containsKey(name)) out += Issue(path + name, "required", Severity.ERROR, mapOf("name" to name))
        }
        (schema["maxProperties"] as? JsonPrimitive)?.intOrNull?.let { max ->
            if (inst.size > max) out += Issue(path, "maxProperties", Severity.ERROR, mapOf("max" to "$max", "actual" to "${inst.size}"))
        }
        val additional = schema["additionalProperties"]
        for ((k, v) in inst) {
            val ps = props[k] as? JsonObject
            when {
                ps != null -> check(ps, v, path + k, out)
                additional is JsonObject -> check(additional, v, path + k, out)
                additional is JsonPrimitive && additional.booleanOrNull == false ->
                    out += Issue(path + k, "unknownField", Severity.WARNING, mapOf("name" to k))
            }
        }
    }

    private fun checkArray(schema: JsonObject, inst: JsonArray, path: List<Any>, out: MutableList<Issue>) {
        (schema["minItems"] as? JsonPrimitive)?.intOrNull?.let {
            if (inst.size < it) out += Issue(path, "minItems", Severity.ERROR, mapOf("min" to "$it", "actual" to "${inst.size}"))
        }
        (schema["maxItems"] as? JsonPrimitive)?.intOrNull?.let {
            if (inst.size > it) out += Issue(path, "maxItems", Severity.ERROR, mapOf("max" to "$it", "actual" to "${inst.size}"))
        }
        if ((schema["uniqueItems"] as? JsonPrimitive)?.booleanOrNull == true && inst.toSet().size != inst.size) {
            out += Issue(path, "uniqueItems", Severity.ERROR)
        }
        (schema["items"] as? JsonObject)?.let { itemSchema ->
            inst.forEachIndexed { i, e -> check(itemSchema, e, path + i, out) }
        }
    }

    private fun checkPrimitive(schema: JsonObject, inst: JsonPrimitive, path: List<Any>, out: MutableList<Issue>) {
        if (inst.isString) {
            val len = inst.content.codePointCount(0, inst.content.length)
            (schema["minLength"] as? JsonPrimitive)?.intOrNull?.let {
                if (len < it) out += Issue(path, "minLength", Severity.ERROR, mapOf("min" to "$it", "actual" to "$len"))
            }
            (schema["maxLength"] as? JsonPrimitive)?.intOrNull?.let {
                if (len > it) out += Issue(path, "maxLength", Severity.ERROR, mapOf("max" to "$it", "actual" to "$len"))
            }
            (schema["pattern"] as? JsonPrimitive)?.content?.let {
                if (!Regex(it).containsMatchIn(inst.content)) out += Issue(path, "pattern", Severity.ERROR, mapOf("pattern" to it))
            }
        } else {
            val d = inst.doubleOrNull ?: return
            (schema["minimum"] as? JsonPrimitive)?.doubleOrNull?.let {
                if (d < it) out += Issue(path, "minimum", Severity.ERROR, mapOf("min" to fmt(it), "actual" to fmt(d)))
            }
            (schema["maximum"] as? JsonPrimitive)?.doubleOrNull?.let {
                if (d > it) out += Issue(path, "maximum", Severity.ERROR, mapOf("max" to fmt(it), "actual" to fmt(d)))
            }
        }
    }

    private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    companion object {
        fun load(text: String) = MiniJsonSchema(Json.parseToJsonElement(text) as JsonObject)
    }
}
