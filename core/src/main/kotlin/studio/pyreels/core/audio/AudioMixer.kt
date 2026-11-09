package studio.pyreels.core.audio

import studio.pyreels.core.timeline.SfxCue
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.timeline.VoiceCue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

data class MixSettings(
    val voiceGain: Float = 1.0f,
    val sfxGain: Float = 0.8f,
    val ambientGain: Float = 0.55f,
    /** No continuous background bed: only context sound effects and the voice. */
    val ambientMood: String = "none",
    /** Ambient level reduction while a voice is speaking (0..1). */
    val duckDepth: Float = 0.65f,
    val targetLufs: Double = -16.0,
    val ceiling: Float = 0.89f, // ≈ −1 dBFS
    val normalize: Boolean = true,
)

/**
 * Streaming mixer: renders the whole soundtrack (ambient + SFX + voice) sample-accurately from the
 * timeline, with ducking, fades, loudness normalisation and a soft peak limiter. It never holds the
 * full mix in memory, so 180 s exports stay memory-safe.
 *
 * Output is mono float PCM at [Synth.SAMPLE_RATE]; the encoder duplicates it to stereo.
 */
class AudioMixer(
    private val timeline: Timeline,
    private val voicePcm: Map<Int, FloatArray>,
    private val settings: MixSettings = MixSettings(),
) {
    val sampleRate = Synth.SAMPLE_RATE
    val totalSamples: Long = timeline.totalMs * sampleRate / 1000

    private class Placed(val start: Long, val data: FloatArray, val gain: Float)

    private val sfx: List<Placed> = timeline.sfx.filter { it.id != "none" }.map { c: SfxCue ->
        val jitter = ((Synth.variant(c.seed).pitch - 1f) * 400).toLong() // ±12 ms timing humanisation
        Placed(max(0, (c.timeMs + jitter) * sampleRate / 1000), Synth.sound(c.id, c.seed), c.gain * settings.sfxGain)
    }.sortedBy { it.start }

    private val voices: List<Placed> = timeline.voices.mapNotNull { v: VoiceCue ->
        voicePcm[v.sceneIndex]?.let { Placed(v.startMs * sampleRate / 1000, it, settings.voiceGain) }
    }.sortedBy { it.start }

    private val voiceRanges: List<LongRange> = voices.map { it.start until it.start + it.data.size }

    private val ambient: FloatArray = if (settings.ambientMood == "none") FloatArray(0) else Synth.ambient(settings.ambientMood)

    private var normGain = 1f
    private var position = 0L
    private var limiterEnv = 1f
    private var duckEnv = 0f

    /** Loudness (LUFS, stereo-equivalent) of the un-normalised mix. */
    fun measureLoudness(): Double {
        val meter = LoudnessMeter(sampleRate)
        val buf = FloatArray(4096)
        var pos = 0L
        resetState()
        while (pos < totalSamples) {
            val n = min(buf.size.toLong(), totalSamples - pos).toInt()
            mixRaw(pos, buf, n)
            meter.add(buf, n)
            pos += n
        }
        resetState()
        return meter.integrated()
    }

    /** Computes the normalisation gain. Call once before [read]. */
    fun prepare(): MixStats {
        val lufs = measureLoudness()
        normGain = if (settings.normalize && lufs > -70) {
            10.0.pow((settings.targetLufs - lufs) / 20).toFloat().coerceIn(0.25f, 4f)
        } else 1f
        seek(0)
        return MixStats(lufs, settings.targetLufs, normGain)
    }

    fun seek(sample: Long) { position = sample.coerceIn(0, totalSamples); resetState() }

    private fun resetState() { limiterEnv = 1f; duckEnv = 0f }

    /** Reads the next [n] samples into [out]. Returns the number of samples written (0 at the end). */
    fun read(out: FloatArray, n: Int = out.size): Int {
        val count = min(n.toLong(), totalSamples - position).toInt()
        if (count <= 0) return 0
        mixRaw(position, out, count)
        val ceil = settings.ceiling
        for (i in 0 until count) {
            var s = out[i] * normGain
            // envelope limiter: instant attack, ~80 ms release
            val a = abs(s)
            val needed = if (a > ceil) ceil / a else 1f
            limiterEnv = if (needed < limiterEnv) needed else limiterEnv + (1f - limiterEnv) * 0.0003f
            s *= limiterEnv
            // safety soft clip (never exceeds the ceiling)
            if (abs(s) > ceil * 0.95f) s = (ceil * tanh((s / ceil).toDouble())).toFloat()
            out[i] = s
        }
        position += count
        return count
    }

    /** Mixes sources (no normalisation) for [start, start+n). */
    private fun mixRaw(start: Long, out: FloatArray, n: Int) {
        java.util.Arrays.fill(out, 0, n, 0f)
        val end = start + n
        // Voice + duck envelope
        for (v in voices) {
            if (v.start >= end) break
            val vEnd = v.start + v.data.size
            if (vEnd <= start) continue
            val from = max(start, v.start); val to = min(end, vEnd)
            for (p in from until to) out[(p - start).toInt()] += v.data[(p - v.start).toInt()] * v.gain
        }
        // SFX (ducked slightly under voice)
        for (c in sfx) {
            if (c.start >= end) break
            val cEnd = c.start + c.data.size
            if (cEnd <= start) continue
            val from = max(start, c.start); val to = min(end, cEnd)
            for (p in from until to) {
                val ducked = if (inVoice(p)) 0.75f else 1f
                out[(p - start).toInt()] += c.data[(p - c.start).toInt()] * c.gain * ducked
            }
        }
        // Ambient: tiled loop, 1.5 s fade in/out, ducked under voice with smooth envelope
        if (ambient.isNotEmpty()) {
            val fade = 1.5 * sampleRate
            val attack = 1f - exp(-1.0 / (0.15 * sampleRate)).toFloat()
            val release = 1f - exp(-1.0 / (0.45 * sampleRate)).toFloat()
            for (i in 0 until n) {
                val p = start + i
                val target = if (inVoice(p)) 1f else 0f
                duckEnv += (target - duckEnv) * (if (target > duckEnv) attack else release)
                val edge = min(1.0, min(p / fade, (totalSamples - p) / fade)).toFloat().coerceAtLeast(0f)
                val g = settings.ambientGain * edge * (1f - settings.duckDepth * duckEnv)
                out[i] += ambient[(p % ambient.size).toInt()] * g
            }
        }
    }

    private var lastVoiceIdx = 0
    private fun inVoice(p: Long): Boolean {
        if (voiceRanges.isEmpty()) return false
        if (lastVoiceIdx >= voiceRanges.size || voiceRanges[lastVoiceIdx].first > p) lastVoiceIdx = 0
        while (lastVoiceIdx < voiceRanges.size && voiceRanges[lastVoiceIdx].last < p) lastVoiceIdx++
        return lastVoiceIdx < voiceRanges.size && p in voiceRanges[lastVoiceIdx]
    }
}

