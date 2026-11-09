package studio.pyreels.app.engine.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import studio.pyreels.core.audio.Synth
import studio.pyreels.core.audio.Wav
import studio.pyreels.core.model.Personas
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.model.castById
import studio.pyreels.core.timeline.Timeline
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice-over through Android TextToSpeech.synthesizeToFile. Each cast member gets its own
 * pitch/rate. Real durations are measured from the synthesised WAV and fed back into the timeline,
 * so captions and scene lengths are in perfect sync. Missing voices degrade to silent captions.
 */
@Singleton
class VoiceEngine @Inject constructor() {

    data class Voices(val pcm: Map<Int, FloatArray>, val durations: Map<Int, Long>, val warning: String?)

    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()

    private suspend fun engine(context: Context): TextToSpeech? {
        tts?.let { if (ready?.isCompleted == true && ready?.getCompleted() == true) return it }
        val d = CompletableDeferred<Boolean>()
        ready = d
        withContext(Dispatchers.Main) {
            tts = TextToSpeech(context.applicationContext) { status -> d.complete(status == TextToSpeech.SUCCESS) }
        }
        val ok = withTimeoutOrNull(6_000) { d.await() } ?: false
        return if (ok) tts else null
    }

    /** Synthesises every voice cue of the timeline (cached on disk by text + voice hash). */
    suspend fun synthesize(context: Context, scenario: Scenario, timeline: Timeline): Voices = mutex.withLock {
        if (timeline.voices.isEmpty()) return Voices(emptyMap(), emptyMap(), null)
        val e = engine(context) ?: return Voices(emptyMap(), emptyMap(), "محرّك تحويل النص إلى كلام غير متاح؛ سيُعرض الفيديو بترجمة صامتة.")
        val locale = if (scenario.meta.language == "en") Locale.US else Locale("ar")
        val avail = e.isLanguageAvailable(locale)
        if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
            return Voices(emptyMap(), emptyMap(), "صوت ${if (locale.language == "ar") "العربية" else "English"} غير مثبّت في الجهاز. ثبّته من إعدادات «تحويل النص إلى كلام»؛ حتى ذلك الحين تُستخدم ترجمة صامتة.")
        }
        e.language = locale
        val dir = File(context.cacheDir, "tts").apply { mkdirs() }
        val pcm = HashMap<Int, FloatArray>()
        val durations = HashMap<Int, Long>()
        var failures = 0
        for (cue in timeline.voices) {
            val member = scenario.castById(cue.castId)
            val persona = member?.let { Personas.forCast(it) } ?: Personas.forLanguage(cue.castId)
            val pitch = member?.voice?.pitch ?: persona.pitch
            val rate = member?.voice?.rate ?: persona.rate
            val key = hash("${locale}|$pitch|$rate|${cue.speech}")
            val file = File(dir, "$key.wav")
            if (!file.exists() || file.length() < 100) {
                val ok = synthesizeOne(e, cue.speech, pitch, rate, file)
                if (!ok) { failures++; continue }
            }
            try {
                val wav = Wav.read(file.readBytes())
                val samples = Wav.trimSilence(Wav.resample(wav, Synth.SAMPLE_RATE))
                if (samples.isNotEmpty()) {
                    pcm[cue.sceneIndex] = samples
                    durations[cue.sceneIndex] = samples.size * 1000L / Synth.SAMPLE_RATE
                }
            } catch (_: Exception) { failures++ }
        }
        Voices(pcm, durations, if (failures > 0) "تعذّر توليد $failures مقطع صوتي؛ ستظهر كترجمة صامتة." else null)
    }

    private suspend fun synthesizeOne(e: TextToSpeech, text: String, pitch: Float, rate: Float, file: File): Boolean {
        val id = file.name
        val done = CompletableDeferred<Boolean>()
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete(true) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id) done.complete(false) }
            override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id) done.complete(false) }
        })
        e.setPitch(pitch)
        e.setSpeechRate(rate)
        val r = e.synthesizeToFile(text, Bundle(), file, id)
        if (r != TextToSpeech.SUCCESS) return false
        return withTimeoutOrNull(20_000) { done.await() } ?: false
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    fun shutdown() { tts?.shutdown(); tts = null }
}
