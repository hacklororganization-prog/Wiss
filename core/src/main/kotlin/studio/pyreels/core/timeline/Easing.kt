package studio.pyreels.core.timeline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/** Calm easing curves only (no bounce, no elastic). */
object Easing {
    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

    /** Smooth ease-in-out (cubic). */
    fun inOut(x: Float): Float {
        val t = clamp01(x)
        return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    }

    fun out(x: Float): Float {
        val t = clamp01(x)
        return 1f - (1f - t).pow(3)
    }

    fun sine(x: Float): Float = (-(cos(PI * clamp01(x)) - 1) / 2).toFloat()

    /** Progress of a window [start, start+dur) at time t, eased. */
    fun window(t: Long, start: Long, dur: Long, curve: (Float) -> Float = ::inOut): Float {
        if (dur <= 0) return if (t >= start) 1f else 0f
        return curve((t - start).toFloat() / dur)
    }

    /** Linear progress (no easing). */
    fun linear(t: Long, start: Long, dur: Long): Float =
        if (dur <= 0) (if (t >= start) 1f else 0f) else clamp01((t - start).toFloat() / dur)

    /** A soft pulse 0→1→0 over [start, start+dur): never faster than 1 Hz when used for repetition. */
    fun pulse(t: Long, start: Long, dur: Long): Float {
        if (t < start || t >= start + dur) return 0f
        val x = (t - start).toFloat() / dur
        return sine(if (x < 0.5f) x * 2f else (1f - x) * 2f)
    }

    fun lerp(a: Float, b: Float, x: Float) = a + (b - a) * x

    /** Standard motion durations (ms), all within the calm 280–450 ms range. */
    const val ENTER_MS = 480L
    const val EXIT_MS = 400L
    const val STAGE_MS = 640L
    const val RISE_PX = 8f
}
