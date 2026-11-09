package studio.pyreels.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val lessonId: String,
    val episode: Int?,
    val scenarioJson: String,
    val briefJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastExportUri: String? = null,
)

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long?,
    val episode: Int,
    val lessonId: String,
    val concept: String,
    val title: String,
    val keySentence: String,
    val mistake: String?,
    val challenge: String?,
    val analogy: String?,
    val savedAt: Long,
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val briefJson: String,
    val builtIn: Boolean,
)

@Entity(tableName = "export_jobs")
data class ExportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val platform: String,
    val quality: String,
    val publishPack: Boolean,
    val packTreeUri: String?,
    val coverTimeMs: Long?,
    val state: String,
    val progress: Float = 0f,
    val outputUri: String? = null,
    val message: String? = null,
    val createdAt: Long,
) {
    companion object {
        const val QUEUED = "queued"; const val RUNNING = "running"; const val DONE = "done"; const val FAILED = "failed"; const val CANCELLED = "cancelled"
    }
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun get(id: Long): ProjectEntity?
    @Insert suspend fun insert(p: ProjectEntity): Long
    @Insert suspend fun insertAll(p: List<ProjectEntity>): List<Long>
    @Update suspend fun update(p: ProjectEntity)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes ORDER BY episode ASC") fun observeAll(): Flow<List<EpisodeEntity>>
    @Query("SELECT * FROM episodes ORDER BY episode ASC") suspend fun all(): List<EpisodeEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(e: EpisodeEntity): Long
    @Update suspend fun update(e: EpisodeEntity)
    @Query("DELETE FROM episodes WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM episodes WHERE projectId = :projectId") suspend fun deleteForProject(projectId: Long)
    @Query("DELETE FROM episodes") suspend fun clear()
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY builtIn DESC, name ASC") fun observeAll(): Flow<List<PresetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: PresetEntity)
    @Query("DELETE FROM presets WHERE id = :id AND builtIn = 0") suspend fun delete(id: String)
    @Query("SELECT COUNT(*) FROM presets") suspend fun count(): Int
}

@Dao
interface ExportJobDao {
    @Query("SELECT * FROM export_jobs WHERE id = :id") suspend fun get(id: Long): ExportJobEntity?
    @Query("SELECT * FROM export_jobs WHERE id = :id") fun observe(id: Long): Flow<ExportJobEntity?>
    @Query("SELECT * FROM export_jobs WHERE state IN ('queued','running') ORDER BY createdAt ASC") suspend fun unfinished(): List<ExportJobEntity>
    @Insert suspend fun insert(j: ExportJobEntity): Long
    @Update suspend fun update(j: ExportJobEntity)
}

@Database(entities = [ProjectEntity::class, EpisodeEntity::class, PresetEntity::class, ExportJobEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun episodes(): EpisodeDao
    abstract fun presets(): PresetDao
    abstract fun jobs(): ExportJobDao
}
