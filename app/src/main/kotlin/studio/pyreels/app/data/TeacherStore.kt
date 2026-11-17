package studio.pyreels.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import studio.pyreels.core.model.Languages
import javax.inject.Inject
import javax.inject.Singleton

/** A teacher character = one programming language the creator added (any language, any name). */
data class Teacher(val id: String, val name: String, val note: String = "")

/**
 * The creator's languages. Nothing is built in: the list starts empty, each entry is a name and
 * (optionally) a gallery picture kept by [AvatarStore] under the same id.
 */
@Singleton
class TeacherStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val sp = context.getSharedPreferences("pyreels_teachers", Context.MODE_PRIVATE)
    private val _teachers = MutableStateFlow(load())
    val teachers: StateFlow<List<Teacher>> = _teachers

    private fun load(): List<Teacher> = try {
        val arr = JSONArray(sp.getString("list", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { Teacher(it.getString("id"), it.getString("name"), it.optString("note", "")) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun persist(list: List<Teacher>) {
        _teachers.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("note", it.note)) }
        sp.edit().putString("list", arr.toString()).apply()
    }

    /**
     * Adds a teacher, or edits [existingId]. A new name whose id already exists edits that teacher
     * (one language, one character). Returns the saved teacher.
     */
    @Synchronized
    fun upsert(existingId: String?, name: String, note: String): Teacher {
        val clean = name.trim().take(20)
        val id = existingId ?: Languages.slug(clean)
        val t = Teacher(id, clean, note.trim().take(160))
        val cur = _teachers.value
        persist(if (cur.any { it.id == id }) cur.map { if (it.id == id) t else it } else cur + t)
        return t
    }

    @Synchronized
    fun remove(id: String) {
        persist(_teachers.value.filterNot { it.id == id })
        AvatarStore.remove(context, id)
    }
}
