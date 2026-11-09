package studio.pyreels.app.engine.python

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.Scene
import studio.pyreels.core.model.TraceStep
import studio.pyreels.core.timeline.RunResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes `run` scenes with `"exec": "real"` in an embedded CPython (Chaquopy), sandboxed by
 * pyreels_runner.py (restricted builtins and imports, no file/network access, step + time limits).
 */
@Singleton
class PythonRunner @Inject constructor() {

    data class Outcome(val result: RunResult?, val ok: Boolean, val message: String?)

    private val cache = HashMap<String, Outcome>()

    private fun ensureStarted(context: Context) {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
    }

    suspend fun run(context: Context, code: String, timeoutMs: Long = 2_500): Outcome = withContext(Dispatchers.Default) {
        synchronized(cache) { cache[code] }?.let { return@withContext it }
        val outcome = withTimeoutOrNull(timeoutMs + 1_500) {
            try {
                ensureStarted(context)
                val json = Python.getInstance().getModule("pyreels_runner").callAttr("run", code, timeoutMs / 1000.0).toString()
                parse(json)
            } catch (t: Throwable) {
                Outcome(null, false, "تعذّر تشغيل بايثون: ${t.message?.take(120)}")
            }
        } ?: Outcome(null, false, "انتهت مهلة تشغيل الكود.")
        synchronized(cache) { cache[code] = outcome }
        outcome
    }

    /** Runs every real Python run scene of a scenario; returns results by scene index. */
    suspend fun runAll(context: Context, scenario: Scenario): Pair<Map<Int, RunResult>, List<String>> {
        val results = HashMap<Int, RunResult>()
        val notes = ArrayList<String>()
        var lastCode: String? = null
        scenario.scenes.forEachIndexed { i, s ->
            if (s is Scene.CodeType) lastCode = s.code
            if (s is Scene.CommonMistake) lastCode = s.fix
            if (s is Scene.Run && s.exec == "real" && s.language == "python") {
                val code = s.code ?: lastCode ?: return@forEachIndexed
                val o = run(context, code)
                if (o.result != null) {
                    results[i] = o.result
                    if (s.output.isNotBlank() && o.result.output.trim() != s.output.trim()) {
                        notes += "المشهد ${i + 1}: المخرجات الحقيقية («${o.result.output.take(40)}») تختلف عن المكتوبة في السيناريو؛ تُعرض الحقيقية."
                    }
                } else o.message?.let { notes += "المشهد ${i + 1}: $it — سيُستخدم التتبّع المكتوب." }
            }
        }
        return results to notes
    }

    private fun parse(json: String): Outcome {
        val o = Json.parseToJsonElement(json).jsonObject
        val output = o["output"]?.jsonPrimitive?.contentOrNull ?: ""
        val error = o["error"]?.jsonPrimitive?.contentOrNull
        val trace = (o["trace"] as? JsonArray)?.map { st ->
            val so = st.jsonObject
            TraceStep(
                line = so["line"]?.jsonPrimitive?.intOrNull ?: 1,
                vars = (so["vars"] as? JsonObject)?.toMap() ?: emptyMap(),
                out = (so["out"] as? JsonPrimitive)?.contentOrNull,
                iteration = (so["iteration"] as? JsonPrimitive)?.intOrNull,
                stack = (so["stack"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
            )
        }.orEmpty()
        // Drop the trailing loop-exit check of a for/while header (it repeats the last state).
        val cleaned = if (trace.size >= 2 && trace.last().line == trace.dropLast(1).lastOrNull { it.line == trace.last().line }?.line && trace.last().out == null && trace.last().iteration != null) trace.dropLast(1) else trace
        return Outcome(RunResult(cleaned.take(16), output, error), error == null, error)
    }
}
