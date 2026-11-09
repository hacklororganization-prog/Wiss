package studio.pyreels.core

/** Access to the app's bundled data files (curriculum, errors library, schema, samples, speech rules). */
object Resources {
    fun text(name: String): String {
        val stream = Resources::class.java.getResourceAsStream("/pyreels/$name")
            ?: error("Missing bundled resource: pyreels/$name")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun exists(name: String): Boolean = Resources::class.java.getResource("/pyreels/$name") != null

    val schemaText: String by lazy { text("scenario.schema.json") }

    val SAMPLE_FILES = listOf(
        "samples/01-print.pyreels.json",
        "samples/02-variables.pyreels.json",
        "samples/03-if.pyreels.json",
        "samples/04-for-loop.pyreels.json",
        "samples/05-functions.pyreels.json",
    )
    const val WEEKLY_SAMPLE = "samples/weekly-batch.pyreels.json"
}
