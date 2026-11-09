package studio.pyreels.core.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal WAV (RIFF PCM) reader/writer. Used for TTS output (synthesizeToFile) and tests. */
object Wav {
    data class Pcm(val sampleRate: Int, val samples: FloatArray) {
        val durationMs get() = samples.size * 1000L / sampleRate
    }

    fun read(bytes: ByteArray): Pcm {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") { "Not a WAV file" }
        var pos = 12
        var channels = 1; var rate = 16000; var bits = 16; var format = 1
        var data: FloatArray? = null
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            var size = bb.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > bytes.size) size = bytes.size - body // streamed WAVs may carry a bogus size
            when (id) {
                "fmt " -> {
                    format = bb.getShort(body).toInt()
                    channels = bb.getShort(body + 2).toInt().coerceAtLeast(1)
                    rate = bb.getInt(body + 4)
                    bits = bb.getShort(body + 14).toInt()
                }
                "data" -> {
                    val bytesPer = bits / 8
                    val frames = size / (bytesPer * channels)
                    val out = FloatArray(frames)
                    for (f in 0 until frames) {
                        var acc = 0f
                        for (c in 0 until channels) {
                            val o = body + (f * channels + c) * bytesPer
                            acc += when {
                                format == 3 && bits == 32 -> bb.getFloat(o)
                                bits == 16 -> bb.getShort(o) / 32768f
                                bits == 8 -> ((bytes[o].toInt() and 0xFF) - 128) / 128f
                                bits == 32 -> bb.getInt(o) / 2147483648f
                                else -> 0f
                            }
                        }
                        out[f] = acc / channels
                    }
                    data = out
                }
            }
            pos = body + size + (size and 1)
        }
        return Pcm(rate, data ?: FloatArray(0))
    }

    /** Linear-interpolation resampler (adequate for speech). */
    fun resample(pcm: Pcm, target: Int = Synth.SAMPLE_RATE): FloatArray {
        if (pcm.sampleRate == target) return pcm.samples
        val ratio = pcm.sampleRate.toDouble() / target
        val n = (pcm.samples.size / ratio).toInt()
        val s = pcm.samples
        return FloatArray(n) { i ->
            val x = i * ratio; val i0 = x.toInt(); val f = (x - i0).toFloat()
            val a = s.getOrElse(i0) { 0f }; val b = s.getOrElse(i0 + 1) { a }
            a + (b - a) * f
        }
    }

    /** Trims leading/trailing near-silence (TTS engines pad generously). */
    fun trimSilence(s: FloatArray, threshold: Float = 0.01f, keepMs: Int = 40, rate: Int = Synth.SAMPLE_RATE): FloatArray {
        var a = 0; var b = s.size - 1
        while (a < s.size && kotlin.math.abs(s[a]) < threshold) a++
        while (b > a && kotlin.math.abs(s[b]) < threshold) b--
        if (a >= b) return FloatArray(0)
        val keep = keepMs * rate / 1000
        return s.copyOfRange((a - keep).coerceAtLeast(0), (b + keep).coerceAtMost(s.size - 1) + 1)
    }

    fun write(samples: FloatArray, sampleRate: Int = Synth.SAMPLE_RATE): ByteArray {
        val out = ByteArrayOutputStream()
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val dataLen = samples.size * 2
        bb.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(dataLen)
        out.write(bb.array())
        val pcm = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) pcm.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        out.write(pcm.array())
        return out.toByteArray()
    }
}
