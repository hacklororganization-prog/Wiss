package studio.pyreels.core

/** Access to the app's bundled data files (the JSON schema and the speech rules). */
object Resources {
    fun text(name: String): String {
        val stream = Resources::class.java.getResourceAsStream("/pyreels/$name")
            ?: error("Missing bundled resource: pyreels/$name")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun exists(name: String): Boolean = Resources::class.java.getResource("/pyreels/$name") != null

    val schemaText: String by lazy { text("scenario.schema.json") }
}
