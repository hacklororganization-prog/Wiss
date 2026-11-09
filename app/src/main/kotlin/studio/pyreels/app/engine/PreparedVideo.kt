package studio.pyreels.app.engine

import android.content.Context
import studio.pyreels.app.engine.python.PythonRunner
import studio.pyreels.app.engine.tts.VoiceEngine
import studio.pyreels.core.model.Scenario
import studio.pyreels.core.timeline.Timeline
import javax.inject.Inject
import javax.inject.Singleton

/** A scenario ready to render: timeline (with real voice durations and real Python results) + voice PCM. */
class PreparedVideo(
    val scenario: Scenario,
    val timeline: Timeline,
    val voicePcm: Map<Int, FloatArray>,
    val notes: List<String>,
)

/** Builds [PreparedVideo]s. The same prepared video drives the preview and the export. */
@Singleton
class VideoEngine @Inject constructor(
    private val python: PythonRunner,
    private val voice: VoiceEngine,
) {
    /** Fast path for live editing: estimated voice durations, declared traces. */
    fun quick(scenario: Scenario): PreparedVideo = PreparedVideo(scenario, Timeline.build(scenario), emptyMap(), emptyList())

    suspend fun prepare(context: Context, scenario: Scenario, withVoice: Boolean = true, withPython: Boolean = true): PreparedVideo {
        val notes = ArrayList<String>()
        val runs = if (withPython) {
            val (r, n) = python.runAll(context, scenario); notes += n; r
        } else emptyMap()
        var timeline = Timeline.build(scenario, runResults = runs)
        var pcm: Map<Int, FloatArray> = emptyMap()
        if (withVoice && timeline.voices.isNotEmpty()) {
            val v = voice.synthesize(context, scenario, timeline)
            v.warning?.let { notes += it }
            if (v.durations.isNotEmpty()) {
                timeline = Timeline.build(scenario, voiceMs = v.durations, runResults = runs)
                pcm = v.pcm
            }
        }
        return PreparedVideo(scenario, timeline, pcm, notes)
    }
}
