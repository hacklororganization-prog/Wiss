package studio.pyreels.core.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import studio.pyreels.core.Resources

/**
 * Deterministic "speak code" layer: converts code and symbols into natural spoken Arabic before
 * the text reaches TTS, so TTS never mispronounces symbols. Rules are data (bundled speech rule files, one per language).
 */
object SpeakCode {

    @Serializable
    data class Pattern(val regex: String, val say: String)

    @Serializable
    data class Rules(
        val language: String,
        val locale: String,
        val description: String = "",
        val patterns: List<Pattern> = emptyList(),
        val operators: Map<String, String> = emptyMap(),
        val keywords: Map<String, String> = emptyMap(),
        val letters: Map<String, String> = emptyMap(),
        val indent: String = "مسافة بادئة",
        val newline: String = "، ثم ",
    )

    private val json = Json { ignoreUnknownKeys = true }

    val pythonRules: Rules by lazy { load(Resources.text("speech/python.ar.json")) }

    fun load(text: String): Rules = json.decodeFromString(Rules.serializer(), text)

    private class Compiled(val rules: Rules) {
        val patterns = rules.patterns.map { Regex(it.regex) to it.say }
        val ops = rules.operators.keys.sortedByDescending { it.length }
    }

    private val compiled = HashMap<Rules, Compiled>()
    private fun compiled(r: Rules) = compiled.getOrPut(r) { Compiled(r) }

    /** Reads a code snippet (one or many lines) aloud. */
    fun readCode(code: String, rules: Rules = pythonRules): String =
        code.lines().filter { it.isNotBlank() }.joinToString(rules.newline) { readLine(it, rules) }.trim()

    fun readLine(line: String, rules: Rules = pythonRules): String {
        val c = compiled(rules)
        val out = StringBuilder()
        var i = 0
        // Leading indentation is spoken once.
        val indentLen = line.length - line.trimStart().length
        if (indentLen > 0) { out.append(rules.indent).append(' '); i = indentLen }
        while (i < line.length) {
            val ch = line[i]
            if (ch.isWhitespace()) { i++; continue }
            // 1. patterns anchored at i
            var matched = false
            for ((re, say) in c.patterns) {
                val m = re.matchAt(line, i) ?: continue
                if (m.value.isEmpty()) continue
                out.append(fill(say, m, rules)).append(' ')
                i += m.value.length
                matched = true
                break
            }
            if (matched) continue
            // 2. comments
            if (ch == '#') { out.append(rules.operators["#"] ?: "").append(line.substring(i + 1).trim()); break }
            // 3. numbers
            if (ch.isDigit()) {
                var j = i
                while (j < line.length && (line[j].isDigit() || (line[j] == '.' && j + 1 < line.length && line[j + 1].isDigit()))) j++
                out.append(ArabicNumbers.read(line.substring(i, j))).append(' ')
                i = j; continue
            }
            // 4. identifiers / keywords
            if (ch.isLetter() || ch == '_') {
                var j = i
                while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                out.append(identifier(line.substring(i, j), rules)).append(' ')
                i = j; continue
            }
            // 5. operators (longest first)
            val op = c.ops.firstOrNull { line.startsWith(it, i) }
            if (op != null) {
                val say = rules.operators.getValue(op)
                if (say.isNotEmpty()) out.append(say).append(' ')
                i += op.length; continue
            }
            out.append(ch); i++
        }
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun fill(template: String, m: MatchResult, rules: Rules): String =
        Regex("\\{(id:|n:)?(\\d+)\\}").replace(template) { r ->
            val g = m.groupValues.getOrNull(r.groupValues[2].toInt()) ?: ""
            when (r.groupValues[1]) {
                "id:" -> identifier(g, rules)
                "n:" -> ArabicNumbers.read(g)
                else -> g
            }
        }

    fun identifier(name: String, rules: Rules = pythonRules): String {
        rules.keywords[name]?.let { return it }
        if (name.length == 1) rules.letters[name.lowercase()]?.let { return it }
        if (name.all { it.isDigit() }) return ArabicNumbers.read(name)
        // snake_case and digits: total_price2 → "total price اثنان"
        return name.split('_').filter { it.isNotEmpty() }.joinToString(" ") { part ->
            val m = Regex("^([A-Za-z]+)(\\d+)$").find(part)
            when {
                m != null -> (if (m.groupValues[1].length == 1) rules.letters[m.groupValues[1].lowercase()] ?: m.groupValues[1] else m.groupValues[1]) +
                    " " + ArabicNumbers.read(m.groupValues[2])
                part.length == 1 -> rules.letters[part.lowercase()] ?: part
                else -> part
            }
        }
    }

    private val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")

    /** Removes what TTS should not read (emoji, markdown backticks) without converting code. */
    fun cleanForSpeech(text: String): String = text.replace(emoji, "").replace("`", "").replace(Regex("\\s+"), " ").trim()

    /**
     * Converts a mixed Arabic/code sentence: segments in backticks are read as code; if the whole
     * text looks like code it is read entirely as code.
     */
    fun toSpeech(text: String, rules: Rules = pythonRules): String {
        val clean = text.replace(emoji, "")
        if (clean.contains('`')) {
            return Regex("`([^`]+)`").replace(clean) { readCode(it.groupValues[1], rules) }.replace(Regex("\\s+"), " ").trim()
        }
        return if (looksLikeCode(clean)) readCode(clean, rules) else cleanForSpeech(clean)
    }

    fun looksLikeCode(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val arabic = t.count { it in '؀'..'ۿ' }
        if (arabic > 0) return false
        return t.any { it in "=()[]:+-*/<>{}" } || Regex("^(print|def|for|while|if|import|return)\\b").containsMatchIn(t)
    }
}

/** Arabic number words (masculine counting form), enough for code reading. */
object ArabicNumbers {
    private val ones = listOf("صفر", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة")
    private val teens = listOf("", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
    private val tens = listOf("", "عشرة", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hundreds = listOf("", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة")

    fun read(s: String): String {
        if (s.contains('.')) {
            val (a, b) = s.split('.', limit = 2)
            return "${read(a.ifEmpty { "0" })} فاصلة ${b.map { ones[it - '0'] }.joinToString(" ")}"
        }
        val n = s.toLongOrNull() ?: return s
        return words(n)
    }

    fun words(n: Long): String {
        if (n < 0) return "سالب ${words(-n)}"
        if (n <= 10) return ones[n.toInt()]
        if (n < 20) return teens[(n - 10).toInt()]
        if (n < 100) {
            val o = (n % 10).toInt(); val t = (n / 10).toInt()
            return if (o == 0) tens[t] else "${ones[o]} و${tens[t]}"
        }
        if (n < 1000) {
            val h = (n / 100).toInt(); val r = n % 100
            return if (r == 0L) hundreds[h] else "${hundreds[h]} و${words(r)}"
        }
        if (n < 1_000_000) {
            val th = n / 1000; val r = n % 1000
            val head = when {
                th == 1L -> "ألف"
                th == 2L -> "ألفان"
                th in 3..10 -> "${words(th)} آلاف"
                else -> "${words(th)} ألفًا"
            }
            return if (r == 0L) head else "$head و${words(r)}"
        }
        if (n < 1_000_000_000) {
            val m = n / 1_000_000; val r = n % 1_000_000
            val head = when (m) { 1L -> "مليون"; 2L -> "مليونان"; in 3..10 -> "${words(m)} ملايين"; else -> "${words(m)} مليونًا" }
            return if (r == 0L) head else "$head و${words(r)}"
        }
        return n.toString()
    }
}
