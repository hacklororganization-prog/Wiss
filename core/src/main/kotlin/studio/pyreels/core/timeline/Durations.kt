package studio.pyreels.core.timeline

import studio.pyreels.core.model.Limits
import studio.pyreels.core.model.Scene
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Allowed duration window of one scene. */
data class SceneBounds(val minMs: Long, val idealMs: Long, val maxMs: Long) {
    val fixed get() = minMs == maxMs
}

object DurationEstimator {

    fun words(text: String) = text.split(Regex("\\s+")).count { it.isNotBlank() }

    /** Comfortable reading time for a beginner. */
    fun readMs(text: String): Long {
        val w = words(text)
        if (w == 0) return 0
        return ((w / Limits.WORDS_PER_SECOND) * 1000).roundToLong() + 800
    }

    fun typingIndicatorMs(text: String): Long = (800 + text.length * 14L).coerceIn(1000, 1900)

    /** Minimum on-screen time so the text is read at most at [Limits.WORDS_PER_SECOND]. */
    fun minReadMs(text: String): Long = ((words(text) / Limits.WORDS_PER_SECOND) * 1000).roundToLong() + 250

    /** Typing-indicator length actually used for a chat scene of duration [d]: never steals reading time. */
    fun chatTypingMs(text: String, d: Long, outgoing: Boolean = false): Long =
        min(if (outgoing) composeMs(text) else typingIndicatorMs(text), max(350L, d - minReadMs(text)))

    /** Ahmed types his own messages letter by letter in the input bar. */
    fun composeMs(text: String): Long = (600 + text.length * 55L).coerceIn(1300, 3600)

    /** Average typing time of code (natural typing ≈ 55 ms per char plus line pauses). */
    fun codeTypingMs(code: String): Long = code.length * 72L + code.count { it == '\n' } * 240L + 600

    fun bounds(scene: Scene, voiceMs: Long? = null): SceneBounds {
        scene.durationMs?.let { val d = it.coerceIn(500, 20_000); return SceneBounds(d, d, d) }
        val voiceMin = voiceMs?.let { it + 600 } ?: 0L
        val b = when (scene) {
            is Scene.HookPredict -> return SceneBounds(Limits.HOOK_MS, Limits.HOOK_MS, Limits.HOOK_MS)
            is Scene.Recap -> SceneBounds(4_500, 5_800, 8_000)
            is Scene.Chat -> {
                val ideal = (if (scene.from == "ahmed") composeMs(scene.text) else typingIndicatorMs(scene.text)) + Easing.ENTER_MS + max(readMs(scene.text), 2_000) +
                    (if (scene.code != null) 1_500 else 0) + (if (scene.reactions.isNotEmpty()) 600 else 0)
                val typingMin = if (scene.from == "ahmed") composeMs(scene.text) * 3 / 4 else 800L
                val minMs = max(2_400L, typingMin + max(minReadMs(scene.text), 1_400)) + (if (scene.code != null) 1_000 else 0)
                SceneBounds(minMs, max(ideal, minMs), (ideal * 1.4).roundToLong())
            }
            is Scene.Analogy -> {
                val ideal = 1_200 + readMs(scene.title) + scene.items.size * 1_300L + readMs(scene.text ?: "")
                SceneBounds((ideal * 0.8).roundToLong(), ideal, (ideal * 1.4).roundToLong())
            }
            is Scene.CodeType -> {
                val typing = if (scene.typing == "instant") 400 else codeTypingMs(scene.code) + (if (scene.typo) 900 else 0)
                val ideal = typing + 1_800 + readMs(scene.caption ?: "")
                SceneBounds(max(2_500, (typing * 0.55).roundToLong() + 1_000), ideal, min(Limits.MAX_SCENE_MS, ideal * 3 / 2))
            }
            is Scene.Run -> {
                val steps = scene.trace.size
                val ideal = 1_000 + steps * 1_700L + 700 + scene.output.length * 30L + 1_600
                SceneBounds(max(3_000, 1_000 + steps * 1_100L + 1_400), ideal, min(Limits.MAX_SCENE_MS, ideal * 3 / 2))
            }
            is Scene.EngageMoment -> SceneBounds(6_000, 7_000, 8_500)
            is Scene.Reveal -> {
                val ideal = 1_200 + max(readMs(scene.text), 2_400) + 1_600
                SceneBounds(4_200, ideal, ideal + 2_500)
            }
            is Scene.CommonMistake -> {
                val ideal = 1_200 + 1_500 + readMs(scene.explain) + 600 + codeTypingMs(scene.fix) / 2 + 1_600
                SceneBounds(max(7_000, ideal * 3 / 4), ideal, min(Limits.MAX_SCENE_MS, ideal * 3 / 2))
            }
            is Scene.KeySentence -> SceneBounds(4_200, max(5_000, readMs(scene.text) + 2_000), 7_500)
            is Scene.Teaser -> SceneBounds(4_500, 6_000L + (if (scene.challenge != null) 1_800 else 0), 10_000)
        }
        if (voiceMin <= 0) return b
        val minMs = max(b.minMs, voiceMin)
        return SceneBounds(minMs, max(b.idealMs, voiceMin + 300), max(b.maxMs, voiceMin + 1_500))
    }
}

/**
 * Fits scene durations to a target total. Fixed scenes keep their duration; flexible scenes are
 * scaled proportionally within their bounds (iteratively, so clamped scenes hand their share to others).
 */
object DurationFitter {

    data class Fit(val durations: LongArray, val totalMs: Long, val targetMs: Long, val reachable: Boolean)

    /**
     * Calm pacing first: when the comfortable (ideal) durations run longer than the target, the
     * video is allowed to run up to [Limits.CALM_STRETCH] × target before any scene is compressed.
     */
    fun calmTarget(bounds: List<SceneBounds>, targetMs: Long): Long {
        val ideal = bounds.sumOf { it.idealMs }
        val cap = minOf((targetMs * Limits.CALM_STRETCH).roundToLong(), Limits.HARD_CAP_SECONDS * 1000L - 2_000)
        return ideal.coerceIn(minOf(targetMs, cap), maxOf(targetMs, cap))
    }

    fun fit(bounds: List<SceneBounds>, targetMs: Long): Fit {
        val d = LongArray(bounds.size) { bounds[it].idealMs }
        var total = d.sum()
        val locked = BooleanArray(bounds.size) { bounds[it].fixed }
        repeat(8) {
            val diff = targetMs - total
            if (kotlin.math.abs(diff) < 50) return@repeat
            val flexible = d.indices.filter { !locked[it] }
            val flexSum = flexible.sumOf { d[it] }.toDouble()
            if (flexible.isEmpty() || flexSum <= 0) return@repeat
            for (i in flexible) {
                val wanted = d[i] + (diff * (d[i] / flexSum)).roundToLong()
                val c = wanted.coerceIn(bounds[i].minMs, bounds[i].maxMs)
                if (c != wanted) locked[i] = true
                d[i] = c
            }
            total = d.sum()
        }
        val tol = (targetMs * Limits.TARGET_TOLERANCE).roundToLong()
        return Fit(d, total, targetMs, kotlin.math.abs(total - targetMs) <= tol)
    }
}
