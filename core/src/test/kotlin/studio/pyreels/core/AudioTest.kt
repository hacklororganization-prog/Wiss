package studio.pyreels.core

import studio.pyreels.core.audio.AudioMixer
import studio.pyreels.core.audio.LoudnessMeter
import studio.pyreels.core.audio.MixSettings
import studio.pyreels.core.audio.Synth
import studio.pyreels.core.audio.Wav
import studio.pyreels.core.model.SfxIds
import studio.pyreels.core.timeline.Timeline
import studio.pyreels.core.validate.ScenarioValidator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioTest {
    @Test
    fun everySfxIsSoftAndShort() {
        for (id in SfxIds.ALL.filter { it != SfxIds.NONE }) {
            val s = Synth.base(id)
            assertTrue(s.isNotEmpty(), id)
            val peak = s.maxOf { abs(it) }
            assertTrue(peak in 0.01f..0.5f, "$id peak $peak")
            assertTrue(s.size <= Synth.SAMPLE_RATE * 1.2, "$id too long")
        }
    }

    @Test
    fun variantsDiffer() {
        val a = Synth.sound(SfxIds.KEY_TAP, 1); val b = Synth.sound(SfxIds.KEY_TAP, 2)
        assertTrue(a.size != b.size || a.indices.any { a[it] != b[it] })
    }

    @Test
    fun loudnessMeterMatchesReference() {
        // 1 kHz sine at -20 dBFS (amplitude 0.1) on both channels ≈ -20 LUFS (K-weighting ≈ +0.7 dB at 1 kHz, -3 dB for RMS, +3 dB stereo)
        val sr = Synth.SAMPLE_RATE
        val buf = FloatArray(sr * 5) { (0.1 * sin(2 * PI * 1000 * it / sr)).toFloat() }
        val m = LoudnessMeter(sr); m.add(buf, buf.size)
        val l = m.integrated()
        assertTrue(abs(l - (-20.0)) < 0.5, "measured $l")
    }

    @Test
    fun mixIsNormalisedAndNeverClips() {
        val s = ScenarioValidator.parse(Resources.text("samples/02-variables.pyreels.json")).scenarios.single()
        val tl = Timeline.build(s)
        val voice = tl.voices.associate { v -> v.sceneIndex to FloatArray((v.durationMs * 48).toInt()) { (0.4 * sin(2 * PI * 220 * it / 48000)).toFloat() } }
        val mixer = AudioMixer(tl, voice, MixSettings())
        val stats = mixer.prepare()
        val buf = FloatArray(4096)
        var peak = 0f; var total = 0L
        val meter = LoudnessMeter(48000)
        while (true) {
            val n = mixer.read(buf); if (n == 0) break
            for (i in 0 until n) peak = maxOf(peak, abs(buf[i]))
            meter.add(buf, n); total += n
        }
        assertEquals(mixer.totalSamples, total)
        assertTrue(peak <= 0.9f, "peak $peak")
        val out = meter.integrated()
        println("measured=${stats.measuredLufs} gain=${stats.gainDb} out=$out")
        assertTrue(abs(out - (-16.0)) < 2.0, "output loudness $out")
    }

    @Test
    fun wavRoundTrip() {
        val s = FloatArray(4800) { (0.5 * sin(2 * PI * 440 * it / 48000)).toFloat() }
        val back = Wav.read(Wav.write(s, 48000))
        assertEquals(48000, back.sampleRate)
        assertEquals(s.size, back.samples.size)
        assertTrue(abs(back.samples[100] - s[100]) < 0.001f)
        assertEquals(2400, Wav.resample(back, 24000).size)
    }
}
