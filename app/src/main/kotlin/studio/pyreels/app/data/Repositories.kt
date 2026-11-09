package studio.pyreels.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import studio.pyreels.core.brief.Brief
import studio.pyreels.core.brief.EpisodeRecord
import studio.pyreels.core.brief.StylePreset
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.templates.Templates
import studio.pyreels.core.validate.ScenarioValidator
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun ProjectEntity.scenario(): Scenario? = ScenarioValidator.parse(scenarioJson).scenarios.firstOrNull()
fun ProjectEntity.brief(): Brief? = briefJson?.let { runCatching { json.decodeFromString(Brief.serializer(), it) }.getOrNull() }

@Singleton
class ProjectRepository @Inject constructor(private val db: AppDatabase) {
    val projects: Flow<List<ProjectEntity>> = db.projects().observeAll()

    suspend fun get(id: Long) = db.projects().get(id)

    suspend fun create(s: Scenario, brief: Brief?): Long {
        val now = System.currentTimeMillis()
        return db.projects().insert(
            ProjectEntity(
                title = s.meta.title, lessonId = s.meta.lesson.id, episode = s.meta.series.episode,
                scenarioJson = ScenarioValidator.encode(s), briefJson = brief?.let { json.encodeToString(Brief.serializer(), it) },
                createdAt = now, updatedAt = now,
            ),
        )
    }

    suspend fun createAll(list: List<Scenario>, brief: Brief?): List<Long> = list.map { create(it, brief) }

    suspend fun save(id: Long, s: Scenario) {
        val p = db.projects().get(id) ?: return
        db.projects().update(p.copy(title = s.meta.title, lessonId = s.meta.lesson.id, episode = s.meta.series.episode, scenarioJson = ScenarioValidator.encode(s), updatedAt = System.currentTimeMillis()))
    }

    suspend fun setExport(id: Long, uri: String) {
        val p = db.projects().get(id) ?: return
        db.projects().update(p.copy(lastExportUri = uri, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = db.projects().delete(id)
}

@Singleton
class SeriesRepository @Inject constructor(private val db: AppDatabase) {
    val history: Flow<List<EpisodeRecord>> = db.episodes().observeAll().map { l -> l.map { it.toRecord() } }
    val entities: Flow<List<EpisodeEntity>> = db.episodes().observeAll()

    suspend fun all(): List<EpisodeRecord> = db.episodes().all().map { it.toRecord() }

    /** Records (or replaces) the episode of a project when it is saved/exported. */
    suspend fun record(projectId: Long?, s: Scenario) {
        projectId?.let { db.episodes().deleteForProject(it) }
        val r = Templates.record(s, System.currentTimeMillis())
        val episode = if (r.episode > 0) r.episode else (db.episodes().all().maxOfOrNull { it.episode } ?: 0) + 1
        db.episodes().insert(
            EpisodeEntity(projectId = projectId, episode = episode, lessonId = r.lessonId, concept = r.concept, title = r.title, keySentence = r.keySentence, mistake = r.mistake, challenge = r.challenge, analogy = r.analogy, savedAt = r.savedAt),
        )
    }

    suspend fun update(e: EpisodeEntity) = db.episodes().update(e)
    suspend fun delete(id: Long) = db.episodes().delete(id)
    suspend fun reset() = db.episodes().clear()

    private fun EpisodeEntity.toRecord() = EpisodeRecord(episode, lessonId, concept, title, keySentence, mistake, challenge, analogy, savedAt)
}

@Singleton
class PresetRepository @Inject constructor(private val db: AppDatabase) {
    val presets: Flow<List<StylePreset>> = db.presets().observeAll().map { l ->
        l.mapNotNull { p -> runCatching { StylePreset(p.id, p.name, json.decodeFromString(Brief.serializer(), p.briefJson), p.builtIn) }.getOrNull() }
    }

    suspend fun ensureDefaults() {
        if (db.presets().count() == 0) StylePreset.DEFAULTS.forEach { save(it) }
    }

    suspend fun save(p: StylePreset) = db.presets().upsert(PresetEntity(p.id, p.name, json.encodeToString(Brief.serializer(), p.brief), p.builtIn))
    suspend fun delete(id: String) = db.presets().delete(id)
}