data class MixStats(val measuredLufs: Double, val targetLufs: Double, val gain: Float) {
    val gainDb get() = 20 * log10(gain.toDouble())
}

/** ITU-R BS.1770 integrated loudness (K-weighting, 400 ms blocks, absolute and relative gates). */
class LoudnessMeter(sampleRate: Int) {
    private val shelf = Biquad.highShelf(1681.974, 3.99984, 0.7071752, sampleRate.toDouble())
    private val hp = Biquad.highPass(38.13547, 0.5003270, sampleRate.toDouble())
    private val step = sampleRate / 10 // 100 ms hops, 400 ms blocks (75 % overlap)
    private val hops = ArrayList<Double>()
    private var acc = 0.0
    private var count = 0

    fun add(buf: FloatArray, n: Int) {
        for (i in 0 until n) {
            val y = hp.process(shelf.process(buf[i].toDouble()))
            acc += y * y
            if (++count == step) { hops += acc / step; acc = 0.0; count = 0 }
        }
    }

    /** Short-term block loudness values (400 ms) for jump analysis. */
    fun blocks(): List<Double> = (3 until hops.size).map { i -> toLufs((hops[i] + hops[i - 1] + hops[i - 2] + hops[i - 3]) / 4) }

    fun integrated(): Double {
        val blocks = (3 until hops.size).map { i -> (hops[i] + hops[i - 1] + hops[i - 2] + hops[i - 3]) / 4 }
        val abs = blocks.filter { toLufs(it) > -70 }
        if (abs.isEmpty()) return -99.0
        val rel = toLufs(abs.average()) - 10
        val gated = abs.filter { toLufs(it) > rel }
        return toLufs((if (gated.isEmpty()) abs else gated).average())
    }

    // Mono mix is played on both stereo channels: sum of channel powers = 2 × mono power.
    private fun toLufs(ms: Double) = if (ms <= 0) -99.0 else -0.691 + 10 * log10(2 * ms)
}
