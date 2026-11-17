package studio.pyreels.core

import studio.pyreels.core.validate.ScenarioValidator

/** Test-only scenarios (NOT shipped with the app). */
object Fixtures {
    fun text(name: String): String =
        (Fixtures::class.java.getResourceAsStream("/fixtures/$name.json") ?: error("missing fixture $name")).use { it.readBytes().toString(Charsets.UTF_8) }

    fun scenario(name: String) = ScenarioValidator.parse(text(name)).scenarios.single()

    val ALL = listOf("print", "variables", "if", "for-loop", "functions", "zig-variables", "arabic-lang")
}
