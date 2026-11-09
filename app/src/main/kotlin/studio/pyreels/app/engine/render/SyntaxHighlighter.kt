package studio.pyreels.app.engine.render

import studio.pyreels.core.model.Palette

/** Unified, tiny syntax highlighter (same look for every language). Results are cached per line. */
object SyntaxHighlighter {
    enum class Kind { TEXT, KEYWORD, STRING, NUMBER, COMMENT, FUNCTION }

    class Token(val text: String, val kind: Kind)

    private val keywords = mapOf(
        "python" to setOf("def", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "import", "from", "as", "class", "try", "except", "finally", "with", "pass", "break", "continue", "lambda", "True", "False", "None", "is", "global", "yield", "raise", "print", "range", "len", "input", "int", "str", "float", "list", "dict", "set", "type", "self"),
        "java" to setOf("public", "private", "protected", "class", "static", "void", "int", "double", "float", "boolean", "char", "String", "new", "return", "if", "else", "for", "while", "true", "false", "null", "import", "package", "final", "this", "System"),
        "cpp" to setOf("int", "double", "float", "char", "bool", "void", "return", "if", "else", "for", "while", "true", "false", "nullptr", "include", "using", "namespace", "std", "cout", "cin", "endl", "auto", "const", "class", "struct", "new", "delete"),
        "javascript" to setOf("let", "const", "var", "function", "return", "if", "else", "for", "while", "true", "false", "null", "undefined", "new", "class", "this", "console", "of", "in", "=>", "async", "await"),
    )
    private val generic = setOf("fun", "val", "var", "let", "const", "func", "fn", "return", "if", "else", "for", "while", "true", "false", "null", "nil", "class", "struct", "package", "import", "public", "static", "void", "int", "mut", "use", "echo")

    private val cache = object : LinkedHashMap<Pair<String, String>, List<Token>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, List<Token>>?) = size > 512
    }

    fun tokens(line: String, language: String): List<Token> = synchronized(cache) {
        cache.getOrPut(line to language) { tokenize(line, language) }
    }

    private fun tokenize(line: String, language: String): List<Token> {
        val kw = keywords[language] ?: generic
        val out = ArrayList<Token>()
        val commentStart = if (language == "python") "#" else "//"
        var i = 0
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { out += Token(buf.toString(), Kind.TEXT); buf.setLength(0) } }
        while (i < line.length) {
            val c = line[i]
            when {
                line.startsWith(commentStart, i) || (language == "cpp" && c == '#' && line.trimStart().startsWith("#")) -> {
                    flush(); out += Token(line.substring(i), if (c == '#' && language == "cpp") Kind.KEYWORD else Kind.COMMENT); i = line.length
                }
                c == '"' || c == '\'' || c == '`' -> {
                    flush()
                    var j = i + 1
                    while (j < line.length && line[j] != c) { if (line[j] == '\\') j++; j++ }
                    j = minOf(j + 1, line.length)
                    out += Token(line.substring(i, j), Kind.STRING); i = j
                }
                c.isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit()) -> {
                    flush()
                    var j = i
                    while (j < line.length && (line[j].isDigit() || line[j] == '.')) j++
                    out += Token(line.substring(i, j), Kind.NUMBER); i = j
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    flush()
                    var j = i
                    while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_' || line[j] == '$')) j++
                    val w = line.substring(i, j)
                    var k = j
                    while (k < line.length && line[k] == ' ') k++
                    val kind = when {
                        w in kw -> Kind.KEYWORD
                        k < line.length && line[k] == '(' -> Kind.FUNCTION
                        else -> Kind.TEXT
                    }
                    out += Token(w, kind); i = j
                }
                else -> { buf.append(c); i++ }
            }
        }
        flush()
        return out
    }

    fun color(kind: Kind, p: Palette): Int = when (kind) {
        Kind.TEXT -> p.codeText; Kind.KEYWORD -> p.codeKeyword; Kind.STRING -> p.codeString
        Kind.NUMBER -> p.codeNumber; Kind.COMMENT -> p.codeComment; Kind.FUNCTION -> p.codeFunction
    }.toInt()
}
