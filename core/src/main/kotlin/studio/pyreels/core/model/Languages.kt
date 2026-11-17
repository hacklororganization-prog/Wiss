package studio.pyreels.core.model

/**
 * Programming languages are open-ended: the user names any language (and gives it a picture), and
 * the app never needs a built-in list. This object only normalises names to a stable id and knows
 * the file name / run command of the languages it happens to recognise (cosmetic: the editor's
 * file tab and terminal prompt). Unknown languages get sensible generic values.
 */
object Languages {
    private val aliases = mapOf(
        "c++" to "cpp", "cplusplus" to "cpp", "cxx" to "cpp", "c#" to "csharp", "cs" to "csharp", "f#" to "fsharp",
        "js" to "javascript", "node" to "javascript", "nodejs" to "javascript", "ts" to "typescript", "py" to "python",
        "golang" to "go", "rs" to "rust", "rb" to "ruby", "kt" to "kotlin", "objective-c" to "objc", "objectivec" to "objc",
        "shell" to "bash", "sh" to "bash", "vb.net" to "vb", "visualbasic" to "vb",
    )

    private val files = mapOf(
        "python" to "main.py", "java" to "Main.java", "cpp" to "main.cpp", "c" to "main.c", "javascript" to "main.js",
        "typescript" to "main.ts", "kotlin" to "Main.kt", "csharp" to "Program.cs", "go" to "main.go", "rust" to "main.rs",
        "php" to "index.php", "swift" to "main.swift", "ruby" to "main.rb", "dart" to "main.dart", "lua" to "main.lua",
        "r" to "main.R", "perl" to "main.pl", "scala" to "Main.scala", "haskell" to "main.hs", "bash" to "main.sh",
        "sql" to "query.sql", "html" to "index.html", "css" to "style.css", "julia" to "main.jl", "elixir" to "main.exs",
        "fsharp" to "Program.fs", "vb" to "Program.vb", "objc" to "main.m", "matlab" to "main.m", "zig" to "main.zig",
        "groovy" to "Main.groovy", "powershell" to "main.ps1", "fortran" to "main.f90", "pascal" to "main.pas",
        "ocaml" to "main.ml", "clojure" to "main.clj", "nim" to "main.nim", "assembly" to "main.asm", "cobol" to "main.cob",
    )

    private val commands = mapOf(
        "python" to "python main.py", "java" to "java Main.java", "cpp" to "g++ main.cpp && ./a.out", "c" to "gcc main.c && ./a.out",
        "javascript" to "node main.js", "typescript" to "ts-node main.ts", "kotlin" to "kotlin Main.kt", "csharp" to "dotnet run",
        "go" to "go run main.go", "rust" to "cargo run", "php" to "php index.php", "swift" to "swift main.swift",
        "ruby" to "ruby main.rb", "dart" to "dart main.dart", "lua" to "lua main.lua", "r" to "Rscript main.R", "perl" to "perl main.pl",
        "scala" to "scala Main.scala", "haskell" to "runghc main.hs", "bash" to "bash main.sh", "julia" to "julia main.jl",
        "elixir" to "elixir main.exs", "zig" to "zig run main.zig", "groovy" to "groovy Main.groovy", "fsharp" to "dotnet fsi Program.fs",
    )

    /**
     * Stable id (a-z, 0-9, _) for any language name. Names that contain no Latin letters (for
     * example Arabic) get a short id derived from the name, so they still work everywhere.
     */
    fun slug(name: String?): String {
        val raw = name?.trim()?.lowercase().orEmpty()
        if (raw.isEmpty()) return "text"
        aliases[raw]?.let { return it }
        val s = raw.replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val id = if (s.isNotEmpty()) s else "l" + Integer.toHexString(raw.hashCode()).takeLast(7)
        val safe = if (id[0].isLetter()) id else "l$id"
        return aliases[safe] ?: safe.take(24)
    }

    fun isPython(language: String?) = slug(language) == "python"

    fun fileName(language: String?): String {
        val id = slug(language)
        return files[id] ?: "main.${id.take(5)}"
    }

    fun runCommand(language: String?): String {
        val id = slug(language)
        return commands[id] ?: "run ${fileName(language)}"
    }
}
