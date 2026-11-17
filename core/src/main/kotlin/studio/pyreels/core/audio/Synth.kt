package studio.pyreels.core.audio

import studio.pyreels.core.model.SfxIds
import studio.pyreels.core.timeline.Rng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Procedural sound design. Every sound is generated from oscillators, envelopes and filtered
 * noise — no audio files. All sounds are soft (peak ≈ −12 dBFS before mixing) and short.
 */
object Synth {
    const val SAMPLE_RATE = 48_000
    private const val TWO_PI = 2.0 * PI

    /** Humanisation applied per cue: slight pitch and gain variation. */
    data class Variant(val pitch: Float = 1f, val gain: Float = 1f)

    fun variant(seed: Int): Variant {
        val r = Rng(seed.toLong() * 2654435761L + 17)
        return Variant(pitch = 1f + r.signed() * 0.03f, gain = 1f + r.signed() * 0.12f)
    }

    private val cache = HashMap<String, FloatArray>()

    /** Number of pre-rendered humanised variants per sound (cheap, and no two taps sound identical). */
    private const val VARIANTS = 5

    /** Base (un-humanised) sound, cached. */
    fun base(id: String): FloatArray = synchronized(cache) { cache.getOrPut(id) { soften(generate(id, Variant())) } }

    /** Humanised sound: one of [VARIANTS] cached variants with slightly different pitch and level. */
    fun sound(id: String, seed: Int): FloatArray {
        val idx = Math.floorMod(seed, VARIANTS)
        return synchronized(cache) {
            cache.getOrPut("$id#$idx") {
                val k = (idx - VARIANTS / 2).toFloat()
                soften(generate(id, Variant(pitch = 1f + k * 0.018f, gain = 1f + k * 0.06f)))
            }
        }
    }

    /**
     * Rounds every sound off: a gentle low-pass (no harsh highs) and short fades, so nothing clicks
     * or sounds sharp even when many cues play in a row.
     */
    private fun soften(x: FloatArray, cutoffHz: Double = 4800.0): FloatArray {
        if (x.isEmpty()) return x
        val a = (1.0 - exp(-TWO_PI * cutoffHz / SAMPLE_RATE)).toFloat()
        var y = 0f
        for (i in x.indices) { y += a * (x[i] - y); x[i] = y }
        val fadeIn = min(x.size / 2, n(0.003))
        val fadeOut = min(x.size / 2, n(0.012))
        for (i in 0 until fadeIn) x[i] *= i.toFloat() / fadeIn
        for (i in 0 until fadeOut) x[x.size - 1 - i] *= i.toFloat() / fadeOut
        return x
    }

    fun generate(id: String, v: Variant): FloatArray = when (id) {
        SfxIds.KEY_TAP -> keyTap(v)
        SfxIds.MSG_SEND -> blip(listOf(523.25, 659.25), 0.09, 0.12, v)
        SfxIds.MSG_RECV -> blip(listOf(659.25, 587.33), 0.10, 0.11, v)
        SfxIds.POP -> pop(v)
        SfxIds.SOFT_WHOOSH -> whoosh(0.5, v)
        SfxIds.SUCCESS_CHIME -> chime(listOf(523.25, 659.25, 783.99), 0.11, 0.75, v)
        SfxIds.GENTLE_ERROR -> chime(listOf(392.0, 329.63), 0.14, 0.6, v, soft = true)
        SfxIds.HEART_PULSE -> heart(v)
        SfxIds.TICK -> tick(v)
        SfxIds.HOOK_ENTER -> hookEnter(v)
        SfxIds.RUN_START -> sweep(260.0, 520.0, 0.34, v)
        SfxIds.TYPE_LOOP -> typeLoop(v)
        else -> FloatArray(0)
    }

    private fun n(sec: Double) = (sec * SAMPLE_RATE).toInt()

    private fun env(i: Int, len: Int, attack: Int, curve: Double = 5.0): Double {
        if (i < attack) return i.toDouble() / max(1, attack)
        val x = (i - attack).toDouble() / max(1, len - attack)
        return exp(-curve * x) * (1 - x)
    }

    /** A soft, low "thock" — round and quiet, so a long typing run stays comfortable. */
    private fun keyTap(v: Variant): FloatArray {
        val len = n(0.05)
        val out = FloatArray(len)
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val e = exp(-t * 85.0) * min(1.0, i / (SAMPLE_RATE * 0.002))
            val s = sin(TWO_PI * 380.0 * v.pitch * t) * 0.8 + sin(TWO_PI * 190.0 * v.pitch * t) * 0.5
            out[i] = (s * e * 0.16 * v.gain).toFloat()
        }
        return out
    }

    private fun blip(freqs: List<Double>, noteSec: Double, gain: Double, v: Variant): FloatArray {
        val note = n(noteSec)
        val len = note * freqs.size + n(0.12)
        val out = FloatArray(len)
        freqs.forEachIndexed { k, f ->
            val start = k * note
            val nl = len - start
            var ph = 0.0
            for (i in 0 until nl) {
                ph += TWO_PI * f * v.pitch / SAMPLE_RATE
                val s = sin(ph) + 0.06 * sin(2 * ph)
                out[start + i] += (s * env(i, nl, n(0.012), 5.0) * gain * v.gain).toFloat()
            }
        }
        return out
    }

    private fun pop(v: Variant): FloatArray {
        val len = n(0.09)
        val out = FloatArray(len)
        var ph = 0.0
        for (i in 0 until len) {
            val x = i.toDouble() / len
            val f = (420 - 200 * x) * v.pitch
            ph += TWO_PI * f / SAMPLE_RATE
            out[i] = (sin(ph) * env(i, len, n(0.006), 4.0) * 0.16 * v.gain).toFloat()
        }
        return out
    }

    private fun whoosh(sec: Double, v: Variant): FloatArray {
        val len = n(sec)
        val out = FloatArray(len)
        val r = Rng(7)
        for (i in 0 until len) {
            val x = i.toDouble() / len
            // update the filter coefficients every 64 samples for a smooth sweep
            if (i % 64 == 0) whooshFilter.set(Biquad.bandPass((240 + 700 * sin(PI * x)) * v.pitch, 0.6))
            val s = whooshFilter.process(r.signed().toDouble())
            out[i] = (s * sin(PI * x).pow(2) * 0.10 * v.gain).toFloat()
        }
        whooshFilter.reset()
        return out
    }

    private val whooshFilter = Biquad.bandPass(800.0, 0.8)

    private fun chime(freqs: List<Double>, stepSec: Double, tailSec: Double, v: Variant, soft: Boolean = false): FloatArray {
        val step = n(stepSec)
        val len = step * freqs.size + n(tailSec)
        val out = FloatArray(len)
        freqs.forEachIndexed { k, f ->
            val start = k * step
            val nl = len - start
            for (i in 0 until nl) {
                val t = i.toDouble() / SAMPLE_RATE
                val ph = TWO_PI * f * v.pitch * t
                val s = sin(ph) + (if (soft) 0.04 else 0.12) * sin(2 * ph) + 0.03 * sin(3 * ph)
                out[start + i] += (s * env(i, nl, n(0.014), if (soft) 3.5 else 4.2) * (if (soft) 0.11 else 0.09) * v.gain).toFloat()
            }
        }
        return out
    }

    private fun heart(v: Variant): FloatArray {
        val beat = n(0.16)
        val gap = n(0.13)
        val len = beat * 2 + gap
        val out = FloatArray(len)
        for (b in 0..1) {
            val start = b * (beat + gap)
            for (i in 0 until beat) {
                val t = i.toDouble() / SAMPLE_RATE
                val f = (85 - 30 * i.toDouble() / beat) * v.pitch
                out[start + i] += (sin(TWO_PI * f * t) * env(i, beat, n(0.008), 5.0) * (if (b == 0) 0.3 else 0.22) * v.gain).toFloat()
            }
        }
        return out
    }

    private fun tick(v: Variant): FloatArray {
        val len = n(0.03)
        val out = FloatArray(len)
        for (i in 0 until len) out[i] = (sin(TWO_PI * 1500 * v.pitch * i / SAMPLE_RATE) * env(i, len, 24, 7.0) * 0.06 * v.gain).toFloat()
        return out
    }

    private fun sweep(f0: Double, f1: Double, sec: Double, v: Variant): FloatArray {
        val len = n(sec)
        val out = FloatArray(len)
        var ph = 0.0
        for (i in 0 until len) {
            val x = i.toDouble() / len
            ph += TWO_PI * (f0 + (f1 - f0) * x) * v.pitch / SAMPLE_RATE
            out[i] = ((sin(ph) + 0.1 * sin(2 * ph)) * sin(PI * x) * 0.11 * v.gain).toFloat()
        }
        return out
    }

    /** The signature soft entrance of the hook: a warm swell chord plus a gentle air sweep. */
    private fun hookEnter(v: Variant): FloatArray {
        val len = n(1.1)
        val out = FloatArray(len)
        val chord = listOf(261.63, 329.63, 392.0, 523.25)
        val air = whoosh(1.1, v)
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val x = i.toDouble() / len
            val e = sin(PI * x.pow(0.6)).pow(2)
            var s = 0.0
            chord.forEachIndexed { k, f -> s += sin(TWO_PI * f * v.pitch * t + k) * (0.6 - k * 0.1) }
            out[i] = (s * e * 0.05 * v.gain + air[i] * 0.5).toFloat()
        }
        return out
    }

    private fun typeLoop(v: Variant): FloatArray {
        val len = n(1.0)
        val out = FloatArray(len)
        val r = Rng(99)
        var pos = 0
        while (pos < len) {
            val tap = keyTap(Variant(1f + r.signed() * 0.05f, 0.8f + r.nextFloat() * 0.3f))
            for (i in tap.indices) if (pos + i < len) out[pos + i] += tap[i]
            pos += n(0.07 + r.nextFloat() * 0.06)
        }
        return out
    }

    // ------------------------------------------------------------ ambient

    /**
     * Very quiet ambient loop. Generated as a seamless loop (the loop length is a multiple of every
     * LFO period) so it can be tiled for any duration.
     */
    fun ambient(mood: String, seconds: Int = 16): FloatArray {
        val len = n(seconds.toDouble())
        val out = FloatArray(len)
        when (mood) {
            "calm_pad" -> {
                val chords = listOf(listOf(220.0, 277.18, 329.63), listOf(196.0, 246.94, 293.66), listOf(174.61, 220.0, 261.63), listOf(196.0, 246.94, 329.63))
                val seg = len / chords.size
                for (i in 0 until len) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val c = (i / seg).coerceAtMost(chords.size - 1)
                    val local = (i % seg).toDouble() / seg
                    val fade = sin(PI * local).pow(0.5)
                    var s = 0.0
                    for (f in chords[c]) s += sin(TWO_PI * f * t) + 0.5 * sin(TWO_PI * f * 1.003 * t)
                    val lfo = 0.75 + 0.25 * sin(TWO_PI * t / seconds * 2)
                    out[i] = (s * fade * lfo * 0.05).toFloat()
                }
                lowPass(out, 1400.0)
            }
            "soft_rain" -> {
                val r = Rng(5)
                var pink = 0.0
                for (i in 0 until len) {
                    pink = 0.97 * pink + 0.03 * r.signed()
                    out[i] = (pink * 1.2 + r.signed() * 0.02).toFloat()
                }
                lowPass(out, 2500.0)
                // seamless loop: crossfade the head with the tail
                val xf = n(1.0)
                for (i in 0 until xf) { val a = i.toFloat() / xf; out[i] = out[i] * a + out[len - xf + i] * (1 - a) }
            }
            "warm_keys" -> {
                val notes = listOf(523.25, 659.25, 587.33, 783.99, 659.25, 523.25, 440.0, 587.33)
                val step = len / notes.size
                notes.forEachIndexed { k, f ->
                    val start = k * step
                    val nl = min(n(2.5), len - start)
                    for (i in 0 until nl) {
                        val t = i.toDouble() / SAMPLE_RATE
                        out[start + i] += (sin(TWO_PI * f * t) * exp(-2.2 * t) * 0.06).toFloat()
                    }
                }
            }
            else -> Unit
        }
        return out
    }

    private fun lowPass(buf: FloatArray, cutoff: Double) {
        val f = Biquad.lowPass(cutoff, 0.707)
        for (i in buf.indices) buf[i] = f.process(buf[i].toDouble()).toFloat()
    }
}

/** RBJ biquad filter. */
class Biquad(private var b0: Double, private var b1: Double, private var b2: Double, private var a1: Double, private var a2: Double) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }

    fun set(o: Biquad) { b0 = o.b0; b1 = o.b1; b2 = o.b2; a1 = o.a1; a2 = o.a2 }
    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

    companion object {
        private const val SR = Synth.SAMPLE_RATE.toDouble()

        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        fun lowPass(f: Double, q: Double, sr: Double = SR): Biquad {
            val w = 2 * PI * f / sr; val a = sin(w) / (2 * q); val c = kotlin.math.cos(w)
            return make((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a)
        }

        fun highPass(f: Double, q: Double, sr: Double = SR): Biquad {
            val w = 2 * PI * f / sr; val a = sin(w) / (2 * q); val c = kotlin.math.cos(w)
            return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a)
        }

        fun bandPass(f: Double, q: Double, sr: Double = SR): Biquad {
            val w = 2 * PI * f / sr; val a = sin(w) / (2 * q); val c = kotlin.math.cos(w)
            return make(a, 0.0, -a, 1 + a, -2 * c, 1 - a)
        }

        /** High shelf used by the K-weighting pre-filter. */
        fun highShelf(f: Double, gainDb: Double, q: Double, sr: Double = SR): Biquad {
            val A = 10.0.pow(gainDb / 40); val w = 2 * PI * f / sr; val c = kotlin.math.cos(w); val s = sin(w)
            val alpha = s / (2 * q); val sq = 2 * kotlin.math.sqrt(A) * alpha
            return make(
                A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq),
                (A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq,
            )
        }

        @Suppress("unused")
        private fun db(x: Double) = 20 * kotlin.math.log10(x)
        @Suppress("unused")
        private fun t(x: Double) = tan(x)
    }
}
